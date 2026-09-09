package com.example.workercoordinator.domain.service;

import com.example.workercoordinator.domain.model.ServiceDefinition;

public interface ServiceRegistrationService {
    ServiceDefinition registerService(String productId, String serviceId, String serviceName);

    ServiceDefinition getService(String serviceId);
}
