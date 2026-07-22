package com.example.application.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test configuration that disables Spring Security for integration tests.
 *
 * <p>Allows Bearer token tests to execute without authentication issues.
 * This configuration is only loaded during test execution.</p>
 */
@TestConfiguration
@EnableWebSecurity
public class SecurityTestConfiguration {

    /**
     * Disable security for integration tests.
     *
     * <p>Integration tests mock the Bearer token behavior, so we don't need
     * real Spring Security configuration here.</p>
     */
    @Bean
    public SecurityFilterChain disabledSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authorizeRequests()
                .anyRequest().permitAll();

        return http.build();
    }
}

