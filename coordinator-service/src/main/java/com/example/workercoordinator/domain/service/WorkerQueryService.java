package com.example.workercoordinator.domain.service;

import com.example.workercoordinator.domain.model.WorkerLease;

import java.util.Optional;

public interface WorkerQueryService {
    Optional<WorkerLease> currentOwnership(String productId, String serviceId, String workerTypeId, int regionId, int workerId);
}
