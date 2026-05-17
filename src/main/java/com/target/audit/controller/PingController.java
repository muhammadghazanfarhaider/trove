package com.target.audit.controller;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fuseim.common.util.ServiceResponse;

/** @author Muhammad Salman */
@RestController
@RequestMapping("/audit")
public class PingController {

  private static final Logger logger = LoggerFactory.getLogger(PingController.class);

  @Value("${info.app.name}")
  private String appLabel;
  
  @Value("${service.commit.no}")
  private String serviceCommitNo;

  /**
   * This method is used only for testing if the server/services is up and running
   * @param
   * @return ping status
   * @throws IOException
   */
  @GetMapping(value = "/ping")
  public ServiceResponse ping() throws IOException {

    ServiceResponse response = new ServiceResponse();

    JSONArray resp = new JSONArray();
    JSONObject data = new JSONObject();
    JSONObject subData = new JSONObject();

    subData.put("ping", "pong");
    subData.put("commitNo", serviceCommitNo);
    subData.put("label", appLabel);
    data.put(appLabel, subData);

    resp.put(subData);
    response.setData(resp.toList());
    response.setMessage("Success");
    response.setSuccess(true);
    logger.debug(appLabel + " ping info : " + subData);
    return response;
  }
}
