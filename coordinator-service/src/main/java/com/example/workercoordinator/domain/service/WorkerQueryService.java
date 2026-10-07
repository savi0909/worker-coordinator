package com.example.workercoordinator.domain.service;

import com.example.workercoordinator.domain.model.Status;
import com.example.workercoordinator.domain.model.WorkerSlot;
import com.example.workercoordinator.domain.model.WorkerSlotPage;

/**
 * Read-only view of worker slots. Slots are seeded on the first acquisition in a namespace, so a registered worker
 * type with no acquisitions has no slots.
 */
public interface WorkerQueryService {
    WorkerSlotPage listWorkers(String workerTypeId, Integer regionId, Status status, int limit, int offset);

    WorkerSlot getWorker(String workerTypeId, int regionId, int workerId);
}
