package com.example.application.config;

import com.example.adapters.inbound.security.BearerTokenAuthenticationFilter;
import com.example.adapters.inbound.security.BearerTokenExtractor;
import com.example.core.ports.TokenExtractorPort;
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
     *   <li>🔐 Require authentication for all other /api/** endpoints</li>
     *   <li>✅ Stateless (no sessions, no cookies) — each request is independent</li>
     * </ul>
     *
     * <p>Registers {@link BearerTokenAuthenticationFilter} before Spring Security's
     * default username/password filter to intercept requests first.</p>
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
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules (Spring Security 6+ lambda-based API)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()  // Health checks, metrics
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()  // API docs
                        .requestMatchers("/api/files/**").authenticated()  // Require auth for uploads
                        .anyRequest().permitAll()  // Everything else is public
                )

                // Add bearer token filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
