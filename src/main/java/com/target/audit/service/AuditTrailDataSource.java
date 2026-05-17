package com.target.audit.service;

import com.target.audit.AuditServiceProperties;
import com.target.work.data.model.DataStoreConnection;
import com.target.work.data.model.DataStoreConnectionAction;
import com.target.work.data.service.DataStoreDriver;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuditTrailDataSource {

  private static final Logger logger = LoggerFactory.getLogger(AuditTrailService.class);

  @Autowired private AuditServiceProperties properties;

  private DataStoreDriver dataStoreDriver;

  private DataStoreConnection dataStoreConnection;

  public DataStoreDriver getDataStoreDriver() {
    return dataStoreDriver;
  }

  public void setDataStoreDriver(DataStoreDriver dataStoreDriver) {
    this.dataStoreDriver = dataStoreDriver;
  }

  @PostConstruct
  public void initialiseDataSource() {
    DataStoreConnection connection = new DataStoreConnection();
    connection.setDataServerUrl(properties.getUrl());
    connection.setDriverName(properties.getDriverName());
    connection.setUsername(properties.getUsername());
    connection.setPassword(properties.getPassword());
    connection.setUrl(properties.getUrl());
    DataStoreDriver driver = DataStoreConnectionAction.getDriver(connection, false);
    if (driver != null) {
      setDataStoreDriver(driver);
      setDataStoreConnection(connection);
    } else {
      logger.error(
          "Failed to initialise Data Store for service. This is required by service to do operations in underlying stores like NoSql,Sql etc");
    }
  }

  public DataStoreConnection getDataStoreConnection() {
    return dataStoreConnection;
  }

  public void setDataStoreConnection(DataStoreConnection dataStoreConnection) {
    this.dataStoreConnection = dataStoreConnection;
  }
}
