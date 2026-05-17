package com.target.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan({"com.target.audit", "com.target.app.service", "com.target.connector.nosql"})
@EnableConfigurationProperties(AuditServiceProperties.class)
@EnableAutoConfiguration(
  exclude = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
    org.springframework.boot.actuate.autoconfigure.security.servlet
        .ManagementWebSecurityAutoConfiguration.class
  }
)
public class AuditTrailServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuditTrailServiceApplication.class, args);
  }

 
}
