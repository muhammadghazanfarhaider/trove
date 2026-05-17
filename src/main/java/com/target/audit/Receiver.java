package com.target.audit;

import com.google.gson.Gson;
import com.target.audit.service.AuditTrailService;
import com.target.audittrail.model.AuditData;
import java.io.UnsupportedEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** @author rakesh */
@Component
public class Receiver {

  private static final Logger logger = LoggerFactory.getLogger(Receiver.class);

  @Autowired private AuditTrailService auditTrailService;

  public void receiveMessage(AuditData auditDataRequest) {
    try {
      logger.info("Payload audit :" + auditDataRequest != null ? auditDataRequest.toString() : "");
      auditTrailService.handleAuditData(auditDataRequest);
    } catch (Exception ex) {
      logger.error("Failed to save audit data " + ex.getMessage());
    }
  }

  public void receiveMessage(Object message) {

    byte[] bytes = (byte[]) message;
    String payLoad = null;
    try {
      payLoad = new String(bytes, "UTF-8");
    } catch (UnsupportedEncodingException ex) {
      ex.printStackTrace();
    }
    try {
      AuditData auditDataRequest = new Gson().fromJson(payLoad, AuditData.class);
      logger.info("Payload audit :" + payLoad);
      auditTrailService.handleAuditData(auditDataRequest);

    } catch (Exception ex) {
      logger.error("Failed to save audit data " + ex.getMessage());
    }
  }
}
