package com.target.audit.enhancer;

import com.target.work.data.model.Catalog;
import com.target.work.data.model.CatalogItem;

/**
 * Interface for enhancing audit data during update operations.
 * Similar to AuditEnhancer but specifically for update operations.
 *
 * Implementations can intercept update operations and apply custom logic
 * such as calculated fields, validation, or specialized update mechanisms.
 */
public interface AuditUpdateEnhancer {

  /**
   * Get the name of this update enhancer for logging purposes.
   *
   * @return The enhancer name
   */
  String getName();

  /**
   * Check if this enhancer can handle the given update operation.
   *
   * @param catalog The catalog being updated
   * @param item The catalog item with update data
   * @return true if this enhancer should process the update
   */
  boolean canHandle(Catalog catalog, CatalogItem item);

  /**
   * Enhance the catalog item before update operation.
   *
   * This is the UPDATE equivalent of AuditEnhancer.enhance() for INSERT operations.
   *
   * The enhancer should:
   * - Fetch related data if needed (e.g., existing document fields)
   * - Calculate derived values (e.g., session duration)
   * - Add/modify fields in the CatalogItem
   * - NOT perform the actual update (service handles that)
   *
   * After this method returns, the service will call driver.updateItem()
   * with the enhanced item.
   *
   * @param catalog The catalog being updated
   * @param documentId The document ID to update
   * @param item The catalog item with update data (modify this to enhance)
   * @param dataStoreConnection The data store connection (for fetching related data)
   * @throws Exception if the enhancement fails
   */
  void performUpdate(Catalog catalog, String documentId, CatalogItem item, Object dataStoreConnection) throws Exception;
}
