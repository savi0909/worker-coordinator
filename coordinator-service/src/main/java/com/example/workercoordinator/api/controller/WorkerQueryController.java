package com.example.workercoordinator.api.controller;

import com.example.workercoordinator.domain.model.Status;
import com.example.workercoordinator.domain.model.WorkerSlot;
import com.example.workercoordinator.domain.model.WorkerSlotPage;
import com.example.workercoordinator.domain.service.WorkerQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only worker slot views, scoped by worker type because its ID identifies the owning service and product.
 */
@RestController
@RequestMapping("/api/v1/worker-types/{workerTypeId}")
public class WorkerQueryController {
    private final WorkerQueryService workers;

    public WorkerQueryController(WorkerQueryService workers) {
        this.workers = workers;
    }

    @GetMapping("/workers")
    WorkerSlotPage workers(@PathVariable String workerTypeId,
                           @RequestParam(required = false) @Min(0) @Max(15) Integer regionId,
                           @RequestParam(required = false) Status status,
                           @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit,
                           @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return workers.listWorkers(workerTypeId, regionId, status, limit, offset);
    }

    @GetMapping("/regions/{regionId}/workers/{workerId}")
    WorkerSlot worker(@PathVariable String workerTypeId,
                      @PathVariable @Min(0) @Max(15) int regionId,
                      @PathVariable @Min(0) int workerId) {
        return workers.getWorker(workerTypeId, regionId, workerId);
    }
}
