package com.example.core.usecases;

import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;
import com.example.core.domain.UserContext;
import com.example.core.ports.ContentTypeResolverPort;
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
 * workflow. Delegates content type resolution to {@link ContentTypeResolverPort}, key generation to
 * {@link StorageKeyGeneratorPort}, and storage to {@link StoragePort}.</p>
 *
 * <p><strong>Open/Closed Principle:</strong> New content type resolution strategies can be added
 * by implementing {@link ContentTypeResolverPort} without modifying this class.</p>
 *
 * <p><strong>Dependency Injection:</strong> Constructor injection of all dependencies.
 * No setters, no fields modified after construction.</p>
 */
public class FileServiceImpl implements FileServicePort {

    private final StoragePort storagePort;
    private final StorageKeyGeneratorPort keyGenerator;
    private final ContentTypeResolverPort contentTypeResolver;
    private final String bucket;

    /**
     * Construct with required dependencies.
     *
     * <p>All parameters are required (no nulls). Validation fails fast in constructor.</p>
     *
     * @param storagePort         outbound port for cloud storage operations
     * @param keyGenerator        outbound port for storage key generation strategy
     * @param contentTypeResolver outbound port for content type resolution strategy
     * @param bucket              target bucket name (read from YAML via {@code S3Configuration})
     * @throws IllegalArgumentException if any parameter is null or blank
     */
    public FileServiceImpl(StoragePort storagePort, StorageKeyGeneratorPort keyGenerator,
                          ContentTypeResolverPort contentTypeResolver, String bucket) {
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort must not be null");
        this.keyGenerator = Objects.requireNonNull(keyGenerator, "keyGenerator must not be null");
        this.contentTypeResolver = Objects.requireNonNull(contentTypeResolver, "contentTypeResolver must not be null");
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
     *   <li>Resolve content type (via {@link ContentTypeResolverPort})</li>
     *   <li>Generate user and story-scoped storage key (via {@link StorageKeyGeneratorPort})</li>
     *   <li>Store file in cloud (via {@link StoragePort})</li>
     *   <li>Return complete object with userId (set by adapter)</li>
     * </ol>
     *
     * @param userContext      authenticated user (extracted from Bearer token)
     * @param storyId          story context ID for organizing files within user's space
     * @param filename         original filename (e.g. {@code report.pdf})
     * @param declaredContentType MIME type declared in HTTP request (may be null — will be resolved to default if blank)
     * @param content          raw bytes of the file
     * @return metadata of the stored object (includes userId for audit trail)
     * @throws IllegalArgumentException if any parameter is invalid
     * @throws StorageException         if cloud storage operation fails
     */
    @Override
    public StorageObject upload(UserContext userContext, String storyId, String filename, String declaredContentType, byte[] content) throws StorageException {
        // Validate pre-conditions
        validateUploadParameters(userContext, storyId, filename, content);

        // Resolve content type (handles null/blank cases, returns default if needed)
        String contentType = contentTypeResolver.resolve(filename, declaredContentType);

        // Generate user and story-scoped key via strategy port
        String key = keyGenerator.generateKey(userContext.getUserId(), storyId, filename);

        // Store in cloud — adapter returns complete StorageObject with userId
        return storagePort.store(bucket, key, contentType, content, userContext.getUserId());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Validation — private method for internal use only
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Validate all upload parameters before processing.
     *
     * <p>Fails fast if any pre-condition is violated. Caller should handle exceptions.</p>
     *
     * <p>Note: contentType is NOT validated here because it will be resolved by
     * {@link ContentTypeResolverPort} which handles null/blank values gracefully.</p>
     *
     * @throws IllegalArgumentException if any required parameter is invalid
     */
    private void validateUploadParameters(UserContext userContext, String storyId, String filename, byte[] content) {
        if (userContext == null) {
            throw new IllegalArgumentException("userContext must not be null");
        }
        if (storyId == null || storyId.isBlank()) {
            throw new IllegalArgumentException("storyId must not be blank");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("file content must not be empty");
        }
    }
}

