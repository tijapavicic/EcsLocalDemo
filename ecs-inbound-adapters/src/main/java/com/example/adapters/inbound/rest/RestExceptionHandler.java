package com.example.adapters.inbound.rest;

import com.example.core.domain.StorageException;
import com.example.core.domain.TokenValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;

/**
 * <strong>Global exception handler</strong> — centralizes HTTP error response mapping.
 *
 * <p><strong>Single Responsibility Principle:</strong> All controllers delegate exception handling
 * to this advice. No exception mapping logic pollutes individual controllers.</p>
 *
 * <p><strong>Separates concerns:</strong></p>
 * <ul>
 *   <li>Controllers focus on HTTP routing</li>
 *   <li>This advice focuses on domain exception → HTTP response translation</li>
 * </ul>
 *
 * @see org.springframework.web.bind.annotation.RestControllerAdvice
 */
@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(TokenValidationException.class)
    public ResponseEntity<Map<String, String>> handleTokenValidation(TokenValidationException ex) {
        log.warn("Token validation error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or expired token", "detail", ex.getMessage()));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, String>> handleStorageException(StorageException ex) {
        log.error("Storage failure: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Storage operation failed", "detail", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Validation failure: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid request", "detail", ex.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolation(ConstraintViolationException ex) {
        String violations = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .reduce((s1, s2) -> s1 + "; " + s2)
                .orElse("validation failed");
        log.warn("Constraint violation: {}", violations);
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Validation constraint violated", "detail", violations));
    }
}

