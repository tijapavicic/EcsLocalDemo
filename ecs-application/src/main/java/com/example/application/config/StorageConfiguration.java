package com.example.application.config;

import com.example.core.ports.ContentTypeResolverPort;
import com.example.core.ports.FileServicePort;
import com.example.core.ports.StorageKeyGeneratorPort;
import com.example.core.ports.StoragePort;
import com.example.core.usecases.FileServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

/**
 * <strong>Application configuration</strong> — wires together all adapters and use-cases.
 *
 * <p>This is the ONLY place where beans are defined. It reads {@link S3Configuration}
 * (profile-specific YAML) and builds either a MinIO or AWS-flavored {@link S3Client}.</p>
 *
 * <h2>Dependency Injection Pattern:</h2>
 * <pre>
 * S3StorageAdapter (component) → StoragePort (port)
 * UserScopedKeyGenerator (component) → StorageKeyGeneratorPort (port)
 * FileServiceImpl (bean) ← StoragePort + StorageKeyGeneratorPort (dependencies)
 * </pre>
 *
 * <h2>How profile switching works:</h2>
 * <pre>
 * application-local-minio.yml → endpoint set      → path-style + endpoint override + static creds
 * application-aws.yml         → endpoint absent   → standard AWS SDK + default credentials chain
 * </pre>
 *
 * <h2>Credential resolution:</h2>
 * <ul>
 *   <li>If {@code access-key} and {@code secret-key} are set in YAML → static credentials (MinIO / dev)</li>
 *   <li>Otherwise → {@link DefaultCredentialsProvider}: env vars → system props → IAM role (production)</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(S3Configuration.class)
public class StorageConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StorageConfiguration.class);

    /**
     * Creates an {@link S3Client} configured for MinIO (when {@code endpoint} is set)
     * or for real AWS S3 (when {@code endpoint} is absent).
     *
     * <p>Credentials: uses static keys when provided in YAML; falls back to the
     * AWS Default Credentials Chain (env vars, system props, IAM role) otherwise.</p>
     *
     * @param cfg the profile-specific S3 properties
     */
    @Bean
    public S3Client s3Client(S3Configuration cfg) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(cfg.getRegion()));

        // Use static credentials only when explicitly provided (MinIO / dev environment).
        // For production on AWS ECS, leave keys blank and let the IAM role supply credentials.
        if (hasStaticCredentials(cfg)) {
            log.info("Using static credentials from configuration");
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(cfg.getAccessKey(), cfg.getSecretKey())));
        } else {
            log.info("No static credentials configured — using AWS Default Credentials Chain (env vars / IAM role)");
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        if (cfg.getEndpoint() != null && !cfg.getEndpoint().isBlank()) {
            // MinIO requires path-style access (bucket in URL path, not subdomain)
            log.info("Configuring S3Client for MinIO at {}", cfg.getEndpoint());
            builder.endpointOverride(URI.create(cfg.getEndpoint()))
                   .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                           .pathStyleAccessEnabled(true)
                           .build());
        } else {
            log.info("Configuring S3Client for AWS S3 in region {}", cfg.getRegion());
        }

        return builder.build();
    }

    /**
     * Returns {@code true} when both access key and secret key are non-blank in the configuration.
     */
    private boolean hasStaticCredentials(S3Configuration cfg) {
        return cfg.getAccessKey() != null && !cfg.getAccessKey().isBlank()
                && cfg.getSecretKey() != null && !cfg.getSecretKey().isBlank();
    }

    /**
     * Creates the {@link FileServiceImpl} use-case, injecting outbound port implementations.
     *
     * <p><strong>Dependencies injected:</strong></p>
     * <ul>
     *   <li>{@link StoragePort} — S3StorageAdapter (auto-discovered @Component)</li>
     *   <li>{@link StorageKeyGeneratorPort} — UserScopedKeyGenerator (auto-discovered @Component)</li>
     *   <li>{@link ContentTypeResolverPort} — DefaultContentTypeResolver (auto-discovered @Component)</li>
     *   <li>{@code bucket} — from S3Configuration</li>
     * </ul>
     *
     * <p><strong>Principle:</strong> Use case depends on port interfaces, not concrete adapters.
     * Adapters are auto-discovered by Spring component scan.</p>
     *
     * @param storagePort         the S3StorageAdapter found by component scan
     * @param keyGenerator        the UserScopedKeyGenerator found by component scan
     * @param contentTypeResolver the DefaultContentTypeResolver found by component scan
     * @param cfg                 the profile-specific properties, provides the bucket name
     */
    @Bean
    public FileServicePort fileService(StoragePort storagePort, StorageKeyGeneratorPort keyGenerator,
                                       ContentTypeResolverPort contentTypeResolver, S3Configuration cfg) {
        log.info("FileService configured with bucket={}", cfg.getBucket());
        return new FileServiceImpl(storagePort, keyGenerator, contentTypeResolver, cfg.getBucket());
    }
}

