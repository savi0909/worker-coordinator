package com.example.workeridclient;

import java.time.Instant;

/** The minimum coordinator lease data required by the local generator. */
public record LeaseSnapshot(int regionId, int workerId, long epoch, Instant leaseExpiry) {
    public LeaseSnapshot {
        if (leaseExpiry == null) {
            throw new IllegalArgumentException("leaseExpiry is required");
        }
    }
}
