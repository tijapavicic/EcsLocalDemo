package com.example.application.observability;

import com.example.core.domain.StorageException;
import com.example.core.domain.StorageObject;
import com.example.core.ports.StoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <strong>Observable decorator</strong> — adds observability (logging, metrics) to {@link StoragePort}.
 *
 * <p><strong>Open/Closed Principle:</strong> Adds behavior without modifying {@link com.example.adapters.outbound.storage.S3StorageAdapter}.</p>
 *
 * <p><strong>Single Responsibility Principle:</strong> Observability concerns (logging, metrics)
 * are separated from storage logic. The adapter is clean and focused on S3 concerns.</p>
 *
 * <p><strong>Decorator Pattern:</strong> Wraps a {@link StoragePort} implementation and delegates
 * to it while adding cross-cutting concerns.</p>
 *
 * <p><strong>Metrics (future enhancement):</strong></p>
 * <ul>
 *   <li>storage.upload.success — counter by bucket</li>
 *   <li>storage.upload.failure — counter by bucket and error type</li>
 *   <li>storage.upload.duration — timer in milliseconds</li>
 * </ul>
 *
 * <p>To integrate: Replace bean wiring in {@code StorageConfiguration} to wrap adapter
 * with this decorator.</p>
 */
public class ObservableStoragePort implements StoragePort {

    private static final Logger log = LoggerFactory.getLogger(ObservableStoragePort.class);

    private final StoragePort delegate;

    /**
     * Construct with the decorated implementation.
     *
     * @param delegate the actual storage implementation (e.g., S3StorageAdapter)
     */
    public ObservableStoragePort(StoragePort delegate) {
        this.delegate = delegate;
    }

    @Override
    public StorageObject store(String bucket, String key, String contentType, byte[] content, String userId)
            throws StorageException {

        log.debug("Storage.store() START: bucket={} key={} contentType={} userId={} bytes={}",
                 bucket, key, contentType, userId, content.length);

        long startTime = System.currentTimeMillis();

        try {
            // Delegate to actual storage implementation
            StorageObject result = delegate.store(bucket, key, contentType, content, userId);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Storage.store() SUCCESS: bucket={} key={} userId={} bytes={} duration={}ms",
                    bucket, key, userId, content.length, duration);

            // Future: record metrics
            // meterRegistry.counter("storage.upload.success", "bucket", bucket).increment();
            // meterRegistry.timer("storage.upload.duration").record(duration, TimeUnit.MILLISECONDS);

            return result;

        } catch (StorageException ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Storage.store() FAILED: bucket={} key={} userId={} duration={}ms error={}",
                     bucket, key, userId, duration, ex.getMessage(), ex);

            // Future: record failure metrics
            // meterRegistry.counter("storage.upload.failure",
            //     "bucket", bucket,
            //     "error", ex.getClass().getSimpleName()).increment();

            throw ex;
        }
    }
}

