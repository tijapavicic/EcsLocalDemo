package com.example.core.ports;

import com.example.core.domain.TokenValidationException;
import com.example.core.domain.UserContext;

/**
 * <strong>Inbound port</strong> — contract for Bearer token extraction and validation.
 *
 * <p>Implemented by adapters (e.g., {@code BearerTokenExtractor}).
 * Called by inbound adapters (e.g., {@code FileController}).</p>
 *
 * <p>Implementations must extract the {@code userId} from the Bearer token and return
 * an immutable {@link UserContext} object. Token format is implementation-specific:</p>
 *
 * <ul>
 *   <li><strong>Base64-encoded (development):</strong> {@code userId:email}</li>
 *   <li><strong>JWT (future):</strong> Extract claims from JWT payload</li>
 *   <li><strong>Custom (future):</strong> Proprietary token formats</li>
 * </ul>
 *
 * <p>No Spring dependencies. Pure Java interface.</p>
 */
public interface TokenExtractorPort {

    /**
     * Extract user identity from a Bearer token.
     *
     * <p>Called by inbound adapters after Bearer token is extracted from the
     * Authorization header (via {@code AuthorizationExtractor}).</p>
     *
     * @param token the raw Bearer token (e.g., base64-encoded or JWT)
     * @return immutable {@link UserContext} with userId and email
     * @throws TokenValidationException if token is invalid, malformed, or expired
     * @throws IllegalArgumentException if token is null or blank
     */
    UserContext extractUserContext(String token) throws TokenValidationException;

    /**
     * Validate a Bearer token without extracting user context.
     *
     * @param token the raw Bearer token
     * @return {@code true} if the token is valid; {@code false} otherwise
     */
    boolean isTokenValid(String token);
}


