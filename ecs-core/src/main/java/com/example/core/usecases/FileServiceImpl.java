package com.example.core.usecases;

import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;
import com.example.core.domain.UserContext;
import com.example.core.ports.FileServicePort;
import com.example.core.ports.StorageKeyGeneratorPort;
import com.example.core.ports.StoragePort;

import java.util.Objects;

/**
 * Core use-case: orchestrates file upload with IAM support.
 *
 * <p><strong>No Spring annotations.</strong> Pure Java implementation.</p>
 *
 * <p><strong>Single Responsibility Principle:</strong> Focuses solely on orchestrating the upload
 * workflow. Delegates key generation to {@link StorageKeyGeneratorPort} and storage to
 * {@link StoragePort}.</p>
 *
 * <p><strong>Dependency Injection:</strong> Constructor injection of all dependencies.
 * No setters, no fields modified after construction.</p>
 */
public class FileServiceImpl implements FileServicePort {

    private final StoragePort storagePort;
    private final StorageKeyGeneratorPort keyGenerator;
    private final String bucket;

    /**
     * Construct with required dependencies.
     *
     * <p>All parameters are required (no nulls). Validation fails fast in constructor.</p>
     *
     * @param storagePort    outbound port for cloud storage operations
     * @param keyGenerator   outbound port for storage key generation strategy
     * @param bucket         target bucket name (read from YAML via {@code S3Configuration})
     * @throws IllegalArgumentException if any parameter is null or blank
     */
    public FileServiceImpl(StoragePort storagePort, StorageKeyGeneratorPort keyGenerator, String bucket) {
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort must not be null");
        this.keyGenerator = Objects.requireNonNull(keyGenerator, "keyGenerator must not be null");
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        this.bucket = bucket;
    }

    /**
     * Upload a file to cloud storage with user identity and story context.
     *
     * <p><strong>Flow:</strong></p>
     * <ol>
     *   <li>Validate pre-conditions</li>
     *   <li>Generate user and story-scoped storage key (via {@link StorageKeyGeneratorPort})</li>
     *   <li>Store file in cloud (via {@link StoragePort})</li>
     *   <li>Attach userId to response for audit trail</li>
     * </ol>
     *
     * @param userContext authenticated user (extracted from Bearer token)
     * @param storyId     story context ID for organizing files within user's space
     * @param filename    original filename (e.g. {@code report.pdf})
     * @param contentType MIME type (e.g. {@code application/pdf})
     * @param content     raw bytes of the file
     * @return metadata of the stored object (includes userId for audit trail)
     * @throws IllegalArgumentException if any parameter is invalid
     * @throws StorageException         if cloud storage operation fails
     */
    @Override
    public StorageObject upload(UserContext userContext, String storyId, String filename, String contentType, byte[] content) throws StorageException {
        // Validate pre-conditions
        validateUploadParameters(userContext, storyId, filename, contentType, content);

        // Generate user and story-scoped key via strategy port
        String key = keyGenerator.generateKey(userContext.getUserId(), storyId, filename);

        // Store in cloud and receive metadata
        StorageObject stored = storagePort.store(bucket, key, contentType, content);

        // Attach userId to response for audit trail
        return new StorageObject(
                stored.getKey(),
                stored.getBucket(),
                stored.getContentType(),
                stored.getSizeBytes(),
                stored.getUploadedAt(),
                userContext.getUserId()
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Validation — private method for internal use only
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Validate all upload parameters before processing.
     *
     * <p>Fails fast if any pre-condition is violated. Caller should handle exceptions.</p>
     *
     * @throws IllegalArgumentException if any parameter is invalid
     */
    private void validateUploadParameters(UserContext userContext, String storyId, String filename, String contentType, byte[] content) {
        if (userContext == null) {
            throw new IllegalArgumentException("userContext must not be null");
        }
        if (storyId == null || storyId.isBlank()) {
            throw new IllegalArgumentException("storyId must not be blank");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType must not be blank");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("file content must not be empty");
        }
    }
}

