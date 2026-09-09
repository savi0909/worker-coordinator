package com.example.workercoordinator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("coordinator")
public record CoordinatorProperties(Duration leaseDuration, Duration leaseRenewalInterval, int regionId,
                                    int workerIdBits) {
    public CoordinatorProperties {
        if (leaseDuration == null) leaseDuration = Duration.ofSeconds(30);
        if (leaseRenewalInterval == null) leaseRenewalInterval = Duration.ofSeconds(10);
    }
}
