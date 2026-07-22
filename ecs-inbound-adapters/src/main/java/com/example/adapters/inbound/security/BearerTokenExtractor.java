package com.example.adapters.inbound.security;

import com.example.core.domain.TokenValidationException;
import com.example.core.domain.UserContext;
import com.example.core.ports.TokenExtractorPort;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Inbound adapter - implements TokenExtractorPort.
 *
 * DEMO: Simple Base64 decoding (userId:email format)
 * PRODUCTION: Replace with real JWT validation (see IAM-IMPLEMENTATION-GUIDE.md)
 */
@Component
public class BearerTokenExtractor implements TokenExtractorPort {

    @Override
    public UserContext extractUserContext(String token) throws TokenValidationException {
        if (token == null || token.isBlank()) {
            throw new TokenValidationException("Token is blank");
        }

        try {
            // DEMO: Base64 format: userId:email
            String decoded = new String(Base64.getDecoder().decode(token));
            String[] parts = decoded.split(":");

            if (parts.length < 1) {
                throw new TokenValidationException("Invalid token format");
            }

            String userId = parts[0];
            String email = parts.length > 1 ? parts[1] : null;

            if (userId.isBlank()) {
                throw new TokenValidationException("userId is blank");
            }

            return new UserContext(userId, email, token);

        } catch (IllegalArgumentException ex) {
            throw new TokenValidationException("Invalid token encoding: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            extractUserContext(token);
            return true;
        } catch (TokenValidationException ex) {
            return false;
        }
    }
}

