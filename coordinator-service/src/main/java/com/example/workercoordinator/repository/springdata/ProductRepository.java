package com.example.workercoordinator.repository.springdata;

import com.example.workercoordinator.repository.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, String> {
}
