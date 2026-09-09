package com.example.workercoordinator.api.controller;

import com.example.workercoordinator.domain.exception.CoordinatorException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(CoordinatorException.class)
    ResponseEntity<ErrorResponse> coordinator(CoordinatorException e, HttpServletRequest r) {
        HttpStatus status = e.code().endsWith("NOT_FOUND") ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), e.code(), e.getMessage(), r.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> invalid(MethodArgumentNotValidException e, HttpServletRequest r) {
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "code", "INVALID_REQUEST", "path", r.getRequestURI(), "errors", e.getBindingResult().getFieldErrors().stream().collect(java.util.stream.Collectors.toMap(x -> x.getField(), x -> x.getDefaultMessage(), (a, b) -> a))));
    }

    record ErrorResponse(Instant timestamp, String code, String message, String path) {
    }
}
