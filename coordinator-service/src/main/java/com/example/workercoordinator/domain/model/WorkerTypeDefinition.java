package com.example.workercoordinator.domain.model;

public record WorkerTypeDefinition(String workerTypeId, String serviceId, String workerTypeName, Status status) {
}
