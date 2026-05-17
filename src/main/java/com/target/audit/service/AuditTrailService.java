package com.target.audit.service;

import com.fuseim.common.util.DateUtil;
import com.target.audit.enhancer.AuditEnhancer;
import com.target.audittrail.model.AuditData;
import com.target.audittrail.model.AuditDataHit;
import com.target.audittrail.model.AuditDataItem;
import com.target.audittrail.model.AuditResponse;
import com.target.audittrail.util.AuditTrailConstants;
import com.target.search.model.CatalogQuery;
import com.target.search.model.CatalogSearchResponse;
import com.target.search.model.Conjunction;
import com.target.search.model.Constraint;
import com.target.search.model.Operator;
import com.target.search.model.OrderBy;
import com.target.site.model.FieldType;
import com.target.work.data.model.Catalog;
import com.target.work.data.model.CatalogField;
import com.target.work.data.model.CatalogItem;
import com.target.work.data.model.ProcessField;
import com.target.work.data.service.BatchSearchResultListener;
import com.target.work.data.service.DataStoreDriver;
import com.target.work.data.service.NoSqlDataStoreDriver;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuditTrailService {

  @Autowired private AuditTrailDataSource auditTrailDataSource;

  @Autowired private List<AuditEnhancer> auditEnhancers;

  @Autowired private List<com.target.audit.enhancer.AuditUpdateEnhancer> auditUpdateEnhancers;

  @Value("#{'${audit.date.fields:}'.split(',')}")
  private List<String> auditDateFields;

  @Value("${audit.date.format.target:yyyy-MM-dd}")
  private String auditDateTargetFormat;

  private final int ES_DEFAULT_DATA_SET_SIZE = 10000;

  private final List<String> esKeyFields = Arrays.asList("itemKey", "_id");
  private final List<String> metaFields =
      Arrays.asList(
          "audit_username",
          "audit_usersubject",
          "audit_source",
          "audit_entity",
          "audit_dataevent",
          "audit_datakey",
          "audit_success",
          "audit_changed");

  private static final Logger logger = LoggerFactory.getLogger(AuditTrailService.class);

  /**
   * Handle audit data without client IP (for non-HTTP sources like RabbitMQ, gRPC).
   * Delegates to the main method with null clientIp.
   *
   * @param auditDataRequest The audit data request
   * @return The ID of the saved audit record
   */
  public String handleAuditData(AuditData auditDataRequest) {
    return handleAuditData(auditDataRequest, null);
  }

  /**
   * Handle audit data with client IP (for HTTP REST API calls).
   * Applies all registered enhancers to add additional fields based on audit type.
   *
   * @param auditDataRequest The audit data request
   * @param clientIp The client IP address (can be null for non-HTTP sources)
   * @return The ID of the saved audit record
   */
  public String handleAuditData(AuditData auditDataRequest, String clientIp) {

    DataStoreDriver driver = auditTrailDataSource.getDataStoreDriver();
    Catalog catalog = createTransientDataCatalog(auditDataRequest);
    if (catalog != null) {
      CatalogItem item = constructCatalogItemFromRequest(auditDataRequest);

      // Auto-set audit_changed to current timestamp if not provided
      Object auditChanged = item.getValues().get("audit_changed");
      if (auditChanged == null || auditChanged.toString().trim().isEmpty()) {
        item.getValues().put("audit_changed", Instant.now().toString());
        logger.debug("Auto-set audit_changed to current timestamp for save operation");
      }

      // Apply all registered audit enhancers
      applyEnhancers(item, clientIp);

      return driver.persistItem(auditTrailDataSource.getDataStoreConnection(), catalog, item);
    }
    return null;
  }

  /**
   * Applies all registered audit enhancers to the catalog item.
   * Each enhancer checks if it can handle the item and enhances it accordingly.
   *
   * @param item The catalog item to enhance
   * @param clientIp The client IP address (may be null)
   */
  private void applyEnhancers(CatalogItem item, String clientIp) {
    for (AuditEnhancer enhancer : auditEnhancers) {
      try {
        if (enhancer.canHandle(item)) {
          logger.debug("Applying enhancer: {}", enhancer.getName());
          enhancer.enhance(item, clientIp);
        }
      } catch (Exception e) {
        logger.error("Error in enhancer {}: {}", enhancer.getName(), e.getMessage(), e);
        // Continue with other enhancers even if one fails
      }
    }
  }

  public String updateAuditData(AuditData auditDataRequest) {

    DataStoreDriver driver = auditTrailDataSource.getDataStoreDriver();
    Catalog catalog = createTransientDataCatalog(auditDataRequest);
    if (catalog != null) {
      CatalogItem item = constructCatalogItemFromRequest(auditDataRequest);

      // Extract the document key/ID from the item
      String keyValue = null;
      if (item.getValues().containsKey("_id")) {
        keyValue = (String) item.getValues().get("_id");
        // Remove _id from values as it's passed separately as keyValue
        item.getValues().remove("_id");
      } else if (item.getValues().containsKey("itemKey")) {
        keyValue = (String) item.getValues().get("itemKey");
        item.getValues().remove("itemKey");
      } else if (item.getValues().containsKey("doc_id")) {
        keyValue = (String) item.getValues().get("doc_id");
        item.getValues().remove("doc_id");
      }

      if (keyValue == null || keyValue.isEmpty()) {
        throw new IllegalArgumentException(
            "Document ID (_id, itemKey, or doc_id) is required for update operation");
      }

      // Auto-set audit_changed to current timestamp if not provided
      Object auditChanged = item.getValues().get("audit_changed");
      if (auditChanged == null || auditChanged.toString().trim().isEmpty()) {
        item.getValues().put("audit_changed", Instant.now().toString());
        logger.debug("Auto-set audit_changed to current timestamp for update operation");
      }

      // Handle -NULL- sentinel: fields explicitly set to "-NULL-" should be stored as null.
      // Step 1: collect keys marked for explicit null
      Set<String> explicitNullKeys = new HashSet<>();
      for (Map.Entry<String, Object> entry : item.getValues().entrySet()) {
        if ("-NULL-".equals(entry.getValue())) {
          explicitNullKeys.add(entry.getKey());
        }
      }
      // Step 2: remove all unset nulls and empty strings to avoid overwriting existing values
      item.getValues().entrySet().removeIf(e ->
          e.getValue() == null || "".equals(e.getValue()));
      // Step 3: add back explicit nulls so the driver sets them to null in ES
      for (String key : explicitNullKeys) {
        item.getValues().put(key, null);
      }

      // Apply update enhancers (similar to insert enhancers pattern)
      // Enhancers only modify values in the item, service always performs the update
      for (com.target.audit.enhancer.AuditUpdateEnhancer enhancer : auditUpdateEnhancers) {
        try {
          if (enhancer.canHandle(catalog, item)) {
            logger.debug("Applying update enhancer: {}", enhancer.getName());
            enhancer.performUpdate(catalog, keyValue, item, auditTrailDataSource.getDataStoreConnection());
            // Note: Only one enhancer is applied (first match wins)
            break;
          }
        } catch (Exception e) {
          logger.error("Error in update enhancer {}: {}", enhancer.getName(), e.getMessage(), e);
          throw new RuntimeException("Failed to enhance update: " + e.getMessage(), e);
        }
      }

      // Perform standard update (with enhanced values if enhancer was applied)
      logger.debug("Performing standard update");
      // updateNulls = true only when explicit -NULL- fields are present
      boolean updateNulls = !explicitNullKeys.isEmpty();
      driver.updateItem(
          auditTrailDataSource.getDataStoreConnection(), catalog, keyValue, item, updateNulls);

      // Return the document ID (driver.updateItem always returns 0, so we return the keyValue
      // directly)
      return keyValue;
    }
    return null;
  }

  private Catalog createTransientDataCatalog(AuditData auditDataRequest) {

    Catalog catalog = new Catalog();

    String auditSource = auditDataRequest.getAudit_source();

    if (auditSource.equalsIgnoreCase("PPDM") || auditSource.equalsIgnoreCase("EDGE")) {
      catalog.setEntityName("audit_" + auditDataRequest.getAudit_entity().toLowerCase());
    } else {
      catalog.setEntityName("audit_" + auditSource.trim().toLowerCase());
    }
    catalog.getCatalogFields().addAll(createFieldsFromProperties(auditDataRequest));
    return catalog;
  }

  private CatalogItem constructCatalogItemFromRequest(AuditData auditDataRequest) {
    CatalogItem item = new CatalogItem();
    if (auditDataRequest != null) {
      Field[] fields = auditDataRequest.getClass().getDeclaredFields();
      int publicStaticFinalModifier = Modifier.STATIC + Modifier.FINAL + Modifier.PUBLIC;
      int privateStaticFinalModifier = Modifier.STATIC + Modifier.FINAL + Modifier.PRIVATE;
      // declared fields
      for (Field field : fields) {
        Class<?> fieldClazz = field.getType();
        if (field.getModifiers() != publicStaticFinalModifier
            && field.getModifiers() != privateStaticFinalModifier
            && !(fieldClazz.equals(List.class) || fieldClazz.equals(HashMap.class))) {

          try {
            PropertyDescriptor pd = new PropertyDescriptor(field.getName(), AuditData.class);
            Method getter = pd.getReadMethod();
            Object value = getter.invoke(auditDataRequest);
            item.getValues().put(field.getName(), value);
          } catch (Exception ex) {
            logger.error("Failed to get value of " + field.getName() + " from AuditDataRequest");
          }
        }
      }
      if (auditDataRequest.getDataItemList() != null
          && auditDataRequest.getDataItemList().size() > 0) {
        for (AuditDataItem auditDataItem : auditDataRequest.getDataItemList()) {
          if (auditDataItem.getFieldType().equals(FieldType.DATE)) {
            try {
              // Save date in target format
              item.getValues()
                  .put(
                      auditDataItem.getFieldName(),
                      DateUtil.getInstance()
                          .convertToDate((String) auditDataItem.getValue(), auditDateTargetFormat));
            } catch (Exception e) {
              logger.error("Unable to parse Date for " + auditDataItem.getFieldName());
              e.printStackTrace();
            }
          } else if (auditDataItem.getFieldType().equals(FieldType.STRING)
              || auditDataItem.getFieldType().equals(FieldType.TEXT))
            item.getValues().put(auditDataItem.getFieldName(), auditDataItem.getValue().toString());
          else item.getValues().put(auditDataItem.getFieldName(), auditDataItem.getValue());
        }
      }
      // Trim all string values; convert blank strings to null, then remove nulls
      item.getValues().replaceAll((k, v) -> {
        if (!(v instanceof String)) return v;
        String trimmed = ((String) v).trim();
        return trimmed.isEmpty() ? null : trimmed;
      });
      item.getValues().values().removeIf(Objects::isNull);
    }
    return item;
  }

  /**
   * Sets the attempt count for FILE_MANAGER download records. Counts previous download attempts for
   * the same user and file.
   *
   * @param item The catalog item being saved
   */
  private List<CatalogField> createFieldsFromProperties(AuditData auditDataRequest) {
    List<CatalogField> fieldList = new ArrayList<>();
    if (auditDataRequest != null) {
      Field[] fields = auditDataRequest.getClass().getDeclaredFields();
      int publicStaticFinalModifier = Modifier.STATIC + Modifier.FINAL + Modifier.PUBLIC;
      for (Field field : fields) {
        Class<?> fieldClazz = field.getType();
        if (field.getModifiers() != publicStaticFinalModifier
            && !(fieldClazz.equals(List.class) || fieldClazz.equals(HashMap.class))) {
          CatalogField catalogField =
              createCatalogField(field.getName(), findFieldTypeFromClass(fieldClazz));
          fieldList.add(catalogField);
        }
      }
      if (auditDataRequest.getDataItemList() != null
          && auditDataRequest.getDataItemList().size() > 0) {
        for (AuditDataItem item : auditDataRequest.getDataItemList()) {
          FieldType fieldType = null;
          if (item.getFieldType() != null) {
            fieldType = item.getFieldType();
          } else {
            fieldType = FieldType.STRING;
            logger.error(
                "Field type not specified in data item for key "
                    + item.getFieldName()
                    + ". Using String field type");
          }
          if (item.getFieldName().equalsIgnoreCase("id")
              || item.getFieldName().equalsIgnoreCase("_id")) {
            item.setFieldName("doc_id");
          }
          CatalogField catalogField = createCatalogField(item.getFieldName(), fieldType);
          fieldList.add(catalogField);
        }
      }
    }
    return fieldList;
  }

  private FieldType findFieldTypeFromClass(Class<?> fieldClazz) {
    FieldType fieldType = FieldType.STRING;
    if (fieldClazz.equals(String.class)) {
      return FieldType.STRING;
    } else if (fieldClazz.equals(Double.class) || fieldClazz.equals(double.class)) {
      return FieldType.DOUBLE;
    } else if (fieldClazz.equals(Float.class) || fieldClazz.equals(float.class)) {
      return FieldType.FLOAT;
    } else if (fieldClazz.equals(Integer.class) || fieldClazz.equals(int.class)) {
      return FieldType.INTEGER;
    } else if (fieldClazz.equals(Long.class) || fieldClazz.equals(long.class)) {
      return FieldType.LONG;
    } else if (fieldClazz.equals(Boolean.class) || fieldClazz.equals(boolean.class)) {
      return FieldType.BOOLEAN;
    } else if (fieldClazz.equals(Date.class)) {
      return FieldType.DATETIME;
    }
    return fieldType;
  }

  private CatalogField createCatalogField(String name, FieldType fieldType) {
    CatalogField field = new CatalogField();
    ProcessField processField = new ProcessField();
    processField.setName(name);
    processField.setType(fieldType);
    field.setAttribute(processField);
    field.setSourceFieldName(name);
    return field;
  }

  public AuditResponse getAuditData(Map<String, Object> dataRequest) throws Exception {

    DataStoreDriver driver = auditTrailDataSource.getDataStoreDriver();
    AuditResponse response = new AuditResponse();

    if (driver instanceof NoSqlDataStoreDriver) {

      int offset = 0;
      int limit = 10;

      int totalRecords;

      CatalogSearchResponse catalogSearchResponse = new CatalogSearchResponse();
      CatalogSearchResponse batchSearchResponse = new CatalogSearchResponse();

      CatalogQuery query = new CatalogQuery();

      if (dataRequest.containsKey("userSubjects") && dataRequest.get("userSubjects") != null) {
        List<String> userSubjects = (List<String>) dataRequest.get("userSubjects");
        if (!userSubjects.isEmpty())
          query.addConstraint("audit_usersubject", Operator.IN, userSubjects);
      }

      String source = "";
      String indexName = "";

      if (dataRequest.containsKey("sources") && dataRequest.get("sources") != null) {
        List<String> sources = (List<String>) dataRequest.get("sources");
        if (!sources.isEmpty()) {
          source = sources.stream().findFirst().get();
          query.addConstraint("audit_source", Operator.IN, sources);
        } else {
          throw new Exception("sources must not be empty.");
        }
      }

      if (dataRequest.containsKey("dataEvents") && dataRequest.get("dataEvents") != null) {
        List<String> dataEvents = (List<String>) dataRequest.get("dataEvents");
        if (!dataEvents.isEmpty()) query.addConstraint("audit_dataevent", Operator.IN, dataEvents);
      }

      if (dataRequest.containsKey("entityNames") && dataRequest.get("entityNames") != null) {
        List<String> entityNames = (List<String>) dataRequest.get("entityNames");
        if (source.equalsIgnoreCase("PPDM") || source.equalsIgnoreCase("EDGE"))
          indexName = "audit_" + entityNames.stream().findFirst().get();
        if (!entityNames.isEmpty() && !source.equalsIgnoreCase("EDGE"))
          query.addConstraint("audit_entity", Operator.IN, entityNames);
      }

      if (dataRequest.containsKey("datakeys") && dataRequest.get("datakeys") != null) {
        Map<String, Object> datakeys = (Map<String, Object>) dataRequest.get("datakeys");
        datakeys.forEach((key, value) -> query.addConstraint(key, Operator.EQUAL, value));
      }

      if (dataRequest.containsKey("success") && dataRequest.get("success") != null) {
        boolean success = (boolean) dataRequest.get("success");
        query.addConstraint("audit_success", Operator.EQUAL, success);
      }

      if (dataRequest.containsKey("datapools") && dataRequest.get("datapools") != null) {
        List<Integer> datapools = (List<Integer>) dataRequest.get("datapools");
        if (!datapools.isEmpty()) query.addConstraint("datapoolid", Operator.IN, datapools);
      }

      if (dataRequest.containsKey("catalogNames") && dataRequest.get("catalogNames") != null) {
        List<String> entityNames = (List<String>) dataRequest.get("catalogNames");
        if (indexName.contains(AuditTrailConstants.AUDIT_INDEX_EDGE_COMMIT))
          indexName += "_" + entityNames.stream().findFirst().get();
        if (!entityNames.isEmpty()) query.addConstraint("catalogname", Operator.IN, entityNames);
      }

      if (dataRequest.containsKey("offset") && dataRequest.get("offset") != null) {
        offset = (int) dataRequest.get("offset");
      }

      if (dataRequest.containsKey("limit") && dataRequest.get("limit") != null) {
        limit = (int) dataRequest.get("limit");
      }

      query.getOrderByKeys().add(new OrderBy("audit_changed", OrderBy.OrderByType.DESC.name()));

      if (dataRequest.containsKey("orderByKeys") && dataRequest.get("orderByKeys") != null) {
        Map<String, String> orderByKeys = (Map<String, String>) dataRequest.get("orderByKeys");
        orderByKeys.forEach(
            (key, value) -> {
              if ("desc".equalsIgnoreCase(value)) {
                query.getOrderByKeys().add(new OrderBy(key, OrderBy.OrderByType.DESC.name()));
              } else {
                query.getOrderByKeys().add(new OrderBy(key, OrderBy.OrderByType.ASC.name()));
              }
            });
      }

      Catalog catalog = new Catalog();
      catalog.setEntityName("audit_" + source.toLowerCase());
      if (source.equalsIgnoreCase("PPDM") || source.equalsIgnoreCase("EDGE"))
        catalog.setEntityName(indexName);

      totalRecords = offset + limit;

      if (totalRecords <= ES_DEFAULT_DATA_SET_SIZE) {

        query.setOffset(offset);
        query.setLimit(limit);

        catalogSearchResponse =
            ((NoSqlDataStoreDriver) driver)
                .searchDocuments(auditTrailDataSource.getDataStoreConnection(), catalog, query);
      } else {
        query.setOffset(0);

        ((NoSqlDataStoreDriver) driver)
            .searchInBatch(
                auditTrailDataSource.getDataStoreConnection(),
                catalog,
                query,
                true,
                new BatchSearchResultListener() {
                  @Override
                  public void processItems(List<CatalogItem> catalogItems, long totalCount)
                      throws InterruptedException {
                    batchSearchResponse.getHits().addAll(catalogItems);
                    batchSearchResponse.setCount(totalCount);
                    if (batchSearchResponse.getHits().size() >= totalRecords) {
                      // if list size is >= to the records requested than stop scroll process
                      // because requested records are fetched by scroll no need to scroll on all
                      // records
                      // totalCount shows the actual records present in dataSource
                      throw new InterruptedException();
                    }
                  }
                });
        catalogSearchResponse.setCount(batchSearchResponse.getCount());
        if (totalRecords < catalogSearchResponse.getCount()) {
          catalogSearchResponse
              .getHits()
              .addAll(batchSearchResponse.getHits().subList(offset, totalRecords));
        } else {
          catalogSearchResponse
              .getHits()
              .addAll(
                  batchSearchResponse
                      .getHits()
                      .subList(offset, batchSearchResponse.getHits().size()));
        }
      }

      if (catalogSearchResponse != null && catalogSearchResponse.getHits().size() > 0) {

        List<AuditDataHit> dataHits = new ArrayList<>();

        catalogSearchResponse
            .getHits()
            .forEach(
                item -> {
                  AuditDataHit auditDataHit = new AuditDataHit();
                  Map<String, Object> metaData = new TreeMap<>();
                  Map<String, Object> dataItems = new TreeMap<>();
                  item.getValues()
                      .forEach(
                          (key, value) -> {
                            if (esKeyFields.contains(key)) {
                              // skip ES Key Fields
                            } else if (metaFields.contains(key)) {
                              metaData.put(key, value);
                            } else {

                              // Convert date format to target format
                              if (auditDateFields.stream().anyMatch(key::equals)) {
                                value =
                                    DateUtil.getInstance()
                                        .convertToDate((String) value, auditDateTargetFormat);
                              }

                              dataItems.put(key, value);
                            }
                          });
                  auditDataHit.setMetaData(metaData);
                  auditDataHit.setDataItems(dataItems);
                  dataHits.add(auditDataHit);
                });

        response.setCount(catalogSearchResponse.getCount());
        response.setHits(dataHits);
      }
    }
    return response;
  }

  public AuditResponse searchAuditData(Map<String, Object> dataRequest) throws Exception {

    DataStoreDriver driver = auditTrailDataSource.getDataStoreDriver();
    AuditResponse response = new AuditResponse();

    if (driver instanceof NoSqlDataStoreDriver) {

      int offset = 0;
      int limit = 10;

      int totalRecords;

      CatalogSearchResponse catalogSearchResponse = new CatalogSearchResponse();
      CatalogSearchResponse batchSearchResponse = new CatalogSearchResponse();

      CatalogQuery query = new CatalogQuery();

      if (dataRequest.containsKey("userSubjects") && dataRequest.get("userSubjects") != null) {
        List<String> userSubjects = (List<String>) dataRequest.get("userSubjects");
        if (!userSubjects.isEmpty())
          query.addConstraint("audit_usersubject", Operator.IN, userSubjects);
      }

      String source = "";
      String indexName = "";

      if (dataRequest.containsKey("sources") && dataRequest.get("sources") != null) {
        List<String> sources = (List<String>) dataRequest.get("sources");
        if (!sources.isEmpty()) {
          source = sources.stream().findFirst().get();
          query.addConstraint("audit_source", Operator.IN, sources);
        } else {
          throw new Exception("sources must not be empty.");
        }
      }

      if (dataRequest.containsKey("entityNames") && dataRequest.get("entityNames") != null) {
        List<String> entityNames = (List<String>) dataRequest.get("entityNames");
        if (source.equalsIgnoreCase("PPDM") || source.equalsIgnoreCase("EDGE"))
          indexName = "audit_" + entityNames.stream().findFirst().get();
        if (!entityNames.isEmpty() && !source.equalsIgnoreCase("EDGE"))
          query.addConstraint("audit_entity", Operator.IN, entityNames);
      }

      if (dataRequest.containsKey("constraints") && dataRequest.get("constraints") != null) {
        Map<String, Object> constraints = (Map<String, Object>) dataRequest.get("constraints");
        constraints.forEach(
            (key, value) -> {
              if (value != null && value != "") {
                List<String> values = Arrays.asList(value.toString().split(","));

                if (key.equals("audit_changed")) {
                  query
                      .getConstraints()
                      .add(
                          new Constraint(
                              key,
                              Operator.BETWEEN,
                              new String[] {
                                values.get(0) + AuditTrailConstants.START_TIMESTAMP_FORMAT,
                                values.get(1) + AuditTrailConstants.END_TIMESTAMP_FORMAT
                              },
                              Conjunction.AND));
                } else if (key.equals("audit_username")) {
                  query
                      .getConstraints()
                      .add(
                          new Constraint(
                              key + ".text", Operator.LIKE, values.get(0), Conjunction.AND));
                } else if (!values.get(0).equalsIgnoreCase("ALL")) {
                  query
                      .getConstraints()
                      .add(new Constraint(key, Operator.IN, values, Conjunction.AND));
                }
              }
            });
      }
      if (dataRequest.containsKey("searchText") && dataRequest.get("searchText") != null) {
        query
            .getConstraints()
            .add(new Constraint("*", Operator.MATCH, dataRequest.get("searchText").toString()));
      }

      if (dataRequest.containsKey("orderBy") && dataRequest.get("orderBy") != null) {
        if (dataRequest.get("orderBy").toString().equals("desc"))
          query.getOrderByKeys().add(new OrderBy("audit_changed", OrderBy.OrderByType.DESC.name()));
        else
          query.getOrderByKeys().add(new OrderBy("audit_changed", OrderBy.OrderByType.ASC.name()));
      } else {
        query.getOrderByKeys().add(new OrderBy("audit_changed", OrderBy.OrderByType.DESC.name()));
      }

      if (dataRequest.containsKey("success") && dataRequest.get("success") != null) {
        boolean success = (boolean) dataRequest.get("success");
        query.addConstraint("audit_success", Operator.EQUAL, success);
      }

      if (dataRequest.containsKey("datapools") && dataRequest.get("datapools") != null) {
        List<Integer> datapools = (List<Integer>) dataRequest.get("datapools");
        if (!datapools.isEmpty()) query.addConstraint("datapoolid", Operator.IN, datapools);
      }

      if (dataRequest.containsKey("catalogNames") && dataRequest.get("catalogNames") != null) {
        List<String> entityNames = (List<String>) dataRequest.get("catalogNames");
        if (indexName.contains(AuditTrailConstants.AUDIT_INDEX_EDGE_COMMIT))
          indexName += "_" + entityNames.stream().findFirst().get();
        if (!entityNames.isEmpty()) query.addConstraint("catalogname", Operator.IN, entityNames);
      }

      if (dataRequest.containsKey("offset") && dataRequest.get("offset") != null) {
        offset = (int) dataRequest.get("offset");
      }

      if (dataRequest.containsKey("limit") && dataRequest.get("limit") != null) {
        limit = (int) dataRequest.get("limit");
      }

      Catalog catalog = new Catalog();
      catalog.setEntityName("audit_" + source.toLowerCase());
      if (source.equalsIgnoreCase("PPDM") || source.equalsIgnoreCase("EDGE"))
        catalog.setEntityName(indexName);

      totalRecords = offset + limit;

      if (totalRecords <= ES_DEFAULT_DATA_SET_SIZE) {
        query.setOffset(offset);
        query.setLimit(limit);
        catalogSearchResponse =
            ((NoSqlDataStoreDriver) driver)
                .searchDocuments(auditTrailDataSource.getDataStoreConnection(), catalog, query);
      } else {
        query.setOffset(0);
        ((NoSqlDataStoreDriver) driver)
            .searchInBatch(
                auditTrailDataSource.getDataStoreConnection(),
                catalog,
                query,
                true,
                new BatchSearchResultListener() {
                  @Override
                  public void processItems(List<CatalogItem> catalogItems, long totalCount)
                      throws InterruptedException {
                    batchSearchResponse.getHits().addAll(catalogItems);
                    batchSearchResponse.setCount(totalCount);
                    if (batchSearchResponse.getHits().size() >= totalRecords) {
                      // if list size is >= to the records requested than stop scroll process
                      // because requested records are fetched by scroll no need to scroll on all
                      // records
                      // totalCount shows the actual records present in dataSource
                      throw new InterruptedException();
                    }
                  }
                });
        catalogSearchResponse.setCount(batchSearchResponse.getCount());
        if (totalRecords < catalogSearchResponse.getCount()) {
          catalogSearchResponse
              .getHits()
              .addAll(batchSearchResponse.getHits().subList(offset, totalRecords));
        } else {
          catalogSearchResponse
              .getHits()
              .addAll(
                  batchSearchResponse
                      .getHits()
                      .subList(offset, batchSearchResponse.getHits().size()));
        }
      }

      if (catalogSearchResponse != null && catalogSearchResponse.getHits().size() > 0) {

        List<AuditDataHit> dataHits = new ArrayList<>();

        catalogSearchResponse
            .getHits()
            .forEach(
                item -> {
                  AuditDataHit auditDataHit = new AuditDataHit();
                  Map<String, Object> metaData = new TreeMap<>();
                  Map<String, Object> dataItems = new TreeMap<>();
                  item.getValues()
                      .forEach(
                          (key, value) -> {
                            if (esKeyFields.contains(key)) {
                              // skip ES Key Fields
                            } else if (metaFields.contains(key)) {
                              metaData.put(key, value);
                            } else {

                              // Convert date format to target format
                              if (auditDateFields.stream().anyMatch(key::equals)) {
                                value =
                                    DateUtil.getInstance()
                                        .convertToDate((String) value, auditDateTargetFormat);
                              }

                              dataItems.put(key, value);
                            }
                          });
                  auditDataHit.setMetaData(metaData);
                  auditDataHit.setDataItems(dataItems);
                  dataHits.add(auditDataHit);
                });

        response.setCount(catalogSearchResponse.getCount());
        response.setHits(dataHits);
      }
    }
    return response;
  }

}
