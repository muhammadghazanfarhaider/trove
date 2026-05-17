package com.target.audit.scheduler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuseim.common.util.Util;
import com.target.audit.service.AuditTrailDataSource;
import com.target.work.data.model.DataStoreConnection;

/**
 * Scheduler to clean up old audit history records from Elasticsearch indices.
 * Runs periodically based on cron configuration to delete records older than the configured threshold.
 *
 * <p>This bean is only created when audit.clean.audit.history.enabled=true (default: false).
 * To enable, set audit.clean.audit.history.enabled=true in application.properties or via environment variable.
 */
@Component
@ConditionalOnProperty(
		  name = "audit.clean.audit.history.enabled",
		  havingValue = "true",
		  matchIfMissing = false // Disabled by default - only enabled when property is explicitly set to true
		)
public class ClearAuditHistory {

  @Autowired private AuditTrailDataSource auditTrailDataSource;

  @Value("${es.audit.doc.threshold:500}")
  private Integer auditDocThreshold;

  @Value("${es.audit.mm-dd:1Y}")
  private String auditMMDD;

  private SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
  private Calendar cal = Calendar.getInstance();
  private Integer sdate = 0;
  private String mapkey = "";
  private static final Logger logger = LoggerFactory.getLogger(ClearAuditHistory.class);

  /**
   * Scheduled job to clean audit history.
   * Default: Runs based on ${es.cron.param} configuration.
   *
   * <p>Note: This method only executes if the bean is created (when audit.clean.audit.history.enabled=true).
   */
  @Scheduled(cron = "${es.cron.param:-}")
  public void scheduledCleanAuditHistory() {
    logger.info("Starting scheduled audit history cleanup at: " + Calendar.getInstance().getTime());
    try {
      clearAuditHist();
    } catch (IOException e) {
      logger.error("Error during scheduled audit history cleanup: {}", e.getMessage(), e);
    }
  }

  public void clearAuditHist() throws IOException {

    if (auditMMDD.contains("M")) {
      sdate = Integer.parseInt(auditMMDD.replace("M", ""));
      cal.add(Calendar.MONTH, -sdate);
    } else if (auditMMDD.contains("D")) {
      sdate = Integer.parseInt(auditMMDD.replace("D", ""));
      cal.add(Calendar.DAY_OF_MONTH, -sdate);
    } else if (auditMMDD.contains("Y")) {
      sdate = Integer.parseInt(auditMMDD.replace("Y", ""));
      cal.add(Calendar.YEAR, -sdate);
    }

    ObjectMapper mapper = new ObjectMapper();

    // Prepared Query request for search indeces count
    Request getRequest =
        new Request("GET", "_cat/indices/audit_*?v&pri=false&h=index,docs.count&format=JSON");
    RestHighLevelClient client = getRestClient(auditTrailDataSource.getDataStoreConnection());
    InputStream inputStream =
        client.getLowLevelClient().performRequest(getRequest).getEntity().getContent();

    // Recieved values as json list
    List<String> indexes =
        new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.toList());
    String list = indexes.get(0);
    List<Map<String, Object>> myObjects =
        mapper.readValue(list, new TypeReference<List<Map<String, Object>>>() {});

    // iterate over to put values in map to test threshold value
    myObjects.forEach(
        item ->
            item.entrySet()
                .forEach(
                    entry -> {
                      if (entry.getKey().equals("index")) {
                        mapkey = (String) entry.getValue();
                      } else {
                        if (Integer.parseInt(entry.getValue().toString()) > auditDocThreshold) {
                          deleteRecords(mapkey, client);
                        }
                      }
                    }));
  }

  /** Method to delete desired records from index */
  public void deleteRecords(String index, RestHighLevelClient client) {
    DeleteByQueryRequest request = new DeleteByQueryRequest(index);

    // Build range query with constraint column
    RangeQueryBuilder r =
        QueryBuilders.rangeQuery("audit_changed").to(format.format(cal.getTime())).from(null);
    request.setQuery(r);
    try {
      BulkByScrollResponse bulkResponse = client.deleteByQuery(request, RequestOptions.DEFAULT);
      logger.debug("bulkResponse Value ::::::" + bulkResponse);
      logger.debug("Audit Index documents Deleted Successful ::::::" + index);

    } catch (Exception e) {
      logger.debug("failed due to ::::::" + e.getMessage(), e);
    }
  }

  public static RestHighLevelClient getRestClient(DataStoreConnection connection) {
    RestHighLevelClient restHighLevelClient = null;
    try {
      URL elasticServiceUrl = new URL(connection.getUrl());

      RestClientBuilder builder =
          RestClient.builder(
              new HttpHost(
                  elasticServiceUrl.getHost(),
                  elasticServiceUrl.getPort(),
                  elasticServiceUrl.getProtocol()));

      if (!Util.isEmpty(connection.getUsername()) && !Util.isEmpty(connection.getPassword())) {

        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
            AuthScope.ANY,
            new UsernamePasswordCredentials(connection.getUsername(), connection.getPassword()));

        builder.setHttpClientConfigCallback(
            new RestClientBuilder.HttpClientConfigCallback() {
              @Override
              public HttpAsyncClientBuilder customizeHttpClient(
                  HttpAsyncClientBuilder httpClientBuilder) {
                return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
              }
            });
      }

      restHighLevelClient = new RestHighLevelClient(builder);

    } catch (MalformedURLException ex) {
      logger.error(
          "Connection with elastic service can't be established"
              + ex.getMessage()
              + "with URL "
              + connection.getUrl());
    } catch (Exception ex) {
      logger.error(
          "Connection with elastic service can't be established"
              + ex.getMessage()
              + "with URL "
              + connection.getUrl(),
          ex);
    }
    return restHighLevelClient;
  }
}
