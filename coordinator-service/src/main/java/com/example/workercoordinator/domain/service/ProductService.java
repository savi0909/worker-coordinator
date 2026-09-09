package com.example.workercoordinator.domain.service;

import com.example.workercoordinator.domain.model.ProductDefinition;

public interface ProductService {
    ProductDefinition registerProduct(String productId, String productName);

    ProductDefinition getProduct(String productId);
}
