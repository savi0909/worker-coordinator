package com.example.workercoordinator.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record WorkerLease(String productId, String serviceId, String workerTypeId, int regionId, int workerId,
                          long epoch, UUID instanceId, Instant leaseExpiry, Duration leaseDuration) {
}
