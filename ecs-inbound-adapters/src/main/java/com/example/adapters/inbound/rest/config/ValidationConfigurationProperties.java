package com.example.adapters.inbound.rest.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for inbound adapter validation.
 *
 * <p>Enables {@link ValidationConfiguration} as a configurable property source.</p>
 */
@Configuration
@EnableConfigurationProperties(ValidationConfiguration.class)
public class ValidationConfigurationProperties {
    // This configuration class enables @ConfigurationProperties binding for ValidationConfiguration
}

