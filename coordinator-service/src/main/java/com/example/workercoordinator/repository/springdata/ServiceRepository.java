package com.example.workercoordinator.repository.springdata;

import com.example.workercoordinator.repository.entity.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceRepository extends JpaRepository<ServiceEntity, String> {
    List<ServiceEntity> findByProductIdOrderByIdAsc(String productId);
}
