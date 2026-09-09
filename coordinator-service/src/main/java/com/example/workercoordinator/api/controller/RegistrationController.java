package com.example.workercoordinator.api.controller;

import com.example.workercoordinator.api.dto.RegisterProductRequest;
import com.example.workercoordinator.api.dto.RegisterServiceRequest;
import com.example.workercoordinator.api.dto.RegisterWorkerTypeRequest;
import com.example.workercoordinator.domain.model.ProductDefinition;
import com.example.workercoordinator.domain.model.ServiceDefinition;
import com.example.workercoordinator.domain.model.WorkerTypeDefinition;
import com.example.workercoordinator.domain.service.ProductService;
import com.example.workercoordinator.domain.service.ServiceRegistrationService;
import com.example.workercoordinator.domain.service.WorkerTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RegistrationController {
    private final ProductService products;
    private final ServiceRegistrationService services;
    private final WorkerTypeService workerTypes;

    public RegistrationController(ProductService products, ServiceRegistrationService services, WorkerTypeService workerTypes) {
        this.products = products;
        this.services = services;
        this.workerTypes = workerTypes;
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    ProductDefinition product(@Valid @RequestBody RegisterProductRequest r) {
        return products.registerProduct(r.productId(), r.productName());
    }

    @GetMapping("/products/{productId}")
    ProductDefinition product(@PathVariable String productId) {
        return products.getProduct(productId);
    }

    @PostMapping("/products/{productId}/services")
    @ResponseStatus(HttpStatus.CREATED)
    ServiceDefinition service(@PathVariable String productId, @Valid @RequestBody RegisterServiceRequest r) {
        return services.registerService(productId, r.serviceId(), r.serviceName());
    }

    @GetMapping("/services/{serviceId}")
    ServiceDefinition service(@PathVariable String serviceId) {
        return services.getService(serviceId);
    }

    @PostMapping("/services/{serviceId}/worker-types")
    @ResponseStatus(HttpStatus.CREATED)
    WorkerTypeDefinition workerType(@PathVariable String serviceId, @Valid @RequestBody RegisterWorkerTypeRequest r) {
        return workerTypes.registerWorkerType(serviceId, r.workerTypeId(), r.workerTypeName());
    }

    @GetMapping("/worker-types/{workerTypeId}")
    WorkerTypeDefinition workerType(@PathVariable String workerTypeId) {
        return workerTypes.getWorkerType(workerTypeId);
    }
}
