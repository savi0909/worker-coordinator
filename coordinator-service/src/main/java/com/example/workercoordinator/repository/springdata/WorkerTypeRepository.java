package com.example.workercoordinator.repository.springdata;

import com.example.workercoordinator.repository.entity.WorkerTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerTypeRepository extends JpaRepository<WorkerTypeEntity, String> {
}
