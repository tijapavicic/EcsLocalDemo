package com.example.core.ports;

import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;
import com.example.core.domain.UserContext;

/**
 * <strong>Inbound port</strong> — narrow, focused contract for file upload use case.
 *
 * <p>Implemented by {@link com.example.core.usecases.FileServiceImpl}.
 * Called by {@link com.example.adapters.inbound.rest.FileController}.</p>
 *
 * <p><strong>Interface Segregation Principle:</strong> Clients are not forced to depend on
 * unused methods. Single upload method with all required parameters.</p>
 *
 * <p>No Spring dependencies. Pure Java interface.</p>
 */
public interface FileServicePort {

    /**
     * Upload a file to cloud storage with user identity and story context.
     *
     * <p>File is stored in user and story-scoped path:
     * {@code users/{userId}/{storyId}/{yyyy-MM-dd}/filename.pdf}</p>
     *
     * <p><strong>Content Type Resolution:</strong> The declared content type may be null or blank.
     * If so, {@link ContentTypeResolverPort} will resolve it to a sensible default. The final
     * content type used for storage is determined by the resolution strategy.</p>
     *
     * <p><strong>Pre-conditions (validated by implementation):</strong></p>
     * <ul>
     *   <li>userContext must not be null</li>
     *   <li>storyId must not be blank</li>
     *   <li>filename must not be blank</li>
     *   <li>content must not be null or empty</li>
     *   <li>declaredContentType may be null (will be resolved to default)</li>
     * </ul>
     *
     * @param userContext          authenticated user (extracted from Bearer token)
     * @param storyId              story context ID for organizing files within user's space
     * @param filename             original filename (e.g. {@code report.pdf})
     * @param declaredContentType  MIME type declared in HTTP request (may be null)
     * @param content              raw bytes of the file
     * @return metadata of the stored object (includes userId for audit trail)
     * @throws StorageException         if the storage provider rejects the upload
     * @throws IllegalArgumentException if any pre-condition is violated
     */
    StorageObject upload(UserContext userContext, String storyId, String filename, String declaredContentType, byte[] content) throws StorageException;
}

