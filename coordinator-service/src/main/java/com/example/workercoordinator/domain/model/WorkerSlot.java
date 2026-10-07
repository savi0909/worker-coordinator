package com.example.workercoordinator.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model of one worker slot. {@code status} is effective: a LEASED row whose expiry has passed is EXPIRED, because
 * no scheduler rewrites expired rows. {@code ownerInstanceId} and {@code leaseExpiry} are null for AVAILABLE slots.
 */
public record WorkerSlot(String productId, String serviceId, String workerTypeId, int regionId, int workerId,
                         long epoch, UUID ownerInstanceId, Status status, Instant leaseExpiry) {
}
