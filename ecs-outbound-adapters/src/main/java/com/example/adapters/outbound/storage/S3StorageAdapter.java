package com.example.adapters.outbound.storage;

import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;
import com.example.core.ports.StoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Instant;

/**
 * <strong>Outbound adapter</strong> — implements {@link StoragePort} using AWS SDK v2.
 *
 * <p>Works for <em>both</em> AWS S3 and MinIO. MinIO is S3-compatible — the only
 * difference is the endpoint URL and path-style access, both handled in
 * {@link com.example.application.config.StorageConfiguration}.</p>
 *
 * <p>Contains ZERO business logic. Translates domain calls to SDK calls and back.</p>
 */
@Component
public class S3StorageAdapter implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(S3StorageAdapter.class);

    /** S3Client is configured by {@link com.example.application.config.StorageConfiguration}. */
    private final S3Client s3Client;

    public S3StorageAdapter(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public StorageObject store(String bucket, String key, String contentType, byte[] content)
            throws StorageException {

        log.debug("Storing object: bucket={} key={} contentType={} bytes={}", bucket, key, contentType, content.length);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) content.length)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(content));

            log.info("Stored: s3://{}/{} ({} bytes)", bucket, key, content.length);

            // Return StorageObject without userId — userId is set by FileServiceImpl
            // from the UserContext after storage succeeds.
            return new StorageObject(key, bucket, contentType, (long) content.length, Instant.now(), null);

        } catch (S3Exception ex) {
            throw new StorageException(
                    "S3 store failed [bucket=%s key=%s]: %s".formatted(bucket, key, ex.awsErrorDetails().errorMessage()),
                    ex
            );
        } catch (Exception ex) {
            throw new StorageException(
                    "Unexpected error storing [bucket=%s key=%s]: %s".formatted(bucket, key, ex.getMessage()),
                    ex
            );
        }
    }
}

