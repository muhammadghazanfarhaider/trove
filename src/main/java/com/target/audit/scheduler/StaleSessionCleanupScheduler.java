package com.target.audit.scheduler;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.target.audit.service.AuditTrailDataSource;

/**
 * Scheduler to close stale user session audit records. Runs periodically to find
 * records stuck in "active" status for longer than the configured threshold (default 8H)
 * and marks them as "closed".
 *
 * <p>This handles cases where users don't explicitly log out and their sessions remain
 * in active state indefinitely. After the configured threshold, the session is automatically
 * closed for accurate session duration tracking and reporting.
 *
 * <p>Threshold format: &lt;number&gt;H (hours) or &lt;number&gt;M (minutes)
 * Examples: 8H, 10M, 24H, 30M
 *
 * <p>This bean is only created when audit.stale.session.enabled=true (default: false).
 * To enable, set audit.stale.session.enabled=true in application.properties or via environment variable.
 */
@Component
@ConditionalOnProperty(
  name = "audit.stale.session.enabled",
  havingValue = "true",
  matchIfMissing = false // Disabled by default
)
public class StaleSessionCleanupScheduler {

  private static final Logger logger = LoggerFactory.getLogger(StaleSessionCleanupScheduler.class);

  @Autowired private AuditTrailDataSource auditTrailDataSource;

  @Value("${audit.stale.session.threshold:8H}")
  private String staleThreshold;

  @Value("${audit.stale.session.index.name:audit_user_session}")
  private String indexName;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Scheduled job to close stale sessions. Default: Runs every hour (configurable via cron expression).
   *
   * <p>Cron format: second minute hour day month weekday
   * Default: "0 0 * * * ?" = every hour at 0 minutes
   *
   * <p>Note: This method only executes if the bean is created (when audit.stale.session.enabled=true).
   */
  @Scheduled(cron = "${audit.stale.session.cron:0 0 * * * ?}")
  public void closeStaleUserSessions() {
    try {
      logger.info(
          "Starting stale session cleanup job. Index: {}, Threshold: {}",
          indexName,
          staleThreshold);

      RestHighLevelClient client =
          ClearAuditHistory.getRestClient(auditTrailDataSource.getDataStoreConnection());

      if (client == null) {
        logger.error("Failed to get Elasticsearch client. Skipping stale session cleanup.");
        return;
      }

      int updatedCount = updateStaleSessions(client);

      if (updatedCount > 0) {
        logger.info("Closed {} stale session(s)", updatedCount);
      } else {
        logger.debug("No stale sessions found");
      }

    } catch (Exception e) {
      logger.error("Error during stale session cleanup: {}", e.getMessage(), e);
    }
  }

  /**
   * Parse the threshold configuration string (e.g., "8H", "10M") into amount and time unit.
   * Supports H (hours) and M (minutes).
   *
   * @return Array where [0] is the amount and [1] is the ChronoUnit
   * @throws IllegalArgumentException if the format is invalid
   */
  private Object[] parseThreshold() {
    if (staleThreshold == null || staleThreshold.isEmpty()) {
      throw new IllegalArgumentException("Threshold configuration is empty");
    }

    String upperThreshold = staleThreshold.toUpperCase().trim();

    if (upperThreshold.endsWith("H")) {
      long hours = Long.parseLong(upperThreshold.substring(0, upperThreshold.length() - 1));
      return new Object[] {hours, ChronoUnit.HOURS};
    } else if (upperThreshold.endsWith("M")) {
      long minutes = Long.parseLong(upperThreshold.substring(0, upperThreshold.length() - 1));
      return new Object[] {minutes, ChronoUnit.MINUTES};
    } else {
      throw new IllegalArgumentException(
          "Invalid threshold format: " + staleThreshold + ". Expected format: <number>H or <number>M (e.g., 8H, 10M)");
    }
  }

