package com.example.workercoordinator.infrastructure.persistence;

import com.example.workercoordinator.domain.exception.CoordinatorException;
import com.example.workercoordinator.domain.model.Status;
import com.example.workercoordinator.domain.model.WorkerLease;
import com.example.workercoordinator.domain.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the worker read SQL against PostgreSQL, including effective-status derivation and paging.
 * With two worker-ID bits each region seeds four slots.
 */
@SpringBootTest(properties = "coordinator.worker-id-bits=2")
@Testcontainers(disabledWithoutDocker = true)
class PostgresWorkerQueryServiceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    ProductService products;
    @Autowired
    ServiceRegistrationService services;
    @Autowired
    WorkerTypeService workerTypes;
    @Autowired
    WorkerCoordinator coordinator;
    @Autowired
    WorkerQueryService queries;
    @Autowired
    JdbcTemplate jdbc;

    private String productId;
    private String serviceId;
    private String typeId;

    @BeforeEach
    void registerHierarchy() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        productId = "product-" + suffix;
        serviceId = "service-" + suffix;
        typeId = "type-" + suffix;
        products.registerProduct(productId, "Product");
        services.registerService(productId, serviceId, "Service");
        workerTypes.registerWorkerType(serviceId, typeId, "Worker Type");
    }

    @Test
    void registeredTypeWithoutAcquisitionsHasNoSlots() {
        var page = queries.listWorkers(typeId, null, null, 100, 0);
        assertTrue(page.workers().isEmpty());
        assertFalse(page.hasMore());
    }

    @Test
    void listsSeededSlotsWithOwnershipAndFilters() {
        WorkerLease lease = acquire(1);
        assertEquals(4, queries.listWorkers(typeId, 1, null, 100, 0).workers().size());
        var leased = queries.listWorkers(typeId, 1, Status.LEASED, 100, 0).workers();
        assertEquals(1, leased.size());
        assertEquals(productId, leased.getFirst().productId());
        assertEquals(serviceId, leased.getFirst().serviceId());
        assertEquals(lease.workerId(), leased.getFirst().workerId());
        assertEquals(lease.epoch(), leased.getFirst().epoch());
        assertEquals(lease.instanceId(), leased.getFirst().ownerInstanceId());
        assertEquals(3, queries.listWorkers(typeId, 1, Status.AVAILABLE, 100, 0).workers().size());
        assertTrue(queries.listWorkers(typeId, 2, null, 100, 0).workers().isEmpty());
    }

    @Test
    void expiredLeaseIsReportedAsExpired() {
        WorkerLease lease = acquire(1);
        jdbc.update("UPDATE workers SET lease_expiry = now() - interval '1 second' WHERE worker_type_id=? AND region_id=1 AND worker_id=?",
                typeId, lease.workerId());
        var slot = queries.getWorker(typeId, 1, lease.workerId());
        assertEquals(Status.EXPIRED, slot.status());
        assertEquals(lease.instanceId(), slot.ownerInstanceId());
        assertEquals(1, queries.listWorkers(typeId, 1, Status.EXPIRED, 100, 0).workers().size());
        assertTrue(queries.listWorkers(typeId, 1, Status.LEASED, 100, 0).workers().isEmpty());
    }

    @Test
    void pagesAcrossRegionsInStableOrder() {
        acquire(1);
        acquire(2);
        var first = queries.listWorkers(typeId, null, null, 5, 0);
        assertEquals(5, first.workers().size());
        assertTrue(first.hasMore());
        var second = queries.listWorkers(typeId, null, null, 5, 5);
        assertEquals(3, second.workers().size());
        assertFalse(second.hasMore());
        assertEquals(2, second.workers().getFirst().regionId());
        assertEquals(1, second.workers().getFirst().workerId());
    }

    @Test
    void missingSlotAndUnknownTypeAreNotFound() {
        acquire(1);
        assertEquals("WORKER_NOT_FOUND", assertThrows(CoordinatorException.class, () -> queries.getWorker(typeId, 1, 99)).code());
        assertEquals("WORKER_TYPE_NOT_FOUND", assertThrows(CoordinatorException.class, () -> queries.listWorkers("missing", null, null, 10, 0)).code());
    }

    @Test
    void nonSlotStatusFilterIsRejected() {
        assertEquals("INVALID_STATUS_FILTER", assertThrows(CoordinatorException.class, () -> queries.listWorkers(typeId, null, Status.ACTIVE, 10, 0)).code());
    }

    @Test
    void hierarchyListsFollowProductServiceWorkerType() {
        assertTrue(products.listProducts().stream().anyMatch(p -> p.productId().equals(productId)));
        assertEquals(serviceId, services.listServices(productId).getFirst().serviceId());
        assertEquals(typeId, workerTypes.listWorkerTypes(serviceId).getFirst().workerTypeId());
    }

    private WorkerLease acquire(int regionId) {
        return coordinator.acquire(new WorkerCoordinator.AcquireWorkerCommand(productId, serviceId, typeId, regionId, UUID.randomUUID(), UUID.randomUUID()));
    }
}
