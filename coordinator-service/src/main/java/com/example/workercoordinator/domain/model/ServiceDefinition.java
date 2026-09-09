package com.example.workercoordinator.domain.model;

public record ServiceDefinition(String serviceId, String productId, String serviceName, Status status) {
}
