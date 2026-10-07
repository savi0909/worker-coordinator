package com.example.workercoordinator.domain.service;

import com.example.workercoordinator.domain.model.WorkerTypeDefinition;

import java.util.List;

public interface WorkerTypeService {
    WorkerTypeDefinition registerWorkerType(String serviceId, String workerTypeId, String workerTypeName);

    WorkerTypeDefinition getWorkerType(String workerTypeId);

    List<WorkerTypeDefinition> listWorkerTypes(String serviceId);
}
