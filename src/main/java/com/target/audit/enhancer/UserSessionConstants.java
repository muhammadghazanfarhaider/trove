package com.target.audit.enhancer;

/**
 * Shared constants for USER_SESSION audit enhancers.
 *
 * Used by:
 * - UserSessionLoginEnhancer (insert - adds IP, user agent, etc.)
 * - UserSessionLogoutUpdateEnhancer (update - calculates duration for logout)
 */
public final class UserSessionConstants {

  private UserSessionConstants() {
    // Utility class - prevent instantiation
  }

  // Audit source and entity identifiers
  public static final String AUDIT_SOURCE_USER_SESSION = "USER_SESSION";
  public static final String AUDIT_ENTITY_LOGIN = "Login";

  // Audit data events
  public static final String AUDIT_EVENT_CREATED = "created";
  public static final String AUDIT_EVENT_UPDATED = "updated";

  // Index pattern
  public static final String USER_SESSION_INDEX_PATTERN = "user_session";

  // Field names
  public static final String FIELD_LOGIN_TIME = "loginTime";
  public static final String FIELD_LOGOUT_TIME = "logoutTime";
  public static final String FIELD_SESSION_DURATION = "sessionDuration";
  public static final String FIELD_STATUS = "status";
  public static final String FIELD_IP_ADDRESS = "ipAddress";
  public static final String FIELD_USER_AGENT = "userAgent";

  // Status values
  public static final String STATUS_ACTIVE = "active";
  public static final String STATUS_CLOSED = "closed";
  public static final String STATUS_EXPIRED = "expired";

  // Duration calculation
  public static final long MILLISECONDS_PER_MINUTE = 60000L;
}
