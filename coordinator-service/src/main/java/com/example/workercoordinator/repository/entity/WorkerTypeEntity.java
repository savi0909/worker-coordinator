package com.example.workercoordinator.repository.entity;

import com.example.workercoordinator.domain.model.Status;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "worker_types")
public class WorkerTypeEntity {
    @Id
    @Column(name = "worker_type_id")
    public String id;
    @Column(name = "service_id")
    public String serviceId;
    @Column(name = "worker_type_name")
    public String name;
    @Enumerated(EnumType.STRING)
    public Status status;
    @Column(name = "created_at")
    public Instant createdAt;
    @Column(name = "updated_at")
    public Instant updatedAt;

    protected WorkerTypeEntity() {
    }

    public WorkerTypeEntity(String id, String serviceId, String name) {
        this.id = id;
        this.serviceId = serviceId;
        this.name = name;
        this.status = Status.ACTIVE;
        this.createdAt = this.updatedAt = Instant.now();
    }
}
