package com.example.application.integration;

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
import org.springframework.context.annotation.Import;
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
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for IDM (Identity and Access Management) with Bearer token authentication.
 *
 * <p>End-to-end testing: real HTTP + Bearer tokens → Spring Boot → S3StorageAdapter → real MinIO (TestContainers)</p>
 *
 * <p>Tests:</p>
 * <ul>
 *   <li>Bearer token authentication with valid tokens</li>
 *   <li>User isolation (different users have separate folders)</li>
 *   <li>userId included in response for audit trail</li>
 *   <li>User-scoped file paths (users/{userId}/...)</li>
 *   <li>Error cases (missing header, invalid token, malformed token)</li>
 * </ul>
 *
 * <p>Lives in {@code ecs-application} (not ecs-tests) because it requires the full Spring Boot
 * application context with real MinIO container.</p>
 */
@Testcontainers
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local-minio")
@Import(SecurityTestConfiguration.class)
@DisplayName("FileController — IDM integration tests with Bearer tokens (TestContainers)")
class FileUploadWithIDMIT {

    private static final String BUCKET = "demo-bucket";
    private static final String USER1_ID = "alice";
    private static final String USER1_EMAIL = "alice@example.com";
    private static final String USER2_ID = "bob";
    private static final String USER2_EMAIL = "bob@example.com";
    private static final String USER3_ID = "charlie";
    private static final String USER3_EMAIL = "charlie@example.com";

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

