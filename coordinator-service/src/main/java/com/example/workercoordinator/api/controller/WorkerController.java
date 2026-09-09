package com.example.workercoordinator.api.controller;

import com.example.workercoordinator.api.dto.AcquireWorkerRequest;
import com.example.workercoordinator.api.dto.ReleaseWorkerRequest;
import com.example.workercoordinator.api.dto.RenewLeaseRequest;
import com.example.workercoordinator.domain.model.ReleaseResult;
import com.example.workercoordinator.domain.model.WorkerLease;
import com.example.workercoordinator.domain.service.WorkerCoordinator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Worker ownership mutations are delegated to the transactional PostgreSQL coordinator.
 */
@RestController
@RequestMapping("/api/v1/workers")
public class WorkerController {
    private final WorkerCoordinator coordinator;

    public WorkerController(WorkerCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @PostMapping("/acquire")
    ResponseEntity<WorkerLease> acquire(@Valid @RequestBody AcquireWorkerRequest request) {
        WorkerLease lease = coordinator.acquire(new WorkerCoordinator.AcquireWorkerCommand(request.productId(), request.serviceId(), request.workerTypeId(), request.regionId(), request.instanceId(), request.registrationId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(lease);
    }

    @PostMapping("/renew")
    ResponseEntity<WorkerLease> renew(@Valid @RequestBody RenewLeaseRequest request) {
        return ResponseEntity.ok(coordinator.renew(new WorkerCoordinator.RenewLeaseCommand(request.productId(), request.serviceId(), request.workerTypeId(), request.regionId(), request.workerId(), request.epoch(), request.instanceId(), request.registrationId())));
    }

    @PostMapping("/release")
    ResponseEntity<ReleaseResult> release(@Valid @RequestBody ReleaseWorkerRequest request) {
        return ResponseEntity.ok(coordinator.release(new WorkerCoordinator.ReleaseWorkerCommand(request.productId(), request.serviceId(), request.workerTypeId(), request.regionId(), request.workerId(), request.epoch(), request.instanceId(), request.registrationId())));
    }
}
