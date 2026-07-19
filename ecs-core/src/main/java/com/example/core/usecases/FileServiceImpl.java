package com.example.core.usecases;

import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;
import com.example.core.ports.FileServicePort;
import com.example.core.ports.StoragePort;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Core use-case: orchestrates file upload.
 *
 * <p><strong>No Spring annotations.</strong> This class is instantiated
 * by {@code StorageConfiguration} in the application module via constructor injection.</p>
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validates input (non-null, non-empty content)</li>
 *   <li>Generates a collision-free storage key: {@code yyyy-MM-dd/uuid-filename}</li>
 *   <li>Delegates persistence to {@link StoragePort}</li>
 * </ul>
 * </p>
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
    public StorageObject upload(String filename, String contentType, byte[] content) throws StorageException {
        validateFilename(filename);
        validateContent(content);

        String key = generateKey(filename);
        return storagePort.store(bucket, key, contentType, content);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates a unique, date-partitioned key to avoid collisions and aid sorting.
     * Example: {@code 2024-01-15/3f4a1b2c-report.pdf}
     */
    private String generateKey(String filename) {
        String date = LocalDate.now().toString();
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return date + "/" + uuid + "-" + filename;
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