    // ──────────────────────────────────────────────────────────────────────────
    // ✅ SUCCESS CASES
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("✅ Upload with valid Bearer token — returns 201 with userId")
    void upload_withValidBearerToken_returns201WithUserId() {
        // Arrange: Create Bearer token for user1
        String token = createBearerToken(USER1_ID, USER1_EMAIL);
        byte[] content = "Test file content for user1".getBytes();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "document.pdf";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer " + token);

        // Act
        ResponseEntity<StorageObject> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body, headers),
                StorageObject.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getBucket()).isEqualTo(BUCKET);
        assertThat(response.getBody().getKey()).contains("document.pdf");
        assertThat(response.getBody().getSizeBytes()).isEqualTo(content.length);
        assertThat(response.getBody().getUserId()).isEqualTo(USER1_ID); // ✅ userId in response
        assertThat(response.getBody().getKey()).startsWith("users/alice/"); // ✅ user-scoped path
    }

    @Test
    @DisplayName("✅ Upload as User 2 — returns 201 with different userId and path")
    void upload_asUser2_returns201WithDifferentUserIdAndPath() {
        // Arrange: Create Bearer token for user2
        String token = createBearerToken(USER2_ID, USER2_EMAIL);
        byte[] content = "Test file content for user2".getBytes();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "report.xlsx";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer " + token);

        // Act
        ResponseEntity<StorageObject> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body, headers),
                StorageObject.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUserId()).isEqualTo(USER2_ID); // ✅ user2
        assertThat(response.getBody().getKey()).startsWith("users/bob/"); // ✅ different path
        assertThat(response.getBody().getKey()).contains("report.xlsx");
    }

    @Test
    @DisplayName("✅ User isolation — two users upload, files in separate folders")
    void userIsolation_twoUsersUpload_filesInSeparateFolders() {
        // Arrange: Upload as user1
        String token1 = createBearerToken(USER1_ID, USER1_EMAIL);
        byte[] content1 = "File from user1".getBytes();

        MultiValueMap<String, Object> body1 = new LinkedMultiValueMap<>();
        body1.add("file", new ByteArrayResource(content1) {
            @Override
            public String getFilename() {
                return "user1_file.txt";
            }
        });

        HttpHeaders headers1 = new HttpHeaders();
        headers1.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers1.set("Authorization", "Bearer " + token1);

        // Act: Upload as user1
        ResponseEntity<StorageObject> response1 = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body1, headers1),
                StorageObject.class
        );

        // Arrange: Upload as user2
        String token2 = createBearerToken(USER2_ID, USER2_EMAIL);
        byte[] content2 = "File from user2".getBytes();

        MultiValueMap<String, Object> body2 = new LinkedMultiValueMap<>();
        body2.add("file", new ByteArrayResource(content2) {
            @Override
            public String getFilename() {
                return "user2_file.txt";
            }
        });

        HttpHeaders headers2 = new HttpHeaders();
        headers2.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers2.set("Authorization", "Bearer " + token2);

        // Act: Upload as user2
        ResponseEntity<StorageObject> response2 = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body2, headers2),
                StorageObject.class
        );

        // Assert: Both uploads successful
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Assert: Files in separate folders
        assertThat(response1.getBody().getKey()).startsWith("users/alice/");
        assertThat(response2.getBody().getKey()).startsWith("users/bob/");
        assertThat(response1.getBody().getUserId()).isEqualTo(USER1_ID);
        assertThat(response2.getBody().getUserId()).isEqualTo(USER2_ID);

        // Assert: Different paths
        assertThat(response1.getBody().getKey()).isNotEqualTo(response2.getBody().getKey());
    }

    @Test
    @DisplayName("✅ Multiple uploads from same user — all in same user folder")
    void multipleUploadsFromSameUser_allInSameUserFolder() {
        String token = createBearerToken(USER1_ID, USER1_EMAIL);

        // Upload file 1
        StorageObject result1 = uploadFile(token, "file1.txt", "Content 1".getBytes());

        // Upload file 2
        StorageObject result2 = uploadFile(token, "file2.pdf", "Content 2".getBytes());

        // Upload file 3
        StorageObject result3 = uploadFile(token, "file3.xlsx", "Content 3".getBytes());

        // Assert: All in same user folder
        assertThat(result1.getUserId()).isEqualTo(USER1_ID);
        assertThat(result2.getUserId()).isEqualTo(USER1_ID);
        assertThat(result3.getUserId()).isEqualTo(USER1_ID);

        assertThat(result1.getKey()).startsWith("users/alice/");
        assertThat(result2.getKey()).startsWith("users/alice/");
        assertThat(result3.getKey()).startsWith("users/alice/");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ❌ ERROR CASES
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("❌ Missing Authorization header — returns 400")
    void upload_missingAuthorizationHeader_returns400() {
        byte[] content = "Test content".getBytes();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "file.txt";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        // NO Authorization header

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("❌ Invalid Bearer token (not Base64) — returns 400")
    void upload_invalidBearerToken_returns400() {
        byte[] content = "Test content".getBytes();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "file.txt";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer invalid_not_base64_!!!"); // Invalid Base64

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("❌ Malformed token (no colon separator) — returns 400")
    void upload_malformedToken_returns400() {
        // Base64(justauserwithoutcolon) - missing the colon
        String malformedToken = Base64.getEncoder().encodeToString("justauserwithoutcolon".getBytes());

        byte[] content = "Test content".getBytes();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "file.txt";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer " + malformedToken);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("❌ Empty Bearer token — returns 400")
    void upload_emptyBearerToken_returns400() {
        byte[] content = "Test content".getBytes();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "file.txt";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer "); // Empty token

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("❌ Wrong Authorization prefix (Basic instead of Bearer) — returns 400")
    void upload_wrongAuthorizationPrefix_returns400() {
        String token = createBearerToken(USER1_ID, USER1_EMAIL);

        byte[] content = "Test content".getBytes();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "file.txt";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Basic " + token); // Uses "Basic" instead of "Bearer"

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("❌ Valid token but empty file — returns 400")
    void upload_validTokenButEmptyFile_returns400() {
        String token = createBearerToken(USER1_ID, USER1_EMAIL);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(new byte[0]) {
            @Override
            public String getFilename() {
                return "empty.txt";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer " + token);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("❌ Valid token but no file parameter — returns 400")
    void upload_validTokenButNoFileParameter_returns400() {
        String token = createBearerToken(USER1_ID, USER1_EMAIL);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // NO file added

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer " + token);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Create a Bearer token in the format: Base64(userId:email)
     */
    private String createBearerToken(String userId, String email) {
        String raw = userId + ":" + email;
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }

    /**
     * Helper to upload a file with Bearer token.
     */
    private StorageObject uploadFile(String bearerToken, String filename, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("Authorization", "Bearer " + bearerToken);

        ResponseEntity<StorageObject> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body, headers),
                StorageObject.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        return response.getBody();
    }
}

