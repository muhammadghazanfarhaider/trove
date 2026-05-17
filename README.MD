# audit-trail-service 
 
This service provides interface to keep audit trail for all the crud operations being performed in a given service. 
   
This service provides two interfaces for the client application to connect and use the given service. 
- **RabbitMQ**
- **gRPC**
 
In case of RabbitMQ following details are used exchange name **audit-trail-service** and queue name **audit-trail-queue**.

In case of gRPC by default 6565 port is exposed on which client can connect and send request to create audit trail. 
In oder to change the default gRPC port update **application.properties** to add following property
```$xslt
grpc.port=7001
```

For a client to be able to enable audit trail service via gRPC, following steps are needed. 

- Add following dependency in project pom.xml
```$xslt
		<dependency>
			<groupId>com.target</groupId>
			<artifactId>audit-common</artifactId>
			<version>1.0-SNAPSHOT</version>
		</dependency>
```
- To resolve this dependency locally do ```mvn install``` in **auditcommong** directory. 
- Add a client class for example GrpcClient in target directory for example **src/main/java/com/target/audit/grpc/client**.
- In client file add channel and create connection on **6565** port (or any other port if the service gRPC port is changed via porperties)
- Compose your sample audit data and call **audit** method of service to create audit trail record. 
```$xslt
import com.target.audit.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
```
- inside **main** add following code to create channel and audit service stub
```
ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565).usePlaintext().build();
AuditServiceGrpc.AuditServiceBlockingStub stub = AuditServiceGrpc.newBlockingStub(channel);
```
- Add following code to create data for audit request
```$xslt
            AuditRequest auditRequest = AuditRequest.newBuilder()
                .setUsername("testUserName2")
                .setSource("testSource2")
                .addDataEvent(AuditRequest.DataEvent.created)
                .setEntity("testEntity2")
                .setDatakey("testKey2")
                .setChanged("2019"-01-01)
                .addAuditDataItems(AuditDataItem.newBuilder()
                        .setFieldName("testField")
                        .addFieldType(FieldType.valueOf("STRING"))
                        .setValue("testFieldValue2")
                        .build()
```
- call audit method from stub to make call to audit service 
```
AuditResponse auditResponse = stub.audit(AuditRequest.newBuilder()
                   .setUsername("testUserName2")
                   .setSource("testSource2")
                   .addDataEvent(AuditRequest.DataEvent.created)
                   .setEntity("testEntity2")
                   .setDatakey("testKey2")
                   .setChanged("01/01/2019")
                   .addAuditDataItems(AuditDataItem.newBuilder()
                           .setFieldName("testField2")
                           .addFieldType(FieldType.valueOf("STRING"))
                           .setValue("testFieldValue2")
                           .build())
                   .build());
```
- Response object contains status of call and can be used to check status.
- In order to check if the data is audited. Make Get call on following endpoint with entity used in above exmaple
```
http://localhost:8091/audit/api/v1/read/entity/?entity=testEntity2&username=null
``` 
- Sample client application can be seen here
> src/main/java/com/target/audit/grpc/client/GrpcAuditServiceClient.java
- Following test makes gRPC call for audit record and then checks rest endpoint to make sure the inserted data is present. 
> src/test/java/com/target/SpringGrpcAuditServiceTests.java
