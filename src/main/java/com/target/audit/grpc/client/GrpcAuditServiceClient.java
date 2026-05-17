package com.target.audit.grpc.client;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.target.audit.grpc.AuditDataItem;
import com.target.audit.grpc.AuditRequest;
import com.target.audit.grpc.AuditResponse;
import com.target.audit.grpc.AuditServiceGrpc;
import com.target.audit.grpc.FieldType;
import com.target.audittrail.model.AuditData;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@Component
public class GrpcAuditServiceClient {

  private static final Logger logger = LoggerFactory.getLogger(GrpcAuditServiceClient.class);

  private AuditServiceGrpc.AuditServiceBlockingStub auditServiceBlockingStub;

  @PostConstruct
  private void init() {
    ManagedChannel managedChannel =
        ManagedChannelBuilder.forAddress("localhost", 6565).usePlaintext().build();

    auditServiceBlockingStub = AuditServiceGrpc.newBlockingStub(managedChannel);
    logger.info("gRPC client initialized successfully");
  }

  public String audit(long timeInMilliSecond) {
    AuditResponse auditResponse =
        auditServiceBlockingStub.audit(
            AuditRequest.newBuilder()
                .setUsername("testUserName" + timeInMilliSecond)
                .setSource("testSource" + timeInMilliSecond)
                .addDataEvent(AuditRequest.DataEvent.created)
                .setEntity("testEntity" + timeInMilliSecond)
                .setDatakey("testKey" + timeInMilliSecond)
                .setChanged("01/01/2019")
                .addAuditDataItems(
                    AuditDataItem.newBuilder()
                        .setFieldName("testField")
                        .addFieldType(FieldType.valueOf("STRING"))
                        .setValue("testFieldValue" + timeInMilliSecond)
                        .build())
                .build());

    logger.debug("Audit Response Status : " + auditResponse.getStatus());
    return auditResponse.getStatus();
  }

  public String audit(AuditData auditDataRequest) {
    AuditRequest.Builder auditRequestBuilder =
        AuditRequest.newBuilder()
            .setUsername(auditDataRequest.getAudit_username())
            .setUsersubject(auditDataRequest.getAudit_usersubject())
            .setSource(auditDataRequest.getAudit_source())
            .addDataEvent(
                AuditRequest.DataEvent.valueOf(auditDataRequest.getAudit_dataevent().name()))
            .setEntity(auditDataRequest.getAudit_entity())
            .setDatakey(auditDataRequest.getAudit_datakey())
            .setChanged(auditDataRequest.getAudit_changed().toString());

    for (com.target.audittrail.model.AuditDataItem auditDataItem :
        auditDataRequest.getDataItemList()) {
      auditRequestBuilder.addAuditDataItems(
          AuditDataItem.newBuilder()
              .setFieldName(auditDataItem.getFieldName())
              .addFieldType(
                  FieldType.valueOf(
                      FieldType.getDescriptor()
                          .findValueByName(auditDataItem.getFieldType().name())))
              .setValue(auditDataItem.getValue().toString())
              .build());
    }

    AuditRequest auditRequest = auditRequestBuilder.build();
    logger.info("GrpcAuditServiceClient sending " + auditRequest);

    AuditResponse auditResponse = auditServiceBlockingStub.audit(auditRequest);

    logger.info("Audit Response Status : " + auditResponse.getStatus());
    return auditResponse.getStatus();
  }
}
