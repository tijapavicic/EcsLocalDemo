package com.example.adapters.inbound.rest.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Type-safe validation configuration bound from YAML.
 *
 * <p><strong>Open/Closed Principle:</strong> Validation rules changed via configuration,
 * not by modifying code.</p>
 *
 * <p>Bound from {@code application.yml} under prefix {@code app.validation.upload}.</p>
 *
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.validation.upload")
public class ValidationConfiguration {

    /**
     * Maximum file size in bytes.
     * Default: 10MB (10 * 1024 * 1024 bytes)
     */
    private long maxFileSizeBytes = 10 * 1024 * 1024;

    /**
     * Allowed MIME type patterns (supports wildcards with *).
     * <p>Empty list = all content types allowed.</p>
     * <p>Examples: "image/*", "application/pdf", "text/plain"</p>
     */
    private List<String> allowedContentTypes = new ArrayList<>();
}

