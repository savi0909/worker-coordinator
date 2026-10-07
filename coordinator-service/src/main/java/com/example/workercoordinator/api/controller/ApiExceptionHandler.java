package com.example.workercoordinator.api.controller;

import com.example.workercoordinator.domain.exception.CoordinatorException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(CoordinatorException.class)
    ResponseEntity<ErrorResponse> coordinator(CoordinatorException e, HttpServletRequest r) {
        HttpStatus status = e.code().endsWith("NOT_FOUND") ? HttpStatus.NOT_FOUND
                : e.code().startsWith("INVALID_") ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), e.code(), e.getMessage(), r.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> invalid(MethodArgumentNotValidException e, HttpServletRequest r) {
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "code", "INVALID_REQUEST", "path", r.getRequestURI(), "errors", e.getBindingResult().getFieldErrors().stream().collect(java.util.stream.Collectors.toMap(x -> x.getField(), x -> x.getDefaultMessage(), (a, b) -> a))));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<?> invalidParameter(HandlerMethodValidationException e, HttpServletRequest r) {
        Map<String, String> errors = e.getAllValidationResults().stream().collect(Collectors.toMap(
                x -> x.getMethodParameter().getParameterName(),
                x -> x.getResolvableErrors().getFirst().getDefaultMessage(), (a, b) -> a));
        return invalidRequest(r, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<?> mismatchedParameter(MethodArgumentTypeMismatchException e, HttpServletRequest r) {
        return invalidRequest(r, Map.of(e.getName(), "has an unsupported value"));
    }

    private ResponseEntity<?> invalidRequest(HttpServletRequest r, Map<String, String> errors) {
        return ResponseEntity.badRequest().body(Map.of("timestamp", Instant.now(), "code", "INVALID_REQUEST", "path", r.getRequestURI(), "errors", errors));
    }

    record ErrorResponse(Instant timestamp, String code, String message, String path) {
    }
}
