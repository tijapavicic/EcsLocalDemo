package com.example.adapters.inbound.security;

import com.example.core.domain.TokenValidationException;
import com.example.core.domain.UserContext;
import com.example.core.ports.TokenExtractorPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Spring Security filter for Bearer token authentication.
 *
 * <p><strong>Flow:</strong></p>
 * <ol>
 *   <li>Intercepts every HTTP request</li>
 *   <li>Extracts Bearer token from Authorization header (via AuthorizationExtractor)</li>
 *   <li>Validates token and extracts UserContext (via TokenExtractorPort)</li>
 *   <li>Sets Spring Security Authentication in SecurityContextHolder</li>
 *   <li>Stores UserContext in RequestContextHolder (thread-local)</li>
 *   <li>Proceeds to next filter/controller</li>
 *   <li>Clears context in finally block to prevent ThreadLocal leaks</li>
 * </ol>
 *
 * <p><strong>Token Format:</strong> Authorization: Bearer &lt;token&gt;</p>
 * <p>Example: {@code Authorization: Bearer dXNlcjEyMzp1c2VyQGV4YW1wbGUuY29t}</p>
 *
 * <p><strong>Error Handling:</strong></p>
 * <ul>
 *   <li>Missing Authorization header → Proceeds without authentication (filter allows)</li>
 *   <li>Invalid Bearer token format → 400 Bad Request</li>
 *   <li>Token validation fails → 401 Unauthorized</li>
 *   <li>Successful token → Sets Authentication in SecurityContextHolder</li>
 * </ul>
 *
 * <p><strong>Thread-Safety:</strong> Extends OncePerRequestFilter to ensure filter
 * executes exactly once per request. Uses ThreadLocal (RequestContextHolder) and
 * SecurityContextHolder for storing user context.</p>
 *
 * @see RequestContextHolder
 * @see AuthorizationExtractor
 * @see TokenExtractorPort
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final TokenExtractorPort tokenExtractor;

    public BearerTokenAuthenticationFilter(TokenExtractorPort tokenExtractor) {
        this.tokenExtractor = tokenExtractor;
    }

    /**
     * Filter logic - extract and validate Bearer token.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param filterChain filter chain to proceed through
     * @throws ServletException if filter chain processing fails
     * @throws IOException if I/O error occurs
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Extract Authorization header
            String authHeader = request.getHeader(AUTHORIZATION_HEADER);

            if (authHeader != null && !authHeader.isBlank()) {
                try {
                    // Extract Bearer token and validate
                    String token = AuthorizationExtractor.extractBearerToken(authHeader);
                    UserContext userContext = tokenExtractor.extractUserContext(token);

                    // Set Spring Security Authentication (required for @Authenticated endpoints)
                    Authentication authentication = new UsernamePasswordAuthenticationToken(
                            userContext.getUserId(),
                            token,
                            new ArrayList<>()  // Empty authorities for now
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // Store in thread-local for downstream access
                    RequestContextHolder.set(userContext);
                    log.debug("Bearer token authenticated: userId={}", userContext.getUserId());

                } catch (IllegalArgumentException ex) {
                    log.warn("Invalid Bearer token format: {}", ex.getMessage());
                    // Invalid format - clear any authentication and let ExceptionTranslationFilter handle it
                    SecurityContextHolder.clearContext();
                    throw new jakarta.servlet.ServletException("Invalid Bearer token format", ex);
                } catch (TokenValidationException ex) {
                    log.warn("Token validation failed: {}", ex.getMessage());
                    // Token validation failed - clear any authentication and let ExceptionTranslationFilter handle it
                    SecurityContextHolder.clearContext();
                    throw new jakarta.servlet.ServletException("Token validation failed", ex);
                }
            } else {
                log.debug("No Authorization header present");
                // No Authorization header - allow request to proceed (public endpoints will succeed,
                // protected endpoints will be caught by AuthorizationFilter and return 403)
            }

            // Proceed to next filter/controller
            filterChain.doFilter(request, response);

        } finally {
            // Always clean up ThreadLocal to prevent leaks
            RequestContextHolder.clear();
            // Note: Do NOT clear SecurityContextHolder here - let Spring manage it
        }
    }

    /**
     * Determine which request paths should NOT be filtered.
     *
     * <p>By default, all paths are filtered. Override to exclude specific paths
     * (e.g., health checks, public endpoints, swagger-ui).</p>
     *
     * @param request current HTTP request
     * @return true if this filter should NOT be applied to this request
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Don't filter actuator endpoints (health, metrics)
        if (path.startsWith("/actuator")) {
            return true;
        }

        // Don't filter swagger/api-docs (if present)
        if (path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
            return true;
        }

        return false;
    }
}

