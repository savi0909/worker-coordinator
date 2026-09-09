package com.example.workercoordinator.repository.entity;

import com.example.workercoordinator.domain.model.Status;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "services")
public class ServiceEntity {
    @Id
    @Column(name = "service_id")
    public String id;
    @Column(name = "product_id")
    public String productId;
    @Column(name = "service_name")
    public String name;
    @Enumerated(EnumType.STRING)
    public Status status;
    @Column(name = "created_at")
    public Instant createdAt;
    @Column(name = "updated_at")
    public Instant updatedAt;

    protected ServiceEntity() {
    }

    public ServiceEntity(String id, String productId, String name) {
        this.id = id;
        this.productId = productId;
        this.name = name;
        this.status = Status.ACTIVE;
        this.createdAt = this.updatedAt = Instant.now();
    }
}
