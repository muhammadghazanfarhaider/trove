package com.target.audit.enhancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.target.audit.service.AuditTrailDataSource;
import com.target.search.model.CatalogQuery;
import com.target.search.model.CatalogSearchResponse;
import com.target.search.model.Operator;
import com.target.work.data.model.Catalog;
import com.target.work.data.model.CatalogItem;
import com.target.work.data.service.DataStoreDriver;
import com.target.work.data.service.NoSqlDataStoreDriver;

/**
 * Enhancer for FILE_MANAGER download audit records.
 * Adds attempt count by querying previous download attempts for the same user and file.
 */
@Component
public class FileManagerDownloadEnhancer implements AuditEnhancer {

  private static final Logger logger = LoggerFactory.getLogger(FileManagerDownloadEnhancer.class);

  private final AuditTrailDataSource auditTrailDataSource;

  public FileManagerDownloadEnhancer(AuditTrailDataSource auditTrailDataSource) {
    this.auditTrailDataSource = auditTrailDataSource;
  }

  @Override
  public boolean canHandle(CatalogItem item) {
    Object auditSource = item.getValues().get("audit_source");
    Object auditEntity = item.getValues().get("audit_entity");
    Object auditDataEvent = item.getValues().get("audit_dataevent");

    return "FILE_MANAGER".equals(String.valueOf(auditSource))
        && "Document".equals(String.valueOf(auditEntity))
        && "downloaded".equals(String.valueOf(auditDataEvent));
  }

  @Override
  public void enhance(CatalogItem item, String clientIp) {
    try {
      String filepath = (String) item.getValues().get("filepath");
      String userSubject = (String) item.getValues().get("audit_usersubject");

      if (filepath == null || userSubject == null) {
        logger.warn("Cannot set attempt count: filepath or audit_usersubject is null");
        return;
      }

      // Count previous attempts for same user and file
      int previousAttempts = countPreviousDownloadAttempts(filepath, userSubject);
      int currentAttempt = previousAttempts + 1;

      item.getValues().put("attempt", currentAttempt);
      logger.debug(
          "Set attempt count to {} for user {} and file {}", currentAttempt, userSubject, filepath);

    } catch (Exception e) {
      logger.warn("Failed to set attempt count, continuing with save: " + e.getMessage());
      // Don't fail the save operation if attempt counting fails
    }
  }

  /**
   * Counts previous download attempts for the same user and file.
   *
   * @param filepath The file path
   * @param userSubject The user subject
   * @return The count of previous attempts
   */
  private int countPreviousDownloadAttempts(String filepath, String userSubject) {
    try {
      DataStoreDriver driver = auditTrailDataSource.getDataStoreDriver();

      if (!(driver instanceof NoSqlDataStoreDriver)) {
        logger.warn("DataStoreDriver is not NoSqlDataStoreDriver, cannot count attempts");
        return 0;
      }

      // Build query
      CatalogQuery query = new CatalogQuery();
      query.addConstraint("audit_source", Operator.EQUAL, "FILE_MANAGER");
      query.addConstraint("audit_entity", Operator.EQUAL, "Document");
      query.addConstraint("audit_dataevent", Operator.EQUAL, "downloaded");
      query.addConstraint("filepath", Operator.EQUAL, filepath);
      query.addConstraint("audit_usersubject", Operator.EQUAL, userSubject);
      query.setOffset(0);
      query.setLimit(0); // Only need count

      Catalog catalog = new Catalog();
      catalog.setEntityName("audit_file_manager");

      CatalogSearchResponse searchResponse =
          ((NoSqlDataStoreDriver) driver)
              .searchDocuments(auditTrailDataSource.getDataStoreConnection(), catalog, query);

      long count = searchResponse.getCount();
      logger.debug(
          "Found {} previous download attempts for user {} and file {}",
          count,
          userSubject,
          filepath);

      return (int) count;

    } catch (Exception e) {
      logger.warn("Could not count previous download attempts, defaulting to 0: " + e.getMessage());
      return 0;
    }
  }
}
