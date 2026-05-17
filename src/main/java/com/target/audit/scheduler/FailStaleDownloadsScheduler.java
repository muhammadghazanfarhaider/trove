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
 * Scheduler to mark stale download audit records as DOWNLOAD_FAILED. Runs periodically to find
 * records stuck in DOWNLOAD_STARTED or DOWNLOAD_IN_PROGRESS status for longer than the configured
 * threshold and marks them as failed.
 *
 * <p>This bean is only created when audit.stale.download.enabled=true (default: false). To enable,
 * set audit.stale.download.enabled=true in application.properties or via environment variable.
 */
@Component
@ConditionalOnProperty(
  name = "audit.stale.download.enabled",
  havingValue = "true",
  matchIfMissing = false // Disabled by default
)
public class FailStaleDownloadsScheduler {

  private static final Logger logger = LoggerFactory.getLogger(FailStaleDownloadsScheduler.class);

  @Autowired private AuditTrailDataSource auditTrailDataSource;

  @Value("${audit.stale.download.threshold.minutes:1}")
  private int staleThresholdMinutes;

  @Value("${audit.stale.download.index.name:audit_file_manager}")
  private String indexName;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Scheduled job to mark stale downloads as failed. Default: Runs every minute (configurable via
   * cron expression).
   *
   * <p>Cron format: second minute hour day month weekday Default: "0 * * * * ?" = every minute at 0
   * seconds
   *
   * <p>Note: This method only executes if the bean is created (when
   * audit.stale.download.enabled=true).
   */
  @Scheduled(cron = "${audit.stale.download.cron:0 * * * * ?}")
  public void markStaleDownloadsAsFailed() {
    try {
      logger.info(
          "Starting stale download cleanup job. Index: {}, Threshold: {} minute(s)",
          indexName,
          staleThresholdMinutes);

      RestHighLevelClient client =
          ClearAuditHistory.getRestClient(auditTrailDataSource.getDataStoreConnection());

      if (client == null) {
        logger.error("Failed to get Elasticsearch client. Skipping stale download cleanup.");
        return;
      }

      int updatedCount = updateStaleDownloads(client);

      if (updatedCount > 0) {
        logger.info("Marked {} stale download(s) as DOWNLOAD_FAILED", updatedCount);
      } else {
        logger.debug("No stale downloads found");
      }

    } catch (Exception e) {
      logger.error("Error during stale download cleanup: {}", e.getMessage(), e);
    }
  }

  /**
   * Update stale download records using Elasticsearch Update By Query API. This performs a bulk
   * update in a single operation without fetching records first.
   *
   * @param client Elasticsearch REST client
   * @return Number of documents updated
   * @throws IOException if the update fails
   */
  private int updateStaleDownloads(RestHighLevelClient client) throws IOException {
    // Calculate cutoff timestamp (current time - threshold)
    Instant cutoffTime = Instant.now().minus(staleThresholdMinutes, ChronoUnit.MINUTES);
    String cutoffISO = cutoffTime.toString();

    logger.debug("Cutoff time for stale downloads: {}", cutoffISO);

    // Build Update By Query request
    // Query: Find records where:
    //   1. audit_source = "FILE_MANAGER"
    //   2. audit_entity = "Document"
    //   3. audit_dataevent = "downloaded"
    //   4. status = "DOWNLOAD_STARTED" OR "DOWNLOAD_IN_PROGRESS"
    //   5. audit_changed < cutoffTime (older than threshold)
    Map<String, Object> updateByQueryRequest = new HashMap<>();

    // Query to find stale downloads
    Map<String, Object> query = new HashMap<>();
    Map<String, Object> bool = new HashMap<>();
    java.util.List<Map<String, Object>> must = new java.util.ArrayList<>();

    // Match audit_source = FILE_MANAGER
    Map<String, Object> sourceMatch = new HashMap<>();
    sourceMatch.put("match", Map.of("audit_source", "FILE_MANAGER"));
    must.add(sourceMatch);

    // Match entity = Document
    Map<String, Object> entityMatch = new HashMap<>();
    entityMatch.put("match", Map.of("audit_entity", "Document"));
    must.add(entityMatch);

    // Match dataEvent = downloaded
    Map<String, Object> eventMatch = new HashMap<>();
    eventMatch.put("match", Map.of("audit_dataevent", "downloaded"));
    must.add(eventMatch);

    // Match status = DOWNLOAD_STARTED OR DOWNLOAD_IN_PROGRESS
    // Note: status is a direct field at root level, not in dataItemList
    Map<String, Object> statusShould = new HashMap<>();
    java.util.List<Map<String, Object>> shouldClauses = new java.util.ArrayList<>();
    shouldClauses.add(Map.of("match", Map.of("status", "DOWNLOAD_STARTED")));
    shouldClauses.add(Map.of("match", Map.of("status", "DOWNLOAD_IN_PROGRESS")));
    statusShould.put("bool", Map.of("should", shouldClauses, "minimum_should_match", 1));
    must.add(statusShould);

    // Range query: audit_changed < cutoffTime
    Map<String, Object> rangeQuery = new HashMap<>();
    rangeQuery.put("range", Map.of("audit_changed", Map.of("lt", cutoffISO)));
    must.add(rangeQuery);

    bool.put("must", must);
    query.put("bool", bool);
    updateByQueryRequest.put("query", query);

    // Script to update the status field (direct field at root level)
    Map<String, Object> script = new HashMap<>();
    script.put(
        "source",
        "ctx._source.status = 'DOWNLOAD_FAILED';" + "ctx._source.audit_changed = params.now;");
    script.put("params", Map.of("now", Instant.now().toString()));
    script.put("lang", "painless");
    updateByQueryRequest.put("script", script);

    // Execute Update By Query
    String requestBody = objectMapper.writeValueAsString(updateByQueryRequest);
    logger.debug("Update by query request: {}", requestBody);

    logger.debug("Cutoff time: " + cutoffISO);
    logger.debug("Query JSON: " + requestBody);

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
