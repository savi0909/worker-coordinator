package com.example.workercoordinator.infrastructure.persistence;

import com.example.workercoordinator.domain.exception.CoordinatorException;
import com.example.workercoordinator.repository.entity.ProductEntity;
import com.example.workercoordinator.repository.springdata.ProductRepository;
import com.example.workercoordinator.repository.springdata.ServiceRepository;
import com.example.workercoordinator.repository.springdata.WorkerTypeRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RegistrationServicesTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final ServiceRepository services = mock(ServiceRepository.class);
    private final WorkerTypeRepository types = mock(WorkerTypeRepository.class);
    private final RegistrationServices subject = new RegistrationServices(products, services, types);

    @Test
    void registersProductWhenIdIsNew() {
        when(products.findById("orders")).thenReturn(Optional.empty());
        when(products.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = subject.registerProduct("orders", "Orders");
        assertEquals("orders", result.productId());
        verify(products).save(any(ProductEntity.class));
    }

    @Test
    void sameProductRegistrationIsIdempotent() {
        when(products.findById("orders")).thenReturn(Optional.of(new ProductEntity("orders", "Orders")));
        var result = subject.registerProduct("orders", "Orders");
        assertEquals("Orders", result.productName());
        verify(products, never()).save(any());
    }

    @Test
    void unknownParentIsRejected() {
        when(products.findById("missing")).thenReturn(Optional.empty());
        var exception = assertThrows(CoordinatorException.class, () -> subject.registerService("missing", "processing", "Order Processing"));
        assertEquals("PRODUCT_NOT_FOUND", exception.code());
    }

    @Test
    void workerTypeRequiresService() {
        when(services.findById("missing")).thenReturn(Optional.empty());
        var exception = assertThrows(CoordinatorException.class, () -> subject.registerWorkerType("missing", "generator", "Order ID Generator"));
        assertEquals("SERVICE_NOT_FOUND", exception.code());
    }
}
