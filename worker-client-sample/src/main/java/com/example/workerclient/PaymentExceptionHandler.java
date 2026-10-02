package com.example.workerclient;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class PaymentExceptionHandler {
    @ExceptionHandler(PaymentRequestException.class)
    ResponseEntity<ErrorResponse> handlePaymentRequest(PaymentRequestException error) {
        return ResponseEntity.badRequest().body(new ErrorResponse(Instant.now(), error.code(), error.getMessage()));
    }

    public record ErrorResponse(Instant timestamp, String code, String message) {
    }
}
