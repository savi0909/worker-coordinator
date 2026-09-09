package com.example.workercoordinator.domain.exception;

public class CoordinatorException extends RuntimeException {
    private final String code;

    public CoordinatorException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
