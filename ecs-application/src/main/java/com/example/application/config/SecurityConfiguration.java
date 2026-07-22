package com.example.application.config;

import com.example.adapters.inbound.security.BearerTokenAuthenticationFilter;
import com.example.adapters.inbound.security.BearerTokenExtractor;
import com.example.core.ports.TokenExtractorPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * <strong>Application configuration</strong> — wires security components.
 * ZERO business logic — configuration ONLY.
 *
 * <p>Configures:</p>
 * <ul>
 *   <li>Bearer token extractor bean ({@link TokenExtractorPort})</li>
 *   <li>Bearer token authentication filter registration</li>
 *   <li>Spring Security filter chain (stateless, no sessions)</li>
 *   <li>HTTP security rules (permit actuator, require auth for /api/files)</li>
 * </ul>
 *
 * <p><strong>Follows hexagonal architecture:</strong> No business logic here,
 * only Spring configuration and bean wiring.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    /**
     * Feature flag to enable/disable Bearer token authentication.
     * Configure via: app.security.bearer-token-auth-enabled=true|false
     * Or environment: APP_SECURITY_BEARER_TOKEN_AUTH_ENABLED=true|false
     */
    @Value("${app.security.bearer-token-auth-enabled:true}")
    private boolean bearerAuthEnabled;

    /**
     * Bean: Token extractor adapter (inbound adapter → inbound port).
     *
     * <p>Implements {@link TokenExtractorPort} using Base64 format (demo).
     * Replace with JWT validation in production.</p>
     *
     * @return the token extractor implementation
     */
    @Bean
    public TokenExtractorPort tokenExtractor() {
        return new BearerTokenExtractor();
    }

    /**
     * Bean: Bearer token authentication filter.
     *
     * <p>Intercepts requests, extracts Bearer token, validates, and stores
     * UserContext in ThreadLocal for downstream access.</p>
     *
     * @param tokenExtractor the token extractor port
     * @return the authentication filter
     */
    @Bean
    public BearerTokenAuthenticationFilter bearerTokenAuthenticationFilter(TokenExtractorPort tokenExtractor) {
        return new BearerTokenAuthenticationFilter(tokenExtractor);
    }

    /**
     * Configure Spring Security filter chain.
     *
     * <p>Rules:</p>
     * <ul>
     *   <li>✅ Permit actuator endpoints (health, metrics) — no auth required</li>
     *   <li>✅ Permit swagger-ui (if present) — no auth required</li>
     *   <li>🔐 Require authentication for all other /api/** endpoints (if enabled)</li>
     *   <li>✅ Stateless (no sessions, no cookies) — each request is independent</li>
     * </ul>
     *
     * <p><strong>Approach 1: Property-Based Toggle</strong></p>
     * <ul>
     *   <li>If {@code app.security.bearer-token-auth-enabled=true}:
     *       Register Bearer token filter and require auth for /api/** endpoints</li>
     *   <li>If {@code app.security.bearer-token-auth-enabled=false}:
     *       Skip Bearer token filter and allow all requests (for testing/debugging)</li>
     * </ul>
     *
     * <p>Registers {@link BearerTokenAuthenticationFilter} before Spring Security's
     * default username/password filter to intercept requests first (when enabled).</p>
     *
     * @param http the HttpSecurity builder
     * @param bearerFilter the bearer token authentication filter
     * @return configured filter chain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           BearerTokenAuthenticationFilter bearerFilter) throws Exception {
        http
                // Disable CSRF (stateless API, no session)
                .csrf(csrf -> csrf.disable())

                // Stateless — no cookies, no sessions
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (bearerAuthEnabled) {
            // ╔════════════════════════════════════════════════════════════╗
            // ║ AUTHENTICATION ENABLED                                     ║
            // ║ Bearer tokens are required for /api/files/** endpoints     ║
            // ╚════════════════════════════════════════════════════════════╝
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/**").permitAll()  // Health checks, metrics
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()  // API docs
                    .requestMatchers("/api/files/**").authenticated()  // ✅ Require Bearer token
                    .anyRequest().permitAll()  // Everything else is public
            )
            // Add bearer token filter before UsernamePasswordAuthenticationFilter
            .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class);

        } else {
            // ╔════════════════════════════════════════════════════════════╗
            // ║ AUTHENTICATION DISABLED (Testing/Debugging)               ║
            // ║ All requests are allowed without Bearer token             ║
            // ║ ⚠️ WARNING: DO NOT USE IN PRODUCTION                      ║
            // ╚════════════════════════════════════════════════════════════╝
            http.authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll()  // Allow all requests
            );
            // Bearer token filter is not registered — skipped entirely
        }

        return http.build();
    }
}
