package com.example.workercoordinator.repository.entity;

import com.example.workercoordinator.domain.model.Status;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "products")
public class ProductEntity {
    @Id
    @Column(name = "product_id")
    public String id;
    @Column(name = "product_name")
    public String name;
    @Enumerated(EnumType.STRING)
    public Status status;
    @Column(name = "created_at")
    public Instant createdAt;
    @Column(name = "updated_at")
    public Instant updatedAt;

    protected ProductEntity() {
    }

    public ProductEntity(String id, String name) {
        this.id = id;
        this.name = name;
        this.status = Status.ACTIVE;
        this.createdAt = this.updatedAt = Instant.now();
    }
}
