package com.example.tests.integration;

import com.example.application.Application;
import com.example.core.domain.StorageObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: real HTTP → Spring Boot → S3StorageAdapter → real MinIO (TestContainers).
 *
 * <p>TestContainers starts a real MinIO container. {@code @DynamicPropertySource}
 * overrides the S3 endpoint with the dynamic container port before Spring starts.</p>
 */
@Testcontainers
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local-minio")
@DisplayName("FileController — integration test with MinIO (TestContainers)")
class FileUploadIT {

    private static final String BUCKET = "demo-bucket";

    @Container
    static GenericContainer<?> minio = new GenericContainer<>("minio/minio:RELEASE.2023-09-30T07-02-29Z")
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server /data --console-address :9001")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live")
                    .forPort(9000)
                    .withStartupTimeout(Duration.ofSeconds(60)));

    @DynamicPropertySource
    static void overrideEndpoint(DynamicPropertyRegistry registry) {
        registry.add("storage.s3.endpoint",
                () -> "http://localhost:" + minio.getMappedPort(9000));
    }

    @BeforeAll
    static void createBucket() {
        S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create("http://localhost:" + minio.getMappedPort(9000)))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("minioadmin", "minioadmin")))
                .region(Region.US_EAST_1)
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true).build())
                .build();

        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (Exception ignored) {
            // bucket may already exist on re-runs
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("POST /api/files/upload — end-to-end: file persisted in MinIO, 201 returned")
    void upload_endToEnd_persistsFileInMinio_returns201() {
        byte[] content = "Integration test content".getBytes();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "integration-test.txt";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<StorageObject> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body, headers),
                StorageObject.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getBucket()).isEqualTo(BUCKET);
        assertThat(response.getBody().getKey()).contains("integration-test.txt");
        assertThat(response.getBody().getSizeBytes()).isEqualTo(content.length);
    }

    @Test
    @DisplayName("POST /api/files/upload — returns 400 when file is empty")
    void upload_returns400_whenFileIsEmpty() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(new byte[0]) {
            @Override
            public String getFilename() {
                return "empty.txt";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}

