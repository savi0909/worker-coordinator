package com.example.workerclient;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity {
    @Id
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "client_idempotency_key", nullable = false, unique = true)
    private UUID clientIdempotencyKey;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    protected PaymentEntity() {
    }

    public PaymentEntity(long id, UUID clientIdempotencyKey, BigDecimal amount) {
        this.id = id;
        this.clientIdempotencyKey = clientIdempotencyKey;
        this.amount = amount;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getClientIdempotencyKey() {
        return clientIdempotencyKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
