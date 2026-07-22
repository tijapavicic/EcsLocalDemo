package com.example.core.domain;
/**
 * Domain exception - thrown when Bearer token validation fails.
 * Used by TokenExtractorPort implementations.
 */
public class TokenValidationException extends RuntimeException {
    public TokenValidationException(String message) {
        super(message);
    }
    public TokenValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