  /**
   * Update stale session records using Elasticsearch Update By Query API.
   * Finds active sessions older than the threshold and marks them as closed.
   *
   * @param client Elasticsearch REST client
   * @return Number of documents updated
   * @throws IOException if the update fails
   */
  private int updateStaleSessions(RestHighLevelClient client) throws IOException {
    // Parse threshold configuration
    Object[] thresholdParts = parseThreshold();
    long amount = (long) thresholdParts[0];
    ChronoUnit unit = (ChronoUnit) thresholdParts[1];

    // Calculate cutoff timestamp (current time - threshold)
    Instant cutoffTime = Instant.now().minus(amount, unit);
    String cutoffISO = cutoffTime.toString();

    logger.debug("Cutoff time for stale sessions: {} (threshold: {} {})", cutoffISO, amount, unit);

    // Build Update By Query request
    // Query: Find records where:
    //   1. audit_source = "USER_SESSION"
    //   2. audit_entity = "Login"
    //   3. status = "active"
    //   4. loginTime < cutoffTime (older than threshold)
    Map<String, Object> updateByQueryRequest = new HashMap<>();

    // Query to find stale sessions
    Map<String, Object> query = new HashMap<>();
    Map<String, Object> bool = new HashMap<>();
    java.util.List<Map<String, Object>> must = new java.util.ArrayList<>();

    // Match audit_source = USER_SESSION
    Map<String, Object> sourceMatch = new HashMap<>();
    sourceMatch.put("match", Map.of("audit_source", "USER_SESSION"));
    must.add(sourceMatch);

    // Match entity = Login
    Map<String, Object> entityMatch = new HashMap<>();
    entityMatch.put("match", Map.of("audit_entity", "Login"));
    must.add(entityMatch);

    // Match status = active
    Map<String, Object> statusMatch = new HashMap<>();
    statusMatch.put("match", Map.of("status", "active"));
    must.add(statusMatch);

    // Range query: loginTime < cutoffTime (older than threshold)
    // Note: Adjust field name if your login time field has a different name
    Map<String, Object> rangeQuery = new HashMap<>();
    rangeQuery.put("range", Map.of("loginTime", Map.of("lt", cutoffISO)));
    must.add(rangeQuery);

    bool.put("must", must);
    query.put("bool", bool);
    updateByQueryRequest.put("query", query);

    // Calculate threshold in milliseconds to pass as script param
    long thresholdMillis = unit.getDuration().toMillis() * amount;

    // Script to update the status field, set logout time to loginTime + threshold,
    // and set session duration to the exact threshold value (not time of scheduler run).
    Map<String, Object> script = new HashMap<>();
    script.put(
        "source",
        "ctx._source.status = 'expired';" +
        "ctx._source.audit_changed = params.now;" +
        "if (ctx._source.loginTime != null) {" +
        "  long loginMillis = ZonedDateTime.parse(ctx._source.loginTime).toInstant().toEpochMilli();" +
        "  long expiryMillis = loginMillis + params.thresholdMillis;" +
        "  ctx._source.logoutTime = Instant.ofEpochMilli(expiryMillis).toString();" +
        "  ctx._source.sessionDuration = params.thresholdMillis / 60000;" +
        "}");

    Map<String, Object> params = new HashMap<>();
    params.put("now", Instant.now().toString());
    params.put("thresholdMillis", thresholdMillis);
    script.put("params", params);
    script.put("lang", "painless");
    updateByQueryRequest.put("script", script);

    // Execute Update By Query
    String requestBody = objectMapper.writeValueAsString(updateByQueryRequest);
    logger.debug("Update by query request: {}", requestBody);

    logger.debug("Cutoff time: {}", cutoffISO);
    logger.debug("Query JSON: {}", requestBody);

    Request request = new Request("POST", "/" + indexName + "/_update_by_query");
    request.setJsonEntity(requestBody);
    request.addParameter("conflicts", "proceed"); // Proceed even if version conflicts occur
    request.addParameter("refresh", "true"); // Refresh indices after update

    Response response = client.getLowLevelClient().performRequest(request);
    String responseBody = EntityUtils.toString(response.getEntity());

    logger.debug("Update by query response: {}", responseBody);

    // Parse response to get updated count
    @SuppressWarnings("unchecked")
    Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
    Integer updated = (Integer) responseMap.get("updated");

    return updated != null ? updated : 0;
  }
}
