package com.target.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("audit-trail")
public class AuditServiceProperties {

  private String driverName;
  private String url;
  private String username;
  private String password;
  private String dataServerUrl;

  /**
   * System will construct the target schema/table from entity name. For instance ,in case of
   * Elastic Search driver , entity name will be used to create index and document type. The format
   * will be indexName:documentType . For other drivers entity name will be used accordingly.
   */
  private String auditTrailEntityName;

  public String getDriverName() {
    return driverName;
  }

  public void setDriverName(String driverName) {
    this.driverName = driverName;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getDataServerUrl() {
    return dataServerUrl;
  }

  public void setDataServerUrl(String dataServerUrl) {
    this.dataServerUrl = dataServerUrl;
  }

  public String getAuditTrailEntityName() {
    return auditTrailEntityName;
  }

  public void setAuditTrailEntityName(String auditTrailEntityName) {
    this.auditTrailEntityName = auditTrailEntityName;
  }
}
