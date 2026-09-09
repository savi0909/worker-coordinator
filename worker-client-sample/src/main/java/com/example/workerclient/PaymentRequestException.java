package com.example.workerclient;

public class PaymentRequestException extends RuntimeException {
    private final String code;

    public PaymentRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
