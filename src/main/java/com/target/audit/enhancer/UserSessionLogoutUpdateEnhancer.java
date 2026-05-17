package com.target.audit.enhancer;

import com.target.audit.service.AuditTrailDataSource;
import com.target.search.model.CatalogQuery;
import com.target.search.model.CatalogSearchResponse;
import com.target.search.model.Operator;
import com.target.work.data.model.Catalog;
import com.target.work.data.model.CatalogItem;
import com.target.work.data.service.DataStoreDriver;
import com.target.work.data.service.NoSqlDataStoreDriver;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.target.audit.enhancer.UserSessionConstants.*;

/**
 * Update enhancer for USER_SESSION logout operations.
 *
 * Similar to FileManagerDownloadEnhancer, this enhancer ONLY manipulates
 * values in the CatalogItem. The main service (AuditTrailService) handles
 * the actual update operation.
 *
 * This enhancer:
 * - Fetches the existing document to get loginTime (using DataStoreDriver)
 * - Calculates sessionDuration in minutes using Java ChronoUnit
 * - Adds sessionDuration and status="closed" to the item
 * - Service then performs standard update with enhanced values
 *
 * Used when updating an existing active session with logout information.
 */
@Component
public class UserSessionLogoutUpdateEnhancer implements AuditUpdateEnhancer {

  private static final Logger logger = LoggerFactory.getLogger(UserSessionLogoutUpdateEnhancer.class);

  private final AuditTrailDataSource auditTrailDataSource;

  // Constructor injection like FileManagerDownloadEnhancer
  public UserSessionLogoutUpdateEnhancer(AuditTrailDataSource auditTrailDataSource) {
    this.auditTrailDataSource = auditTrailDataSource;
  }

  @Override
  public String getName() {
    return "UserSessionLogoutUpdateEnhancer";
  }

  @Override
  public boolean canHandle(Catalog catalog, CatalogItem item) {
    // Check if this is a USER_SESSION index and has logoutTime field
    // This is consistent with UserSessionLogoutEnhancer logic
    String indexName = catalog.getEntityName();
    Object logoutTime = item.getValues().get(FIELD_LOGOUT_TIME);

    // Match USER_SESSION records with logoutTime
    boolean canHandle = indexName != null
        && indexName.contains(USER_SESSION_INDEX_PATTERN)
        && logoutTime != null
        && !logoutTime.toString().trim().isEmpty();

    if (canHandle) {
      logger.debug("UserSessionLogoutUpdateEnhancer will handle update for index: {}", indexName);
    }

    return canHandle;
  }

  @Override
  public void performUpdate(Catalog catalog, String documentId, CatalogItem item, Object dataStoreConnection) throws Exception {
    logger.info("Enhancing logout update with session duration calculation for document {} in index {}",
        documentId, catalog.getEntityName());

    try {
      // Step 1: Fetch existing document to get loginTime (using DataStoreDriver like FileManagerDownloadEnhancer)
      String loginTime = fetchLoginTimeFromDocument(catalog, documentId);

      if (loginTime == null) {
        logger.warn("Could not fetch loginTime from document {}, skipping duration calculation", documentId);
      } else {
        // Step 2: Calculate session duration in Java (like UserSessionLogoutEnhancer)
        String logoutTime = item.getValues().get(FIELD_LOGOUT_TIME).toString();
        long durationMinutes = calculateSessionDuration(loginTime, logoutTime);

        // Step 3: Add calculated fields to item
        item.getValues().put(FIELD_SESSION_DURATION, durationMinutes);
        logger.debug("Calculated session duration: {} minutes (login: {}, logout: {})",
            durationMinutes, loginTime, logoutTime);
      }

      // Step 4: Set status to closed
      item.getValues().put(FIELD_STATUS, STATUS_CLOSED);

      // Service will now call driver.updateItem() with the enhanced item

    } catch (Exception e) {
      logger.error("Failed to enhance logout update for document {}: {}", documentId, e.getMessage(), e);
      throw e;
    }
  }

  /**
   * Fetch loginTime from the existing document using DataStoreDriver.
   * Uses the same pattern as FileManagerDownloadEnhancer.
   *
   * @param catalog The catalog
   * @param documentId The document ID
   * @return The loginTime as string, or null if not found
   */
  private String fetchLoginTimeFromDocument(Catalog catalog, String documentId) {
    try {
      DataStoreDriver driver = auditTrailDataSource.getDataStoreDriver();

      if (!(driver instanceof NoSqlDataStoreDriver)) {
        logger.warn("DataStoreDriver is not NoSqlDataStoreDriver, cannot fetch loginTime");
        return null;
      }

      // Build query to fetch document by ID
      // Use a fresh Catalog with only the entity name (no field restrictions) so that
      // all fields (including loginTime) are returned in the search result.
      Catalog fetchCatalog = new Catalog();
      fetchCatalog.setEntityName(catalog.getEntityName());

      CatalogQuery query = new CatalogQuery();
      query.addConstraint("_id", Operator.EQUAL, documentId);
      query.setOffset(0);
      query.setLimit(1);

      CatalogSearchResponse searchResponse =
          ((NoSqlDataStoreDriver) driver)
              .searchDocuments(auditTrailDataSource.getDataStoreConnection(), fetchCatalog, query);

      if (searchResponse.getHits() != null && !searchResponse.getHits().isEmpty()) {
        CatalogItem existingItem = searchResponse.getHits().get(0);
        Object loginTimeObj = existingItem.getValues().get(FIELD_LOGIN_TIME);

        if (loginTimeObj != null) {
          String loginTime = loginTimeObj.toString();
          logger.debug("Fetched loginTime: {} for document {}", loginTime, documentId);
          return loginTime;
        } else {
          logger.warn("Document {} does not have {} field", documentId, FIELD_LOGIN_TIME);
          return null;
        }
      } else {
        logger.warn("Document {} not found in index {}", documentId, catalog.getEntityName());
        return null;
      }

    } catch (Exception e) {
      logger.warn("Could not fetch loginTime for document {}, defaulting to null: {}", documentId, e.getMessage());
      return null;
    }
  }

  /**
   * Calculate session duration in minutes.
   * Uses the same logic as UserSessionLogoutEnhancer (Java ChronoUnit).
   *
   * @param loginTimeStr Login time in ISO 8601 format
   * @param logoutTimeStr Logout time in ISO 8601 format
   * @return Duration in minutes
   */
  private long calculateSessionDuration(String loginTimeStr, String logoutTimeStr) {
    Instant loginTime = Instant.parse(loginTimeStr);
    Instant logoutTime = Instant.parse(logoutTimeStr);
    return ChronoUnit.MINUTES.between(loginTime, logoutTime);
  }
}
