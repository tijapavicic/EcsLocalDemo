package com.example.adapters.inbound.security;

import java.util.Optional;

/**
 * Helper utility - parses Authorization header.
 * Extracts Bearer token from HTTP request headers.
 */
public class AuthorizationExtractor {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    public static String extractBearerToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new IllegalArgumentException("Authorization header is missing");
        }

        if (!authHeader.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException("Authorization header must start with 'Bearer '");
        }

        String token = authHeader.substring(BEARER_PREFIX_LENGTH).trim();
        if (token.isBlank()) {
            throw new IllegalArgumentException("Bearer token is empty");
        }

        return token;
    }

    public static Optional<String> extractBearerTokenSafe(String authHeader) {
        try {
            return Optional.of(extractBearerToken(authHeader));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}

