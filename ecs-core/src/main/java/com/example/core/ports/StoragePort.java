package com.example.core.ports;

import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;

/**
 * <strong>Outbound port</strong> — the contract that adapters must implement to persist files.
 *
 * <p>Implemented by {@link com.example.adapters.outbound.storage.S3StorageAdapter}
 * (works for both AWS S3 and MinIO via path-style access).</p>
 *
 * <p>No Spring dependencies. Pure Java interface.</p>
 */
public interface StoragePort {

    /**
     * Persist bytes to the given bucket under the given key with user identity for audit trail.
     *
     * <p><strong>Liskov Substitution Principle:</strong> Adapter returns a complete {@link StorageObject}
     * with all fields populated (including userId). No post-processing required by caller.</p>
     *
     * @param bucket      target bucket / container name
     * @param key         storage key (path), e.g. {@code users/alice/story-123/2024-01-15/uuid-file.pdf}
     * @param contentType MIME type
     * @param content     raw bytes
     * @param userId      authenticated user ID (for audit trail and S3 object metadata)
     * @return complete metadata of the stored object (includes userId)
     * @throws StorageException if the provider rejects the operation
     * @throws IllegalArgumentException if userId is null or blank
     */
    StorageObject store(String bucket, String key, String contentType, byte[] content, String userId) throws StorageException;
}

