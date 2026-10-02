package com.example.workerclient;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    public ResponseEntity<PaymentDtos.PaymentResponse> create(@Valid @RequestBody PaymentDtos.CreatePaymentRequest request) {
        var result = payments.create(request.clientIdempotencyKey(), request.amount());
        return result.created()
                ? ResponseEntity.created(URI.create("/api/v1/payments/" + result.response().id())).body(result.response())
                : ResponseEntity.ok(result.response());
    }
}
