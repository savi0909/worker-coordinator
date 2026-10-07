package com.example.workercoordinator.infrastructure.persistence;

import com.example.workercoordinator.domain.exception.CoordinatorException;
import com.example.workercoordinator.domain.model.ProductDefinition;
import com.example.workercoordinator.domain.model.ServiceDefinition;
import com.example.workercoordinator.domain.model.WorkerTypeDefinition;
import com.example.workercoordinator.domain.service.ProductService;
import com.example.workercoordinator.domain.service.ServiceRegistrationService;
import com.example.workercoordinator.domain.service.WorkerTypeService;
import com.example.workercoordinator.repository.entity.ProductEntity;
import com.example.workercoordinator.repository.entity.ServiceEntity;
import com.example.workercoordinator.repository.entity.WorkerTypeEntity;
import com.example.workercoordinator.repository.springdata.ProductRepository;
import com.example.workercoordinator.repository.springdata.ServiceRepository;
import com.example.workercoordinator.repository.springdata.WorkerTypeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RegistrationServices implements ProductService, ServiceRegistrationService, WorkerTypeService {
    private final ProductRepository products;
    private final ServiceRepository services;
    private final WorkerTypeRepository workerTypes;

    public RegistrationServices(ProductRepository products, ServiceRepository services, WorkerTypeRepository workerTypes) {
        this.products = products;
        this.services = services;
        this.workerTypes = workerTypes;
    }

    @Override
    @Transactional
    public ProductDefinition registerProduct(String id, String name) {
        return products.findById(id).map(p -> {
            if (!same(p.name, name, "PRODUCT_ALREADY_EXISTS"))
                throw conflict("Product ID is already registered with different metadata");
            return product(p);
        }).orElseGet(() -> product(products.save(new ProductEntity(id, name))));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDefinition getProduct(String id) {
        return products.findById(id).map(this::product).orElseThrow(() -> notFound("PRODUCT_NOT_FOUND", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDefinition> listProducts() {
        return products.findAll(Sort.by("id")).stream().map(this::product).toList();
    }

    @Override
    @Transactional
    public ServiceDefinition registerService(String productId, String id, String name) {
        getProduct(productId);
        return services.findById(id).map(s -> {
            if (!s.productId.equals(productId) || !same(s.name, name, "SERVICE_ALREADY_EXISTS"))
                throw new CoordinatorException("SERVICE_ALREADY_EXISTS", "Service ID has conflicting registration");
            return service(s);
        }).orElseGet(() -> service(services.save(new ServiceEntity(id, productId, name))));
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceDefinition getService(String id) {
        return services.findById(id).map(this::service).orElseThrow(() -> notFound("SERVICE_NOT_FOUND", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceDefinition> listServices(String productId) {
        getProduct(productId);
        return services.findByProductIdOrderByIdAsc(productId).stream().map(this::service).toList();
    }

    @Override
    @Transactional
    public WorkerTypeDefinition registerWorkerType(String serviceId, String id, String name) {
        getService(serviceId);
        return workerTypes.findById(id).map(w -> {
            if (!w.serviceId.equals(serviceId) || !same(w.name, name, "WORKER_TYPE_ALREADY_EXISTS"))
                throw new CoordinatorException("WORKER_TYPE_ALREADY_EXISTS", "Worker type ID has conflicting registration");
            return workerType(w);
        }).orElseGet(() -> workerType(workerTypes.save(new WorkerTypeEntity(id, serviceId, name))));
    }

    @Override
    @Transactional(readOnly = true)
    public WorkerTypeDefinition getWorkerType(String id) {
        return workerTypes.findById(id).map(this::workerType).orElseThrow(() -> notFound("WORKER_TYPE_NOT_FOUND", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkerTypeDefinition> listWorkerTypes(String serviceId) {
        getService(serviceId);
        return workerTypes.findByServiceIdOrderByIdAsc(serviceId).stream().map(this::workerType).toList();
    }

    private boolean same(String a, String b, String code) {
        return a.equals(b);
    }

    private CoordinatorException conflict(String message) {
        return new CoordinatorException("IDEMPOTENCY_CONFLICT", message);
    }

    private CoordinatorException notFound(String code, String id) {
        return new CoordinatorException(code, "No resource registered with ID: " + id);
    }

    private ProductDefinition product(ProductEntity e) {
        return new ProductDefinition(e.id, e.name, e.status);
    }

    private ServiceDefinition service(ServiceEntity e) {
        return new ServiceDefinition(e.id, e.productId, e.name, e.status);
    }

    private WorkerTypeDefinition workerType(WorkerTypeEntity e) {
        return new WorkerTypeDefinition(e.id, e.serviceId, e.name, e.status);
    }
}
