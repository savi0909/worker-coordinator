package com.example.workerclient;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {
    private PaymentDtos() { }

    public record CreatePaymentRequest(@NotNull UUID clientIdempotencyKey, @NotNull @Positive BigDecimal amount) { }
    public record PaymentResponse(long id, UUID clientIdempotencyKey, BigDecimal amount, Instant createdAt, Instant updatedAt) { }
}
