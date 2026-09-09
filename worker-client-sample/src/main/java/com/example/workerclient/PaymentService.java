package com.example.workerclient;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final PaymentIdGenerator ids;
    private final PaymentWriter writer;

    public PaymentService(PaymentRepository payments, PaymentIdGenerator ids, PaymentWriter writer) {
        this.payments = payments;
        this.ids = ids;
        this.writer = writer;
    }

    public PaymentResult create(UUID idempotencyKey, BigDecimal amount) {
        if (!UuidV7.isV7(idempotencyKey)) {
            throw new PaymentRequestException("INVALID_IDEMPOTENCY_KEY", "clientIdempotencyKey must be UUIDv7");
        }
        var existing = payments.findByClientIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return new PaymentResult(toResponse(existing.get()), false);

        var payment = new PaymentEntity(ids.nextPaymentIdLong(), idempotencyKey, amount);
        try {
            return new PaymentResult(toResponse(writer.save(payment)), true);
        } catch (DataIntegrityViolationException duplicate) {
            return payments.findByClientIdempotencyKey(idempotencyKey)
                    .map(found -> new PaymentResult(toResponse(found), false))
                    .orElseThrow(() -> duplicate);
        }
    }

    private PaymentDtos.PaymentResponse toResponse(PaymentEntity payment) {
        return new PaymentDtos.PaymentResponse(payment.getId(), payment.getClientIdempotencyKey(), payment.getAmount(),
                payment.getCreatedAt(), payment.getUpdatedAt());
    }

    public record PaymentResult(PaymentDtos.PaymentResponse response, boolean created) { }
}
