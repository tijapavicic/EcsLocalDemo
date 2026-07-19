# 📦 EcsLocalDemo — Code Walkthrough

> **A multi-cloud S3 file upload service built with Hexagonal Architecture**  
> Stack: Java 17 · Spring Boot 3.1.7 · AWS SDK v2 · MinIO · Docker · ArchUnit

---

## 🗺️ Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Project Structure](#2-project-structure)
3. [The Request Journey — Step by Step](#3-the-request-journey--step-by-step)
4. [Core Domain — Zero Spring](#4-core-domain--zero-spring)
5. [Inbound Adapter — REST Controller](#5-inbound-adapter--rest-controller)
6. [Outbound Adapter — S3 Storage](#6-outbound-adapter--s3-storage)
7. [Application Wiring — Configuration](#7-application-wiring--configuration)
8. [Profile Switching — Local vs AWS](#8-profile-switching--local-vs-aws)
9. [Architecture Enforcement — ArchUnit](#9-architecture-enforcement--archunit)
10. [Testing Strategy — Three Layers](#10-testing-strategy--three-layers)
11. [Running Locally](#11-running-locally)
12. [Key Design Decisions](#12-key-design-decisions)

---

## 1. Architecture Overview

This project implements **Hexagonal Architecture** (Ports & Adapters). The core idea:

> **The domain knows nothing about the outside world.  
> The outside world talks to the domain through ports (interfaces).**

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│   HTTP Request                                              │
│       │                                                     │
│       ▼                                                     │
│  ┌────────────────────┐                                     │
│  │  FileController    │  ← Inbound Adapter  (REST)         │
│  │  @RestController   │                                     │
│  └────────┬───────────┘                                     │
│           │ calls                                           │
│           ▼                                                 │
│  ┌────────────────────┐                                     │
│  │  FileServicePort   │  ← Inbound Port  (interface)       │
│  └────────┬───────────┘                                     │
│           │ implements                                      │
│           ▼                                                 │
│  ┌────────────────────┐                                     │
│  │  FileServiceImpl   │  ← Core Use Case  (pure Java)      │
│  │  (no Spring!)      │                                     │
│  └────────┬───────────┘                                     │
│           │ calls                                           │
│           ▼                                                 │
│  ┌────────────────────┐                                     │
│  │  StoragePort       │  ← Outbound Port  (interface)      │
│  └────────┬───────────┘                                     │
│           │ implements                                      │
│           ▼                                                 │
│  ┌────────────────────┐                                     │
│  │  S3StorageAdapter  │  ← Outbound Adapter  (AWS SDK)     │
│  │  @Component        │                                     │
│  └────────┬───────────┘                                     │
│           │                                                 │
│           ▼                                                 │
│     AWS S3 / MinIO                                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### ✅ The Golden Rule
```
Adapters NEVER call other Adapters.
All communication flows through Domain Ports (interfaces).
```

---

## 2. Project Structure

```
ecs-local-demo/
│
├── ecs-core/                        # 🧠 Pure domain — ZERO Spring
│   └── src/main/java/com/example/core/
│       ├── domain/
│       │   ├── StorageObject.java   # Value object (Lombok @Value)
│       │   └── StorageException.java
│       ├── ports/
│       │   ├── FileServicePort.java # Inbound port interface
│       │   └── StoragePort.java     # Outbound port interface
│       └── usecases/
│           └── FileServiceImpl.java # Core business logic
│
├── ecs-inbound-adapters/            # 🌐 REST layer
│   └── ...rest/
│       └── FileController.java
│
├── ecs-outbound-adapters/           # 💾 Storage layer
│   └── ...storage/
│       └── S3StorageAdapter.java
│
├── ecs-application/                 # ⚙️  Spring config ONLY
│   └── ...config/
│       ├── StorageConfiguration.java
│       ├── S3Configuration.java
│   └── resources/
│       ├── application.yml
│       ├── application-local-minio.yml
│       └── application-aws.yml
│
└── ecs-tests/                       # 🧪 All test suites
    └── ...tests/
        ├── arch/HexagonalArchitectureTest.java
        ├── unit/FileServiceImplTest.java
        ├── unit/FileControllerTest.java
        └── integration/FileUploadIT.java
```

> **Module dependency rule:** `ecs-application` depends on everything.  
> `ecs-core` depends on nothing. Adapters depend only on `ecs-core`.

---

## 3. The Request Journey — Step by Step

A `POST /api/files/upload` request travels through **5 distinct layers**:

```
Step 1  →  HTTP arrives at FileController
Step 2  →  Controller calls FileServicePort.upload()
Step 3  →  FileServiceImpl generates key, calls StoragePort.store()
Step 4  →  S3StorageAdapter uploads to AWS S3 / MinIO
Step 5  →  StorageObject returned back up the chain → 201 Created
```

Let's trace each step through the actual code.

---

## 4. Core Domain — Zero Spring

### `StorageObject.java` — the domain value object

```java
// ecs-core/domain/StorageObject.java
@Value                          // Lombok: immutable, all-args constructor, getters
public class StorageObject {

    @JsonProperty("key")
    String key;                 // e.g. "2024-01-15/3f4a1b2c-report.pdf"

    @JsonProperty("bucket")
    String bucket;              // e.g. "demo-bucket"

    @JsonProperty("contentType")
    String contentType;         // e.g. "application/pdf"

    @JsonProperty("sizeBytes")
    long sizeBytes;

    @JsonProperty("uploadedAt")
    Instant uploadedAt;         // UTC timestamp
}
```

> **Why `@Value`?** Enforces immutability — value objects should never change after creation.

---

### `FileServicePort.java` — inbound port

```java
// ecs-core/ports/FileServicePort.java
public interface FileServicePort {

    /**
     * Upload a file to cloud storage.
     * @throws StorageException         if the storage provider rejects the upload
     * @throws IllegalArgumentException if filename is blank or content is empty
     */
    StorageObject upload(String filename, String contentType, byte[] content)
            throws StorageException;
}
```

> This interface is the **only thing the REST layer is allowed to know about**.  
> It's defined in `ecs-core` — no Spring, no HTTP, no AWS.

---

### `StoragePort.java` — outbound port

```java
// ecs-core/ports/StoragePort.java
public interface StoragePort {

    /**
     * Persist bytes to the given bucket under the given key.
     */
    StorageObject store(String bucket, String key, String contentType, byte[] content)
            throws StorageException;
}
```

> The S3StorageAdapter implements this. The core domain only talks to this interface —  
> it never imports `S3Client`, `BlobClient`, or any cloud SDK.

---

### `FileServiceImpl.java` — the use case (no Spring annotations!)

```java
// ecs-core/usecases/FileServiceImpl.java
public class FileServiceImpl implements FileServicePort {

    private final StoragePort storagePort;
    private final String bucket;

    // Constructor injection — no @Autowired, no @Service
    public FileServiceImpl(StoragePort storagePort, String bucket) {
        this.storagePort = Objects.requireNonNull(storagePort, "storagePort must not be null");
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        this.bucket = bucket;
    }

    @Override
    public StorageObject upload(String filename, String contentType, byte[] content)
            throws StorageException {
        validateFilename(filename);       // ← guard clause
        validateContent(content);        // ← guard clause

        String key = generateKey(filename);
        return storagePort.store(bucket, key, contentType, content);
    }

    // Generates: "2024-01-15/3f4a1b2c-report.pdf"
    private String generateKey(String filename) {
        String date = LocalDate.now().toString();           // date partition
        String uuid = UUID.randomUUID().toString().substring(0, 8); // collision-free prefix
        return date + "/" + uuid + "-" + filename;
    }
}
```

**Key observations:**
| Feature | Detail |
|---------|--------|
| No `@Service` | Instantiated by Spring config, not component scan |
| No `@Autowired` | Pure constructor injection |
| Validates early | Guard clauses before any I/O |
| Generates key | Date-partitioned for S3 performance + sorting |
| Delegates storage | Calls `StoragePort` — never knows about S3 |

---

## 5. Inbound Adapter — REST Controller

```java
// ecs-inbound-adapters/rest/FileController.java
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileServicePort fileService;  // ← interface, not a concrete class

    public FileController(FileServicePort fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StorageObject> upload(
            @RequestParam("file") MultipartFile file) throws IOException, StorageException {

        log.info("Upload request: name={} size={} contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        // ① Pre-flight validation (HTTP concern)
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // ② Delegate to domain (HTTP → domain call)
        StorageObject stored = fileService.upload(
                file.getOriginalFilename(),
                file.getContentType() != null
                        ? file.getContentType()
                        : MediaType.APPLICATION_OCTET_STREAM_VALUE,
                file.getBytes()
        );

        // ③ Return HTTP response (domain → HTTP)
        return ResponseEntity.status(HttpStatus.CREATED).body(stored);
    }

    // ─── Exception Handlers ────────────────────────────────────

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, String>> handleStorageException(StorageException ex) {
        log.error("Storage failure: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Storage operation failed", "detail", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Validation failure: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid request", "detail", ex.getMessage()));
    }
}
```

**Controller responsibility checklist:**
- ✅ Translate HTTP → domain call
- ✅ Translate domain result → HTTP response
- ✅ Map domain exceptions → HTTP status codes
- ❌ No business logic (zero file key generation, zero validation rules)
- ❌ No direct dependency on S3StorageAdapter

---

## 6. Outbound Adapter — S3 Storage

```java
// ecs-outbound-adapters/storage/S3StorageAdapter.java
@Component                       // ← Spring manages lifecycle
public class S3StorageAdapter implements StoragePort {

    private final S3Client s3Client;

    public S3StorageAdapter(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public StorageObject store(String bucket, String key, String contentType, byte[] content)
            throws StorageException {

        log.debug("Storing object: bucket={} key={} bytes={}", bucket, key, content.length);

        try {
            // ① Build the S3 request
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) content.length)
                    .build();

            // ② Execute upload
            s3Client.putObject(request, RequestBody.fromBytes(content));

            log.info("Stored: s3://{}/{} ({} bytes)", bucket, key, content.length);

            // ③ Return domain object (not AWS SDK response)
            return new StorageObject(key, bucket, contentType, content.length, Instant.now());

        } catch (S3Exception ex) {
            // ④ Translate AWS exception → domain exception
            throw new StorageException(
                    "S3 store failed [bucket=%s key=%s]: %s"
                            .formatted(bucket, key, ex.awsErrorDetails().errorMessage()),
                    ex
            );
        } catch (Exception ex) {
            throw new StorageException(
                    "Unexpected error storing [bucket=%s key=%s]: %s"
                            .formatted(bucket, key, ex.getMessage()),
                    ex
            );
        }
    }
}
```

**Adapter responsibility checklist:**
- ✅ Implements `StoragePort` (outbound port)
- ✅ Translates domain call → AWS SDK call
- ✅ Translates AWS SDK result → domain object (`StorageObject`)
- ✅ Translates `S3Exception` → `StorageException` (no AWS leakage upward)
- ❌ No business logic — no key generation, no validation
- ❌ No direct knowledge of what the REST layer is doing

> **Works for both AWS and MinIO** — same adapter, different `S3Client` configuration.  
> MinIO is 100% S3-compatible, so the code is identical.

---

## 7. Application Wiring — Configuration

This is the **only place** where Spring wires the whole system together:

```java
// ecs-application/config/StorageConfiguration.java
@Configuration
@EnableConfigurationProperties(S3Configuration.class)
public class StorageConfiguration {

    // ① Create the S3Client — MinIO or AWS depending on profile
    @Bean
    public S3Client s3Client(S3Configuration cfg) {
        AwsBasicCredentials credentials =
                AwsBasicCredentials.create(cfg.getAccessKey(), cfg.getSecretKey());

        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(cfg.getRegion()));

        if (cfg.getEndpoint() != null && !cfg.getEndpoint().isBlank()) {
            // MinIO: needs custom endpoint + path-style access
            builder.endpointOverride(URI.create(cfg.getEndpoint()))
                   .serviceConfiguration(S3Configuration.builder()
                           .pathStyleAccessEnabled(true).build());
        }
        // AWS: no endpoint override — SDK discovers s3.region.amazonaws.com automatically

        return builder.build();
    }

    // ② Create the FileService use case, inject the port and bucket name
    @Bean
    public FileServicePort fileService(StoragePort storagePort, S3Configuration cfg) {
        return new FileServiceImpl(storagePort, cfg.getBucket());
        //         ↑ core class      ↑ found via @Component scan
    }
}
```

**Why is all config here?**  
> ArchUnit Rule 4 enforces it: `@Configuration` is forbidden in `ecs-core` and `ecs-adapters`.  
> Having one assembly point makes the wiring explicit and predictable.

---

### `S3Configuration.java` — YAML bridge

```java
@Getter @Setter
@ConfigurationProperties(prefix = "storage.s3")
public class S3Configuration {
    private String bucket;      // storage.s3.bucket
    private String region;      // storage.s3.region
    private String endpoint;    // storage.s3.endpoint  (MinIO only)
    private String accessKey;   // storage.s3.access-key
    private String secretKey;   // storage.s3.secret-key
}
```

Spring binds these fields from whichever `application-{profile}.yml` is active.

---

## 8. Profile Switching — Local vs AWS

### Local MinIO (`application-local-minio.yml`)

```yaml
storage:
  s3:
    bucket: demo-bucket
    region: us-east-1
    endpoint: http://localhost:9000    # ← MinIO endpoint (path-style required)
    access-key: minioadmin
    secret-key: minioadmin
```

```bash
# Start MinIO via Docker Compose, then run:
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.profiles.active=local-minio"
```

### AWS S3 (`application-aws.yml`)

```yaml
storage:
  s3:
    bucket: my-company-uploads
    region: us-east-1
    # endpoint: intentionally absent → SDK uses default AWS endpoints
    access-key: ${AWS_ACCESS_KEY_ID}
    secret-key: ${AWS_SECRET_ACCESS_KEY}
```

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.profiles.active=aws"
```

### How the switch works in code

```java
if (cfg.getEndpoint() != null && !cfg.getEndpoint().isBlank()) {
    // MinIO path: override endpoint + enable path-style
    builder.endpointOverride(URI.create(cfg.getEndpoint()))
           .serviceConfiguration(S3Configuration.builder()
                   .pathStyleAccessEnabled(true).build());
} else {
    // AWS path: SDK auto-discovers s3.<region>.amazonaws.com
}
```

> **One codebase, two environments.** The `S3StorageAdapter` doesn't change at all.

---

## 9. Architecture Enforcement — ArchUnit

ArchUnit tests run on every `mvn test` build and **fail the build** if any rule is violated.

```java
// ecs-tests/arch/HexagonalArchitectureTest.java
@AnalyzeClasses(packages = "com.example")
public class HexagonalArchitectureTest {

    // Rule 1: Core must have ZERO Spring imports
    @ArchTest
    public static final ArchRule core_must_have_no_spring_dependencies =
            noClasses()
                    .that().resideInAPackage("com.example.core..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..", "jakarta.inject..")
                    .because("Core domain must be framework-agnostic (Hexagonal Architecture Rule 1)");

    // Rule 2: Inbound adapters must NOT import outbound adapter classes
    @ArchTest
    public static final ArchRule inbound_adapters_must_not_depend_on_outbound_adapters =
            noClasses()
                    .that().resideInAPackage("com.example.adapters.inbound..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.example.adapters.outbound..")
                    .because("Inbound adapters must call inbound ports, not outbound adapters (Rule 2)");

    // Rule 3: Outbound adapters must implement a StoragePort
    @ArchTest
    public static final ArchRule outbound_adapters_must_implement_ports =
            classes()
                    .that().resideInAPackage("com.example.adapters.outbound..")
                    .and().haveSimpleNameEndingWith("Adapter")
                    .should().implement(com.example.core.ports.StoragePort.class)
                    .because("Outbound adapters must implement domain ports (Rule 3)");

    // Rule 4: @Configuration lives ONLY in the application module
    @ArchTest
    public static final ArchRule configuration_only_in_application_module =
            noClasses()
                    .that().resideInAnyPackage("com.example.adapters..", "com.example.core..")
                    .should().beAnnotatedWith(org.springframework.context.annotation.Configuration.class)
                    .because("Spring @Configuration must live ONLY in the application module (Rule 4)");
}
```

**What happens if you break a rule?**

```
[ERROR] Rule 1 violated: Class <com.example.core.usecases.FileServiceImpl>
        has dependency on <org.springframework.stereotype.Service>
        in (FileServiceImpl.java:8)
        because Core domain must be framework-agnostic

BUILD FAILURE
```

---

## 10. Testing Strategy — Three Layers

### Layer 1 — Unit Tests (No Spring context)

```java
// FileServiceImplTest.java — tests pure domain logic
@DisplayName("FileServiceImpl — unit tests")
class FileServiceImplTest {

    private StoragePort storagePort;
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        storagePort = mock(StoragePort.class);          // ← Mockito mock, not Spring mock
        fileService = new FileServiceImpl(storagePort, "test-bucket");
    }

    @Test
    @DisplayName("upload() — key contains today's date and original filename")
    void upload_generatedKey_containsDateAndFilename() throws StorageException {
        when(storagePort.store(any(), any(), any(), any()))
                .thenAnswer(inv -> new StorageObject(
                        inv.getArgument(1), "test-bucket", "text/plain", 11, Instant.now()));

        fileService.upload("hello.txt", "text/plain", "hello world".getBytes());

        var captor = ArgumentCaptor.forClass(String.class);
        verify(storagePort).store(eq("test-bucket"), captor.capture(), any(), any());

        // Assert key pattern: "2024-01-15/3f4a1b2c-hello.txt"
        assertThat(captor.getValue())
                .contains("hello.txt")
                .matches("\\d{4}-\\d{2}-\\d{2}/.+");
    }

    @Test
    @DisplayName("upload() — throws IllegalArgumentException when content is empty")
    void upload_throwsIllegalArgument_whenContentIsEmpty() {
        assertThatThrownBy(() -> fileService.upload("file.txt", "text/plain", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");
    }

    @Test
    @DisplayName("constructor — throws NullPointerException when storagePort is null")
    void constructor_throwsNPE_whenStoragePortIsNull() {
        assertThatThrownBy(() -> new FileServiceImpl(null, "bucket"))
                .isInstanceOf(NullPointerException.class);
    }
}
```

> **No Spring, no network, no Docker.** Tests complete in ~100ms.

---

### Layer 2 — Controller Unit Tests (Standalone MockMvc)

```java
// FileControllerTest.java — tests HTTP mapping without full Spring context
@DisplayName("FileController — unit tests (standalone MockMvc)")
class FileControllerTest {

    private MockMvc mockMvc;
    private FileServicePort fileService;

    @BeforeEach
    void setUp() {
        fileService = mock(FileServicePort.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FileController(fileService))
                .build();
    }

    @Test
    @DisplayName("POST /api/files/upload — returns 201 with StorageObject JSON")
    void upload_returns201_withStorageObjectBody() throws Exception {
        StorageObject stored = new StorageObject(
                "2024-01-15/abc-report.pdf", "demo-bucket",
                "application/pdf", 1024L, Instant.now());

        when(fileService.upload(any(), any(), any())).thenReturn(stored);

        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "PDF content".getBytes());

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("2024-01-15/abc-report.pdf"))
                .andExpect(jsonPath("$.bucket").value("demo-bucket"))
                .andExpect(jsonPath("$.sizeBytes").value(1024));
    }

    @Test
    @DisplayName("POST /api/files/upload — returns 500 when StorageException is thrown")
    void upload_returns500_whenStorageExceptionThrown() throws Exception {
        when(fileService.upload(any(), any(), any()))
                .thenThrow(new StorageException("MinIO unreachable"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "file.txt", MediaType.TEXT_PLAIN_VALUE, "data".getBytes());

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Storage operation failed"));
    }
}
```

> **No Spring Application Context started.** MockMvc in standalone mode tests HTTP layer only.

---

### Layer 3 — Integration Test (Real MinIO via TestContainers)

```java
// FileUploadIT.java — full end-to-end with real MinIO
@Testcontainers
@SpringBootTest(classes = Application.class,
                webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local-minio")
class FileUploadIT {

    // ① TestContainers starts a real MinIO Docker container
    @Container
    static GenericContainer<?> minio =
            new GenericContainer<>("minio/minio:RELEASE.2023-09-30T07-02-29Z")
                    .withEnv("MINIO_ROOT_USER", "minioadmin")
                    .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
                    .withCommand("server /data --console-address :9001")
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/live")
                            .withStartupTimeout(Duration.ofSeconds(60)));

    // ② Inject container's dynamic port into Spring context before startup
    @DynamicPropertySource
    static void overrideEndpoint(DynamicPropertyRegistry registry) {
        registry.add("storage.s3.endpoint",
                () -> "http://localhost:" + minio.getMappedPort(9000));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("end-to-end: file persisted in MinIO, 201 returned")
    void upload_endToEnd_persistsFileInMinio_returns201() {
        byte[] content = "Integration test content".getBytes();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override public String getFilename() { return "integration-test.txt"; }
        });

        ResponseEntity<StorageObject> response = restTemplate.postForEntity(
                "/api/files/upload",
                new HttpEntity<>(body, new HttpHeaders()),
                StorageObject.class
        );

        // ③ Assert the full response — real file stored in real MinIO
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getBucket()).isEqualTo("demo-bucket");
        assertThat(response.getBody().getKey()).contains("integration-test.txt");
        assertThat(response.getBody().getSizeBytes()).isEqualTo(content.length);
    }
}
```

**What TestContainers does:**
```
① Pull minio/minio Docker image
② Start container on a random port (e.g. 32768)
③ Wait for /minio/health/live to respond
④ Inject "storage.s3.endpoint=http://localhost:32768" via @DynamicPropertySource
⑤ Spring Boot starts with real S3Client pointing at MinIO
⑥ Test runs against real storage
⑦ Container torn down after test
```

---

## 11. Running Locally

### Prerequisites

```bash
java -version   # Need 17+
mvn -version    # Need 3.9+
docker -version # For MinIO
```

### Start MinIO

```bash
docker-compose up -d

# MinIO API:     http://localhost:9000
# MinIO Console: http://localhost:9001  (user: minioadmin / minioadmin)
```

### Run the App

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--spring.profiles.active=local-minio"
```

### Upload a File

```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@./pom.xml" \
  -H "Accept: application/json"
```

**Response:**
```json
{
  "key": "2024-01-15/3f4a1b2c-pom.xml",
  "bucket": "demo-bucket",
  "contentType": "application/xml",
  "sizeBytes": 2847,
  "uploadedAt": "2024-01-15T10:30:45Z"
}
```

### Run All Tests

```bash
# Unit + architecture tests (no Docker required)
mvn test -pl ecs-core,ecs-inbound-adapters,ecs-outbound-adapters,ecs-tests \
  -Dtest="FileServiceImplTest,FileControllerTest,HexagonalArchitectureTest"

# Full suite including integration (Docker required)
mvn verify
```

---

## 12. Key Design Decisions

### ① Why Hexagonal Architecture?

| Problem | Without Hexagonal | With Hexagonal |
|---------|------------------|----------------|
| Switch from AWS to Azure | Rewrite domain + adapters | Create new adapter, keep domain unchanged |
| Test business logic | Need Spring + real AWS | Mock port, pure JUnit |
| Add gRPC endpoint | Couple with REST code | New inbound adapter, same domain ports |
| Enforce boundaries | Code review hope | ArchUnit enforces at build time |

---

### ② Why no `@Service` on `FileServiceImpl`?

```java
// ❌ What most people would write:
@Service
public class FileServiceImpl implements FileServicePort { }

// ✅ What this project does:
public class FileServiceImpl implements FileServicePort { }
// Instantiated explicitly in StorageConfiguration via @Bean
```

**Reason:** If `@Service` is added to `ecs-core`, the ArchUnit enforcer catches it and the **build fails**.  
This makes the architecture boundary machine-enforceable, not just a guideline.

---

### ③ Why `StoragePort` takes `bucket` as a parameter?

```java
// StoragePort
StorageObject store(String bucket, String key, String contentType, byte[] content);
```

The bucket name comes from YAML config, not hardcoded in the adapter.  
`FileServiceImpl` holds the bucket, the adapter just executes the command.  
This means one `S3StorageAdapter` can serve multiple buckets without subclassing.

---

### ④ Why are tests in a separate `ecs-tests` module?

Integration tests need ALL modules (core + adapters + application) on the classpath.  
Putting them in their own module with all modules as test-scoped dependencies makes this clean.

```xml
<!-- ecs-tests/pom.xml -->
<dependency><artifactId>ecs-core</artifactId><scope>test</scope></dependency>
<dependency><artifactId>ecs-inbound-adapters</artifactId><scope>test</scope></dependency>
<dependency><artifactId>ecs-outbound-adapters</artifactId><scope>test</scope></dependency>
<dependency><artifactId>ecs-application</artifactId><scope>test</scope></dependency>
```

---

### ⑤ Why `@DynamicPropertySource` in integration tests?

```java
@DynamicPropertySource
static void overrideEndpoint(DynamicPropertyRegistry registry) {
    registry.add("storage.s3.endpoint",
            () -> "http://localhost:" + minio.getMappedPort(9000));
}
```

TestContainers assigns a **random host port** at runtime (prevents port conflicts in CI).  
`@DynamicPropertySource` runs **before** Spring Boot starts, injecting the real container URL.  
This is cleaner than `@TestPropertySource` because the port isn't known until runtime.

---

## 📋 Quick Reference

| Component | Location | Role |
|-----------|----------|------|
| `StorageObject` | `ecs-core/domain` | Immutable result value object |
| `StorageException` | `ecs-core/domain` | Checked domain exception |
| `FileServicePort` | `ecs-core/ports` | Inbound port (REST → domain) |
| `StoragePort` | `ecs-core/ports` | Outbound port (domain → storage) |
| `FileServiceImpl` | `ecs-core/usecases` | Core use case, no Spring |
| `FileController` | `ecs-inbound-adapters` | REST endpoint, delegates to port |
| `S3StorageAdapter` | `ecs-outbound-adapters` | AWS SDK, implements StoragePort |
| `StorageConfiguration` | `ecs-application/config` | Spring bean wiring, ONLY here |
| `S3Configuration` | `ecs-application/config` | YAML → Java config binding |
| `HexagonalArchitectureTest` | `ecs-tests/arch` | ArchUnit rule enforcement |
| `FileServiceImplTest` | `ecs-tests/unit` | Pure unit tests, no Spring |
| `FileControllerTest` | `ecs-tests/unit` | MockMvc standalone tests |
| `FileUploadIT` | `ecs-tests/integration` | TestContainers end-to-end |

---

*Generated: July 21, 2026 · EcsLocalDemo v1.0.0 · Hexagonal Architecture*

