package com.target;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ProtocolException;
import java.util.Calendar;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import com.target.audit.AuditTrailServiceApplication;
import com.target.audit.grpc.client.GrpcAuditServiceClient;
import com.target.search.model.CatalogSearchResponse;

@SpringBootTest(
    classes = {AuditTrailServiceApplication.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "audit.stale.download.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
    }
)
@TestPropertySource(properties = {
    "audit-trail.driver-name=InternalText",
    "audit-trail.url=http://localhost:9300/"
})
public class SpringGrpcAuditServiceTests {

    private static final Logger logger = LoggerFactory.getLogger(SpringGrpcAuditServiceTests.class);

    @Autowired
    private GrpcAuditServiceClient grpcAuditServiceClient;

    @Autowired
    Environment environment;

    @Test
    @org.junit.jupiter.api.Disabled("Disabled during Spring Boot 3 upgrade - needs gRPC server running")
    public void testAudit() throws IOException, ProtocolException, InterruptedException {

        long timeInMilliSecond = Calendar.getInstance().getTimeInMillis();
        grpcAuditServiceClient.audit(timeInMilliSecond);

        RestTemplate restTemplate = new RestTemplate();
        Thread.sleep(1000);
        String fooResourceUrl
                = "http://localhost:"+
                environment.getProperty("local.server.port")+
                "/audit/api/v1/read/entity/?entity=testEntity"+timeInMilliSecond
                +"&username=null";
        ResponseEntity<CatalogSearchResponse> response
                = restTemplate.getForEntity(fooResourceUrl, CatalogSearchResponse.class);

        logger.info(String.valueOf(response.getStatusCodeValue()));
        logger.info(String.valueOf(response.getBody().getCount()));
        logger.info(response.getBody().getHits().get(0).getValue("entity").toString());
        assertThat(response.getStatusCodeValue())
                .isEqualTo(200);
        assertThat(response.getBody().getCount())
                .isGreaterThan(0);
        assertThat(response.getBody().getHits().get(0).getValue("entity"))
                .isEqualTo("testEntity"+timeInMilliSecond);
    }
}
