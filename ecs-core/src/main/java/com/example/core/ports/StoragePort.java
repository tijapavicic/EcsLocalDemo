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
     * Persist bytes to the given bucket under the given key.
     *
     * @param bucket      target bucket / container name
     * @param key         storage key (path), e.g. {@code uploads/2024-01-15/uuid-file.pdf}
     * @param contentType MIME type
     * @param content     raw bytes
     * @return metadata of the stored object
     * @throws StorageException if the provider rejects the operation
     */
    StorageObject store(String bucket, String key, String contentType, byte[] content) throws StorageException;
}

