package com.example.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reads {@code storage.s3.*} properties from the active profile YAML.
 *
 * <p>Profile {@code local-minio} → reads {@code application-local-minio.yml}<br>
 * Profile {@code aws}         → reads {@code application-aws.yml}</p>
 *
 * <p>This class is the bridge between YAML configuration and Java.
 * It is injected into {@link StorageConfiguration} to create the {@code S3Client} bean
 * and into {@link com.example.core.usecases.FileServiceImpl} to know which bucket to use.</p>
 *
 * <p>Registered via {@code @EnableConfigurationProperties} in {@link StorageConfiguration}.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "storage.s3")
public class S3Configuration {

    /**
     * Target bucket name.
     * <ul>
     *   <li>MinIO: {@code demo-bucket}</li>
     *   <li>AWS:   {@code my-company-uploads}</li>
     * </ul>
     */
    private String bucket;

    /**
     * AWS region code, e.g. {@code us-east-1}, {@code eu-west-1}.
     * MinIO accepts any value here — {@code us-east-1} is conventional.
     */
    private String region;

    /**
     * Custom endpoint URL. <strong>Set only for MinIO</strong>.
     * Leave blank (or omit) for real AWS — the SDK discovers the endpoint automatically.
     *
     * <ul>
     *   <li>Local MinIO: {@code http://localhost:9000}</li>
     *   <li>Docker MinIO: {@code http://minio:9000}</li>
     *   <li>AWS: <em>not set</em></li>
     * </ul>
     */
    private String endpoint;

    /** Access key / MinIO root user. For AWS, prefer IAM roles over static keys. */
    private String accessKey;

    /** Secret key / MinIO root password. */
    private String secretKey;
}

