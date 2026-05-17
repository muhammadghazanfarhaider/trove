package com.target.audit.enhancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.target.work.data.model.CatalogItem;

import static com.target.audit.enhancer.UserSessionConstants.*;

/**
 * Enhancer for USER_SESSION login audit records during INSERT operations. Captures the client IP
 * address for login events to track login sources.
 *
 * <p>This is used when: - User logs in via the frontend - New session record is created - Login
 * audit event is generated
 *
 * <p>Works together with: - UserSessionLogoutUpdateEnhancer (handles logout updates with duration
 * calculation)
 */
@Component
public class UserSessionLoginEnhancer implements AuditEnhancer {

  private static final Logger logger = LoggerFactory.getLogger(UserSessionLoginEnhancer.class);

  @Override
  public boolean canHandle(CatalogItem item) {
    Object auditSource = item.getValues().get("audit_source");
    Object auditEntity = item.getValues().get("audit_entity");
    Object auditDataEvent = item.getValues().get("audit_dataevent");

    return AUDIT_SOURCE_USER_SESSION.equals(String.valueOf(auditSource))
        && AUDIT_ENTITY_LOGIN.equals(String.valueOf(auditEntity))
        && AUDIT_EVENT_CREATED.equals(String.valueOf(auditDataEvent));
  }

  @Override
  public void enhance(CatalogItem item, String clientIp) {
    try {
      if (clientIp != null && !clientIp.isEmpty()) {

        // Set client IP (field name is client_ip, not ipAddress from constants)
        // This is for backward compatibility with existing records
        item.getValues().put("clientIP", clientIp);
        logger.debug("Set client IP address to {} for login audit", clientIp);
      } else {
        logger.warn("Client IP is null or empty, skipping IP capture for login audit");
      }
    } catch (Exception e) {
      logger.warn("Failed to set client IP, continuing with save: " + e.getMessage());
      // Don't fail the save operation if IP capture fails
    }
  }
}
