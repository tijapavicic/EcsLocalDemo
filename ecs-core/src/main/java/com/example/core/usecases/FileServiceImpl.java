package com.example.core.usecases;

import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;
import com.example.core.domain.UserContext;
import com.example.core.ports.FileServicePort;
import com.example.core.ports.StoragePort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Core use-case: orchestrates file upload with IAM support.
 *
 * <p><strong>No Spring annotations.</strong> Pure Java implementation.</p>
 *
 * <p>Generates user-scoped storage keys: {@code users/{userId}/yyyy-MM-dd/uuid-filename}</p>
 */
public class FileServiceImpl implements FileServicePort {

    private final StoragePort storagePort;
    private final String bucket;

    /**
     * @param storagePort outbound port — injected by the application config
     * @param bucket      target bucket name read from YAML via {@code S3Configuration}
     */
    public FileServiceImpl(StoragePort storagePort, String bucket) {
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort must not be null");
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        this.bucket = bucket;
    }

    @Override
    @Deprecated
    public StorageObject upload(String filename, String contentType, byte[] content) throws StorageException {
        UserContext anonymousUser = new UserContext("anonymous", null, null);
        return upload(anonymousUser, filename, contentType, content);
    }

    @Override
    public StorageObject upload(UserContext userContext, String filename, String contentType, byte[] content) throws StorageException {
        if (userContext == null) {
            throw new IllegalArgumentException("userContext must not be null");
        }
        validateFilename(filename);
        validateContent(content);

        String key = generateUserScopedKey(userContext.getUserId(), filename);

        try {
            StorageObject result = storagePort.store(bucket, key, contentType, content);

            // Attach userId to result
            return new StorageObject(
                    result.getKey(),
                    result.getBucket(),
                    result.getContentType(),
                    result.getSizeBytes(),
                    result.getUploadedAt(),
                    userContext.getUserId()
            );
        } catch (StorageException ex) {
            throw ex;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates user-scoped key: users/{userId}/2024-01-15/uuid-filename
     */
    private String generateUserScopedKey(String userId, String filename) {
        String date = LocalDate.now(ZoneId.of("UTC")).toString();
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String safeFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return String.format("users/%s/%s/%s-%s", userId, date, uuid, safeFilename);
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
    }

    private void validateContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("file content must not be empty");
        }
    }
}

