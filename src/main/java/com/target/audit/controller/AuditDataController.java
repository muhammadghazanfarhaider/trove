package com.target.audit.controller;

import java.util.Collections;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fuseim.common.util.ServiceResponse;
import com.target.audit.service.AuditTrailService;
import com.target.audittrail.model.AuditData;


@RestController
@RequestMapping("/audit/api/v1/data")
@Component
public class AuditDataController {

  private static final Logger logger = LoggerFactory.getLogger(AuditDataController.class);
  @Autowired private AuditTrailService auditTrailService;

  @PostMapping(path = "/", consumes = "application/json", produces = "application/json")
  public ServiceResponse getAuditData(@RequestBody Map<String, Object> dataRequest) {
    ServiceResponse serviceResponse = new ServiceResponse();

    try {
      serviceResponse.setData(auditTrailService.getAuditData(dataRequest));
      serviceResponse.setSuccess(true);

    } catch (Exception ex) {
      serviceResponse.setMessage(ex.getMessage());
      logger.error("Failed to get audit data " + ex.getMessage());
      ex.printStackTrace();
    }
    return serviceResponse;
  }

  @PostMapping(path = "/search", consumes = "application/json", produces = "application/json")
  public ServiceResponse searchAuditData(@RequestBody Map<String, Object> dataRequest) {
    ServiceResponse serviceResponse = new ServiceResponse();

    try {
      serviceResponse.setData(auditTrailService.searchAuditData(dataRequest));
      serviceResponse.setSuccess(true);

    } catch (Exception ex) {
      serviceResponse.setMessage(ex.getMessage());
      logger.error("Failed to get audit data " + ex.getMessage());
      ex.printStackTrace();
    }
    return serviceResponse;
  }

  @PostMapping("/save")
  public ServiceResponse save(@RequestBody AuditData auditDataRequest, HttpServletRequest request) {
    ServiceResponse serviceResponse = new ServiceResponse();
    try {
      String clientIp = getClientIpAddress(request);
      serviceResponse.setData(
          Collections.singletonMap("id", auditTrailService.handleAuditData(auditDataRequest, clientIp)));
      serviceResponse.setSuccess(true);
      serviceResponse.setMessage("Data saved");
    } catch (Exception ex) {
      serviceResponse.setMessage(ex.getMessage());
      serviceResponse.setSuccess(false);
      logger.error("Failed to save audit data " + ex.getMessage());
      ex.printStackTrace();
    }
    return serviceResponse;
  }

  /**
   * Extract client IP address from HTTP request.
   * Handles cases where request comes through proxy/load balancer.
   */
  private String getClientIpAddress(HttpServletRequest request) {
    String[] headerNames = {
        "X-Forwarded-For",
        "X-Real-IP",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_X_FORWARDED_FOR",
        "HTTP_X_FORWARDED",
        "HTTP_X_CLUSTER_CLIENT_IP",
        "HTTP_CLIENT_IP",
        "HTTP_FORWARDED_FOR",
        "HTTP_FORWARDED",
        "HTTP_VIA",
        "REMOTE_ADDR"
    };

    for (String header : headerNames) {
      String ip = request.getHeader(header);
      if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
        // X-Forwarded-For can contain multiple IPs, take the first one
        if (ip.contains(",")) {
          ip = ip.split(",")[0].trim();
        }
        return ip;
      }
    }

    // Fallback to remote address
    return request.getRemoteAddr();
  }

  /**
   * Update audit data record.
   * For USER_SESSION with logoutTime, automatically calculates session duration.
   *
   * POST /audit/api/v1/data/update
   * Body: {
   *   "audit_source": "USER_SESSION",
   *   "_id": "abc123",
   *   "logoutTime": "2026-02-19T11:30:00.000Z"
   * }
   */
  @PostMapping("/update")
  public ServiceResponse update(@RequestBody AuditData auditDataRequest) {
    ServiceResponse serviceResponse = new ServiceResponse();
    try {
      serviceResponse.setData(
          Collections.singletonMap("id", auditTrailService.updateAuditData(auditDataRequest)));
      serviceResponse.setSuccess(true);
      serviceResponse.setMessage("Data updated");
    } catch (Exception ex) {
      serviceResponse.setMessage(ex.getMessage());
      serviceResponse.setSuccess(false);
      logger.error("Failed to update audit data " + ex.getMessage());
      ex.printStackTrace();
    }
    return serviceResponse;
  }
}
