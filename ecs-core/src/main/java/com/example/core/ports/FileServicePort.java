package com.example.core.ports;

import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;

/**
 * <strong>Inbound port</strong> — the contract the REST layer calls into.
 *
 * <p>Implemented by {@link com.example.core.usecases.FileServiceImpl}.
 * Called by {@link com.example.adapters.inbound.rest.FileController}.</p>
 *
 * <p>No Spring dependencies. Pure Java interface.</p>
 */
public interface FileServicePort {

    /**
     * Upload a file to cloud storage.
     *
     * @param filename    original filename (e.g. {@code report.pdf})
     * @param contentType MIME type (e.g. {@code application/pdf})
     * @param content     raw bytes of the file — must not be null or empty
     * @return metadata of the stored object
     * @throws StorageException         if the storage provider rejects the upload
     * @throws IllegalArgumentException if filename is blank or content is empty
     */
    StorageObject upload(String filename, String contentType, byte[] content) throws StorageException;
}

