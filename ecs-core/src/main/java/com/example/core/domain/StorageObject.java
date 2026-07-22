package com.example.core.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.time.Instant;

/**
 * Immutable value object representing a successfully uploaded object in cloud storage.
 * Provider-agnostic — works with AWS S3, Azure Blob, and MinIO.
 *
 * <p>No Spring dependencies. Pure domain object.</p>
 */
@Value
public class StorageObject {

    /** Storage key (path) within the bucket, e.g. {@code 2024-01-15/uuid-filename.jpg} */
    @JsonProperty("key")
    String key;

    /** Bucket the object lives in. */
    @JsonProperty("bucket")
    String bucket;

    /** MIME type, e.g. {@code image/jpeg}, {@code application/pdf}. */
    @JsonProperty("contentType")
    String contentType;

    /** Size in bytes. */
    @JsonProperty("sizeBytes")
    long sizeBytes;

    /** UTC timestamp of the upload. */
    @JsonProperty("uploadedAt")
    Instant uploadedAt;

    /** User ID - who uploaded this file. */
    @JsonProperty("userId")
    String userId;
}
