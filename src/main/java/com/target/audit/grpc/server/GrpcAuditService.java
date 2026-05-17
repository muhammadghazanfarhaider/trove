package com.target.audit.grpc.server;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.lognet.springboot.grpc.GRpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.target.audit.grpc.AuditDataItem;
import com.target.audit.grpc.AuditRequest;
import com.target.audit.grpc.AuditResponse;
import com.target.audit.grpc.AuditServiceGrpc;
import com.target.audit.service.AuditTrailService;
import com.target.audittrail.model.AuditData;
import com.target.site.model.FieldType;

import io.grpc.stub.StreamObserver;

@GRpcService
public class GrpcAuditService extends AuditServiceGrpc.AuditServiceImplBase {

  private static final Logger logger = LoggerFactory.getLogger(GrpcAuditService.class);

  @Autowired private AuditTrailService auditTrailService;

  @Override
  public void audit(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {
    logger.info("Audit Request received:\n" + request);

    AuditData auditDataRequest = createAuditDataFromGrpcRequest(request);
    AuditResponse.Builder builder = AuditResponse.newBuilder();
    try {
      auditTrailService.handleAuditData(auditDataRequest);
      logger.info("grpc audit service action performed successfully");
      builder.setStatus("Success");
    } catch (Exception exception) {
      logger.error("Error occured while performing audit action" + exception);
      builder.setStatus("Failure");
    }

    responseObserver.onNext(builder.build());
    responseObserver.onCompleted();
  }

  private AuditData createAuditDataFromGrpcRequest(AuditRequest auditRequest) {
    AuditData auditDataRequest = new AuditData();
    auditDataRequest.setAudit_username(auditRequest.getUsername());
    auditDataRequest.setAudit_usersubject(auditRequest.getUsersubject());
    auditDataRequest.setAudit_source(auditRequest.getSource());
    auditDataRequest.setAudit_dataevent(
        AuditData.DataEvent.valueOf(auditRequest.getDataEvent(0).name()));
    auditDataRequest.setAudit_entity(auditRequest.getEntity());
    auditDataRequest.setAudit_datakey(auditRequest.getDatakey());
    auditDataRequest.setAudit_changed(new Date(auditRequest.getChanged()));
    List<com.target.audittrail.model.AuditDataItem> auditDataItems =
        new ArrayList<com.target.audittrail.model.AuditDataItem>();
    for (AuditDataItem auditDataItem : auditRequest.getAuditDataItemsList()) {
      com.target.audittrail.model.AuditDataItem auditDataItemModel =
          new com.target.audittrail.model.AuditDataItem();
      auditDataItemModel.setFieldName(auditDataItem.getFieldName());
      if (auditDataItem.getFieldTypeCount() >= 1) {
        auditDataItemModel.setFieldType(
            FieldType.valueOfTypeName(
                auditDataItem.getFieldType(auditDataItem.getFieldTypeCount() - 1).name()));
      }
      auditDataItemModel.setValue(auditDataItem.getValue());
      auditDataItems.add(auditDataItemModel);
    }
    if (auditDataItems.size() > 0) {
      auditDataRequest.setDataItemList(auditDataItems);
    }
    return auditDataRequest;
  }
}
