package com.target.audit.enhancer;

import com.target.work.data.model.CatalogItem;

/**
 * Interface for audit data enhancers.
 * Implementations can add additional fields or perform transformations
 * on audit records based on specific conditions.
 *
 * <p>This follows the Strategy pattern to keep the main service clean
 * and make it easy to add new enhancement logic.</p>
 */
public interface AuditEnhancer {

  /**
   * Determines if this enhancer should process the given audit item.
   *
   * @param item The catalog item containing audit data
   * @return true if this enhancer should process the item
   */
  boolean canHandle(CatalogItem item);

  /**
   * Enhances the audit item by adding or modifying fields.
   *
   * @param item The catalog item to enhance
   * @param clientIp The client IP address (may be null for non-HTTP sources)
   */
  void enhance(CatalogItem item, String clientIp);

  /**
   * Gets the name of this enhancer for logging purposes.
   *
   * @return A descriptive name for this enhancer
   */
  default String getName() {
    return this.getClass().getSimpleName();
  }
}
