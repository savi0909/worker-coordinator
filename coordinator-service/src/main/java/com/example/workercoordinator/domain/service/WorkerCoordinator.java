package com.example.workercoordinator.domain.service;

import com.example.workercoordinator.domain.model.ReleaseResult;
import com.example.workercoordinator.domain.model.WorkerLease;

import java.util.UUID;

public interface WorkerCoordinator {
    WorkerLease acquire(AcquireWorkerCommand request);

    WorkerLease renew(RenewLeaseCommand request);

    ReleaseResult release(ReleaseWorkerCommand request);

    record AcquireWorkerCommand(String productId, String serviceId, String workerTypeId, int regionId, UUID instanceId,
                                UUID registrationId) {
    }

    record RenewLeaseCommand(String productId, String serviceId, String workerTypeId, int regionId, int workerId,
                             long epoch, UUID instanceId, UUID registrationId) {
    }

    record ReleaseWorkerCommand(String productId, String serviceId, String workerTypeId, int regionId, int workerId,
                                long epoch, UUID instanceId, UUID registrationId) {
    }
}
