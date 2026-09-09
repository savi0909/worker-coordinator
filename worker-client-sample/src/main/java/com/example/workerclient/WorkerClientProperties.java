package com.example.workerclient;
import org.springframework.boot.context.properties.ConfigurationProperties; import java.time.Duration; import java.util.UUID;
@ConfigurationProperties("worker-client")
public record WorkerClientProperties(String coordinatorUrl,String productId,String productName,String serviceId,String serviceName,
 String workerTypeId,String workerTypeName,int regionId,UUID instanceId,Duration renewalInterval,
 int timestampBits,int regionBits,int workerBits,int sequenceBits,long epochMillis) {}
