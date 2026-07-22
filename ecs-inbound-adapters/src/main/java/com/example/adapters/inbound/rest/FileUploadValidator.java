package com.example.adapters.inbound.rest;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * <strong>Input validation strategy</strong> — validates multipart file uploads.
 *
 * <p><strong>Separation of Concerns:</strong> Extracted from {@link FileController} to keep
 * the controller focused on HTTP routing. Validation logic is centralized and testable.</p>
 *
 * <p><strong>Validation Rules:</strong></p>
 * <ul>
 *   <li>File must not be null</li>
 *   <li>File must not be empty</li>
 *   <li>File size must not exceed limit (default: 10MB)</li>
 * </ul>
 *
 * <p>Throws {@link IllegalArgumentException} on validation failure — caught by
 * {@link RestExceptionHandler} and converted to HTTP 400 Bad Request.</p>
 */
@Component
public class FileUploadValidator {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;  // 10 MB

    /**
     * Validate a multipart file upload.
     *
     * <p>Fails fast on first validation error. All errors are thrown as
     * {@link IllegalArgumentException} for consistent error handling.</p>
     *
     * @param file the multipart file to validate
     * @throws IllegalArgumentException if file is null, empty, or exceeds size limit
     */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required and must not be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "file size (" + file.getSize() + " bytes) exceeds maximum allowed size (" + MAX_FILE_SIZE + " bytes)"
            );
        }
    }
}

