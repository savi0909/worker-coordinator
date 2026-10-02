package com.example.workerclient;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class PaymentWriter {
    private final PaymentRepository payments;

    public PaymentWriter(PaymentRepository payments) {
        this.payments = payments;
    }

    @Transactional
    public PaymentEntity save(PaymentEntity payment) {
        return payments.saveAndFlush(payment);
    }
}
