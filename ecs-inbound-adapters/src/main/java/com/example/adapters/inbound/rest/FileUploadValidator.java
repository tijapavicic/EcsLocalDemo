package com.example.adapters.inbound.rest;

import com.example.adapters.inbound.rest.config.ValidationConfiguration;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * <strong>Input validation strategy</strong> — validates multipart file uploads.
 *
 * <p><strong>Separation of Concerns:</strong> Extracted from {@link FileController} to keep
 * the controller focused on HTTP routing. Validation logic is centralized and testable.</p>
 *
 * <p><strong>Open/Closed Principle:</strong> Validation rules configured via YAML
 * ({@link ValidationConfiguration}), not hardcoded. New rules added without code modification.</p>
 *
 * <p><strong>Validation Rules (configurable):</strong></p>
 * <ul>
 *   <li>File must not be null</li>
 *   <li>File must not be empty</li>
 *   <li>File size must not exceed {@code app.validation.upload.max-file-size-bytes}</li>
 *   <li>Content type must match {@code app.validation.upload.allowed-content-types} (if configured)</li>
 * </ul>
 *
 * <p>Throws {@link IllegalArgumentException} on validation failure — caught by
 * {@link RestExceptionHandler} and converted to HTTP 400 Bad Request.</p>
 */
@Component
public class FileUploadValidator {

    private final ValidationConfiguration config;

    /**
     * Constructor with injected configuration.
     *
     * @param config validation rules from YAML properties
     */
    public FileUploadValidator(ValidationConfiguration config) {
        this.config = config;
    }

    /**
     * Validate a multipart file upload against configured rules.
     *
     * <p>Fails fast on first validation error. All errors are thrown as
     * {@link IllegalArgumentException} for consistent error handling.</p>
     *
     * @param file the multipart file to validate
     * @throws IllegalArgumentException if file is null, empty, exceeds size limit, or content type not allowed
     */
    public void validate(MultipartFile file) {
        // Basic null/empty check
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required and must not be empty");
        }

        // Size validation (configurable limit)
        if (file.getSize() > config.getMaxFileSizeBytes()) {
            throw new IllegalArgumentException(
                    "file size (%d bytes) exceeds maximum allowed size (%d bytes)"
                            .formatted(file.getSize(), config.getMaxFileSizeBytes())
            );
        }

        // Content type validation (if restrictions configured)
        if (!config.getAllowedContentTypes().isEmpty()) {
            String contentType = file.getContentType();
            if (contentType == null || contentType.isBlank()) {
                throw new IllegalArgumentException(
                        "content type is required when restrictions are configured"
                );
            }

            if (!matchesAnyAllowedPattern(contentType, config.getAllowedContentTypes())) {
                throw new IllegalArgumentException(
                        "content type '%s' not allowed (allowed: %s)"
                                .formatted(contentType, String.join(", ", config.getAllowedContentTypes()))
                );
            }
        }
    }

    /**
     * Check if content type matches any of the allowed patterns.
     *
     * <p>Supports wildcard patterns (e.g., "image/*" matches "image/jpeg", "image/png").</p>
     *
     * @param contentType the content type to check
     * @param allowedPatterns list of allowed patterns
     * @return true if content type matches at least one pattern
     */
    private boolean matchesAnyAllowedPattern(String contentType, java.util.List<String> allowedPatterns) {
        return allowedPatterns.stream().anyMatch(pattern -> {
            if (pattern.endsWith("/*")) {
                // Wildcard match: "image/*" matches "image/jpeg"
                String prefix = pattern.substring(0, pattern.length() - 1);
                return contentType.startsWith(prefix);
            } else {
                // Exact match
                return contentType.equals(pattern);
            }
        });
    }
}

