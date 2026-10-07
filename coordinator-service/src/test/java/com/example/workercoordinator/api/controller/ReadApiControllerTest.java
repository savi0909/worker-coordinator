package com.example.workercoordinator.api.controller;

import com.example.workercoordinator.domain.exception.CoordinatorException;
import com.example.workercoordinator.domain.model.*;
import com.example.workercoordinator.domain.service.ProductService;
import com.example.workercoordinator.domain.service.ServiceRegistrationService;
import com.example.workercoordinator.domain.service.WorkerQueryService;
import com.example.workercoordinator.domain.service.WorkerTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {RegistrationController.class, WorkerQueryController.class})
class ReadApiControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    ProductService products;
    @MockitoBean
    ServiceRegistrationService services;
    @MockitoBean
    WorkerTypeService workerTypes;
    @MockitoBean
    WorkerQueryService workers;

    @Test
    void listsProducts() throws Exception {
        when(products.listProducts()).thenReturn(List.of(new ProductDefinition("orders", "Orders", Status.ACTIVE)));
        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value("orders"));
    }

    @Test
    void listsServicesOfProduct() throws Exception {
        when(services.listServices("orders")).thenReturn(List.of(new ServiceDefinition("order-processing", "orders", "Order Processing", Status.ACTIVE)));
        mvc.perform(get("/api/v1/products/orders/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceId").value("order-processing"))
                .andExpect(jsonPath("$[0].productId").value("orders"));
    }

    @Test
    void unknownParentIsNotFound() throws Exception {
        when(services.listServices("missing")).thenThrow(new CoordinatorException("PRODUCT_NOT_FOUND", "No resource registered with ID: missing"));
        mvc.perform(get("/api/v1/products/missing/services"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void listsWorkerTypesOfService() throws Exception {
        when(workerTypes.listWorkerTypes("order-processing")).thenReturn(List.of(new WorkerTypeDefinition("order-id-generator", "order-processing", "Order ID Generator", Status.ACTIVE)));
        mvc.perform(get("/api/v1/services/order-processing/worker-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workerTypeId").value("order-id-generator"));
    }

    @Test
    void listsWorkersWithDefaultsAndFilters() throws Exception {
        var slot = new WorkerSlot("orders", "order-processing", "order-id-generator", 1, 0, 3, UUID.randomUUID(), Status.LEASED, Instant.now());
        when(workers.listWorkers("order-id-generator", 1, Status.LEASED, 100, 0)).thenReturn(new WorkerSlotPage(List.of(slot), 100, 0, false));
        mvc.perform(get("/api/v1/worker-types/order-id-generator/workers").param("regionId", "1").param("status", "LEASED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workers[0].epoch").value(3))
                .andExpect(jsonPath("$.workers[0].status").value("LEASED"))
                .andExpect(jsonPath("$.limit").value(100))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void getsSingleWorker() throws Exception {
        var slot = new WorkerSlot("orders", "order-processing", "order-id-generator", 1, 7, 0, null, Status.AVAILABLE, null);
        when(workers.getWorker("order-id-generator", 1, 7)).thenReturn(slot);
        mvc.perform(get("/api/v1/worker-types/order-id-generator/regions/1/workers/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workerId").value(7))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void unseededWorkerIsNotFound() throws Exception {
        when(workers.getWorker("order-id-generator", 1, 7)).thenThrow(new CoordinatorException("WORKER_NOT_FOUND", "No worker slot"));
        mvc.perform(get("/api/v1/worker-types/order-id-generator/regions/1/workers/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKER_NOT_FOUND"));
    }

    @Test
    void outOfRangeParametersAreRejected() throws Exception {
        mvc.perform(get("/api/v1/worker-types/order-id-generator/workers").param("regionId", "16").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors.regionId").exists())
                .andExpect(jsonPath("$.errors.limit").exists());
        mvc.perform(get("/api/v1/worker-types/order-id-generator/regions/99/workers/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.regionId").exists());
        verify(workers, never()).listWorkers(any(), any(), any(), anyInt(), anyInt());
        verify(workers, never()).getWorker(any(), anyInt(), anyInt());
    }

    @Test
    void unknownStatusValueIsRejected() throws Exception {
        mvc.perform(get("/api/v1/worker-types/order-id-generator/workers").param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    void nonSlotStatusFilterIsBadRequest() throws Exception {
        when(workers.listWorkers(any(), any(), any(), anyInt(), anyInt())).thenThrow(new CoordinatorException("INVALID_STATUS_FILTER", "bad filter"));
        mvc.perform(get("/api/v1/worker-types/order-id-generator/workers").param("status", "ACTIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_FILTER"));
    }
}
