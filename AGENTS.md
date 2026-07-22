# AGENTS.md — ECS Local Demo

**Project Type:** Multi-cloud S3 file upload service  
**Architecture:** Hexagonal (Ports & Adapters)  
**Stack:** Java 21 · Spring Boot 3.1.7 · Maven · AWS SDK v2 · TestContainers · ArchUnit  
**Enforcement:** STRICT — ArchUnit tests block builds on violations

---

## 🏗️ Core Architecture Pattern

This project implements **Hexagonal Architecture** with strict separation:

```
HTTP Request
    ↓
[FileController] (inbound adapter)
    ↓
FileServicePort (inbound port interface)
    ↓
[FileServiceImpl] (use case — core domain, NO Spring)
    ↓
StoragePort (outbound port interface)
    ↓
[S3StorageAdapter] (outbound adapter — implements StoragePort)
    ↓
[S3Client] (AWS SDK v2)
```

**Critical Rule:** Adapters NEVER call adapters. All communication flows through domain ports (interfaces).

---

## 📦 Module Responsibilities

### `ecs-core/` — Pure Domain Logic
- ✅ Contains ports (interfaces), use cases, domain objects
- ✅ Constructor injection ONLY — no setters
- ❌ ZERO Spring imports (enforced by ArchUnit)
- ❌ NO @Component, @Service, @Repository, @Autowired

**Example:** `FileServiceImpl` orchestrates uploads by calling `StoragePort.store()`. No Spring, no external dependencies.

### `ecs-inbound-adapters/` — REST / Messaging
- ✅ Inject inbound ports (interfaces), never adapters
- ✅ Translate HTTP/events to domain calls
- ✅ Exception handlers convert domain exceptions to HTTP responses
- ❌ NO business logic (that's in core)
- ❌ NO direct calls to outbound adapters

**Example:** `FileController` receives multipart files, calls `FileServicePort.upload()`, returns `201 Created`.

### `ecs-outbound-adapters/` — Storage / External APIs
- ✅ Implement outbound port interfaces (e.g., `StoragePort`)
- ✅ Use @Component for auto-registration
- ✅ Translate domain objects to/from SDK calls
- ❌ NO business logic
- ❌ NO adapter-to-adapter calls

**Example:** `S3StorageAdapter implements StoragePort` — wraps AWS SDK v2 calls, catches `S3Exception`, converts to domain `StorageException`.

### `ecs-application/` — Configuration + Integration Tests
- ✅ Define ALL @Configuration, @Bean definitions
- ✅ Profile-based conditional beans
- ✅ Wire adapters → ports → use cases
- ✅ **Integration tests** that require full Spring context (TestContainers)
- ❌ NO controllers, NO repositories, NO business logic (except configuration)

**Example:** `StorageConfiguration` creates `S3Client` (MinIO vs AWS), instantiates `FileServiceImpl` with bucket name and `StoragePort` implementation.

### `ecs-tests/` — Unit & Architecture Tests (NO Spring Context)
- ✅ `HexagonalArchitectureTest` enforces 4 rules on every build
- ✅ Unit tests mock ports (no Spring context, fast feedback)
- ✅ Adapter tests use MockMvc standalone (no full context)
- ❌ NO integration tests (those belong in ecs-application)

---

## 🔄 Data Flow Example: File Upload

1. **Client sends:** `POST /api/files/upload` (multipart/form-data)
2. **Spring routes to:** `FileController.upload(MultipartFile file)`
3. **Controller extracts:** filename, contentType, bytes
4. **Controller calls:** `fileService.upload(filename, contentType, content)`
5. **FileServiceImpl validates:** filename (not blank), content (not empty)
6. **FileServiceImpl generates:** collision-free key `2024-01-15/uuid-filename.pdf`
7. **FileServiceImpl calls:** `storagePort.store(bucket, key, contentType, content)`
8. **S3StorageAdapter (MinIO/AWS):** builds `PutObjectRequest`, uploads via `S3Client.putObject()`
9. **Returns:** `StorageObject` with metadata (key, bucket, contentType, size, timestamp)
10. **Controller returns:** `201 Created` with JSON body

---

## 🎯 Profile-Based Configuration

The `S3Configuration` class binds `storage.s3.*` YAML properties. Profile determines whether MinIO or AWS is used:

### Local Development (MinIO)
**Profile:** `local-minio` → **File:** `application-local-minio.yml`  
```yaml
storage:
  s3:
    bucket: demo-bucket
    region: us-east-1
    endpoint: http://localhost:9000          # ← MinIO requires custom endpoint
    accessKey: minioadmin
    secretKey: minioadmin
```

**S3Client Configuration:**
- Endpoint override: `http://localhost:9000` (MinIO runs here)
- Path-style access ENABLED: bucket appears in URL path (`/bucket/key`), not subdomain
- Credentials: Static keys (dev-only)

### AWS Production
**Profile:** `aws` → **File:** `application-aws.yml`  
```yaml
storage:
  s3:
    bucket: my-company-uploads
    region: us-east-1
    # endpoint: NOT SET — SDK uses default AWS endpoints
    accessKey: <not recommended — use IAM role>
    secretKey: <not recommended — use IAM role>
```

**S3Client Configuration:**
- Endpoint: Default AWS endpoints (e.g., `s3.us-east-1.amazonaws.com`)
- Path-style access DISABLED: bucket in subdomain (`s3.bucket.amazonaws.com`)
- Credentials: IAM role (preferred) or static keys

**How switching works in `StorageConfiguration`:**
```java
if (cfg.getEndpoint() != null && !cfg.getEndpoint().isBlank()) {
  // MinIO: custom endpoint + path-style
  builder.endpointOverride(URI.create(cfg.getEndpoint()))
         .serviceConfiguration(S3Configuration.builder()
           .pathStyleAccessEnabled(true).build());
} else {
  // AWS: standard SDK behavior
}
```

**How to test each profile:**
```bash
# MinIO (default local development)
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local-minio"

# AWS (requires valid AWS credentials)
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=aws"
```

---

## ✅ ArchUnit Enforcement (4 Rules)

Tested by `HexagonalArchitectureTest` — **build fails if any rule breaks**:

| Rule | Enforces | Details |
|------|----------|---------|
| **Rule 1** | Core has ZERO Spring imports | Core packages cannot depend on `org.springframework.*` or `jakarta.inject.*`. Adding `@Service` to core breaks build. |
| **Rule 2** | Inbound adapters don't call outbound adapters | Inbound packages cannot import outbound adapter classes. Communication must flow through domain ports. |
| **Rule 3** | Outbound adapters implement port interfaces | All classes ending with `Adapter` in outbound packages must implement `StoragePort`. Direct adapter calls are impossible. |
| **Rule 4** | @Configuration ONLY in application module | Only the application module may bear `@Configuration` annotation. Adapters and core are configuration-free. |

---

## 🧪 Testing Patterns

### Layer 1: Domain Unit Tests (No Spring)
```java
@DisplayName("FileServiceImpl — unit tests")
class FileServiceImplTest {
  private static final String BUCKET = "test-bucket";
  private static final byte[] CONTENT = "hello world".getBytes();
  
  private StoragePort storagePort;
  private FileServiceImpl fileService;
  
  @BeforeEach
  void setUp() {
    storagePort = mock(StoragePort.class);
    fileService = new FileServiceImpl(storagePort, BUCKET);
  }
  
  @Test
  @DisplayName("upload() — generates date-partitioned key with filename")
  void upload_generatedKey_containsDateAndFilename() throws StorageException {
    when(storagePort.store(any(), any(), any(), any()))
      .thenAnswer(inv -> new StorageObject(inv.getArgument(1), BUCKET, "text/plain", CONTENT.length, Instant.now()));
    
    fileService.upload("report.pdf", "application/pdf", CONTENT);
    
    var captor = ArgumentCaptor.forClass(String.class);
    verify(storagePort).store(eq(BUCKET), captor.capture(), any(), any());
    
    assertThat(captor.getValue()).matches("\\d{4}-\\d{2}-\\d{2}/.+-report\\.pdf");
  }
  
  @Test
  @DisplayName("upload() — throws IllegalArgumentException when content is empty")
  void upload_throwsIllegalArgument_whenContentIsEmpty() {
    assertThatThrownBy(() -> fileService.upload("file.txt", "text/plain", new byte[0]))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("content");
  }
}
```

**Key:** Mock the outbound port, test use case in isolation. No Spring context. Use AssertJ assertions and ArgumentCaptor.

### Layer 2: Adapter Tests (MockMvc Standalone — No Spring Context)
```java
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
      .andExpect(jsonPath("$.bucket").value("demo-bucket"));
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

**Key:** Standalone `MockMvcBuilders.standaloneSetup()` — NO Spring context needed. Tests HTTP serialization, deserialization, and exception mapping. Faster than integration tests; covers adapter concerns (multipart parsing, JSON responses, HTTP status codes).

### Layer 3: Integration Tests (Real MinIO + Full Spring)

**Location:** `ecs-application/src/test/` — Integration tests live with the application module, not in ecs-tests. This follows clean hexagonal architecture: tests that need the full Spring context belong where Spring starts.

```java
@Testcontainers
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local-minio")
class FileUploadIT {
  @Container
  static GenericContainer<?> minio = new GenericContainer<>("minio/minio:RELEASE.2023-09-30T07-02-29Z")
    .withEnv("MINIO_ROOT_USER", "minioadmin")
    .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
    .withExposedPorts(9000)
    .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));
  
  @DynamicPropertySource
  static void overrideEndpoint(DynamicPropertyRegistry registry) {
    registry.add("storage.s3.endpoint", () -> "http://localhost:" + minio.getMappedPort(9000));
  }
  
  @Autowired
  private TestRestTemplate restTemplate;
  
  @Test
  void upload_endToEnd_persistsFileInMinio_returns201() {
    byte[] content = "test data".getBytes();
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", new ByteArrayResource(content) {
      @Override public String getFilename() { return "test.txt"; }
    });
    
    ResponseEntity<StorageObject> response = restTemplate.postForEntity(
      "/api/files/upload",
      new HttpEntity<>(body, new HttpHeaders()),
      StorageObject.class
    );
    
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().getKey()).contains("test.txt");
  }
}
```

**Key:** TestContainers spins up real MinIO. `@DynamicPropertySource` injects container endpoint. Full Spring context. Tests real HTTP → domain → adapter → storage flow.

---

### Test Strategy Summary

| Layer | Tool | Spring Context | External Services | Speed | Purpose | Location |
|-------|------|----|----|--------|---------|----------|
| **Domain** | JUnit + Mockito | ❌ No | ❌ No | ⚡⚡⚡ Fast | Test use case logic in isolation | `ecs-tests/unit/` |
| **Adapter** | MockMvc Standalone | ❌ No | ❌ No | ⚡⚡ Fast | Test HTTP layer (serialization, exception mapping, status codes) | `ecs-tests/unit/` |
| **Integration** | TestContainers + Spring | ✅ Yes | ✅ Yes | 🐢 Slow | Test end-to-end: HTTP → domain → storage | **`ecs-application/integration/`** |

---

### Why Integration Tests Live in ecs-application

**Architectural Principle:** Tests should live where the context they test is defined.

- **Unit tests** (mocked, no Spring) → `ecs-tests/` ✅
- **Adapter tests** (MockMvc standalone, no full context) → `ecs-tests/` ✅
- **Integration tests** (require full Spring Boot context) → `ecs-application/` ✅

This creates a **clean dependency flow**:
```
ecs-core (no dependencies except domain logic)
  ↑
ecs-inbound-adapters (depends on ecs-core)
  ↑
ecs-outbound-adapters (depends on ecs-core)
  ↑
ecs-application (depends on all adapters, wires everything, owns integration tests)
  ↑
ecs-tests (depends on all modules, runs unit/architecture tests)
```

**Benefits of this separation:**
1. **ecs-tests** has minimal dependencies (no TestContainers) → faster builds
2. **ecs-application** owns integration tests because it owns the full context
3. **Clean POM structure** — no cross-dependencies between test modules
4. **Hexagonal principle** — application module is the outermost layer, so integration tests belong there

---

## 🛠️ Common Developer Tasks

### Add a New Feature (Example: Download)
1. **Define inbound port:** `FileServicePort.download(String key): byte[]`
2. **Define outbound port:** `StoragePort.retrieve(String bucket, String key): byte[]`
3. **Implement use case:** `FileServiceImpl.download()` (core)
4. **Implement adapter:** `S3StorageAdapter.retrieve()` (outbound)
5. **Add REST endpoint:** `FileController.download()` (inbound)
6. **Wire in config:** `StorageConfiguration` (if new bean needed)
7. **Test:** Unit test use case, integration test adapter
8. **Run:** `mvn test` (ArchUnit + all tests)

### Switch Storage Provider (Example: Azure Blob)
1. **Keep ports unchanged** — domain logic reusable
2. **Create new adapter:** `AzureBlobStorageAdapter implements StoragePort`
3. **Add to configuration:** Conditional bean in `StorageConfiguration`
4. **New profile:** `application-azure.yml` with Azure connection strings
5. **Run:** `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=azure"`

---

## 🔧 Build & Run

### Prerequisites
```bash
# Verify tools are available
java --version          # Must be Java 21+
mvn --version          # Must be Maven 3.9+
docker --version       # For MinIO container
docker-compose --version
```

### Build
```bash
# Full clean build with tests
mvn clean package

# Skip tests for faster builds
mvn clean package -DskipTests

# Run specific test class
mvn test -Dtest=HexagonalArchitectureTest
mvn test -Dtest=FileServiceImplTest
mvn test -Dtest=FileUploadIT
```

### Run Locally (MinIO)
```bash
# Start MinIO via docker-compose (creates demo-bucket automatically)
docker-compose up -d

# Run Spring Boot with local-minio profile
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local-minio"

# Application starts on http://localhost:8080
# MinIO web console: http://localhost:9001 (minioadmin / minioadmin)
```

### Test Upload
```bash
# Upload a file
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@./pom.xml" \
  -H "Accept: application/json"

# Response: 201 Created
# {
#   "key": "2024-01-15/a1b2c3d4-pom.xml",
#   "bucket": "demo-bucket",
#   "contentType": "application/xml",
#   "sizeBytes": 2847,
#   "uploadedAt": "2024-01-15T10:30:45Z"
# }
```

### Run All Tests
```bash
# Unit + integration + architecture tests
mvn verify

# Only architecture tests (fails if rules broken)
mvn test -Dtest=HexagonalArchitectureTest
```

### Cleanup
```bash
# Stop MinIO
docker-compose down

# Clean build artifacts
mvn clean
```

---

## 📋 Dependency Injection Pattern

**S3Configuration** — bridge between YAML and Java:
```java
@Getter @Setter
@ConfigurationProperties(prefix = "storage.s3")
public class S3Configuration {
  private String bucket;      // From YAML: storage.s3.bucket
  private String region;      // From YAML: storage.s3.region
  private String endpoint;    // From YAML: storage.s3.endpoint (MinIO only)
  private String accessKey;   // From YAML: storage.s3.accessKey
  private String secretKey;   // From YAML: storage.s3.secretKey
}
```

**Core uses constructor injection (no Spring):**
```java
public FileServiceImpl(StoragePort storagePort, String bucket) {
  this.storagePort = Objects.requireNonNull(storagePort);
  if (bucket == null || bucket.isBlank()) {
    throw new IllegalArgumentException("bucket must not be blank");
  }
  this.bucket = bucket;
}
```

**Application config wires everything:**
```java
@Bean
public FileServicePort fileService(StoragePort storagePort, S3Configuration cfg) {
  return new FileServiceImpl(storagePort, cfg.getBucket());
}

@Bean
public S3Client s3Client(S3Configuration cfg) {
  // Creates S3Client for MinIO (with endpoint override + path-style)
  // or AWS (standard SDK behavior)
  // See StorageConfiguration for full logic
}
```

**Adapters use constructor + @Component (Spring):**
```java
@Component
public class S3StorageAdapter implements StoragePort {
  private final S3Client s3Client;  // Injected by Spring
  
  public S3StorageAdapter(S3Client s3Client) {
    this.s3Client = s3Client;
  }
}
```

---

## 🛡️ Exception Handling Pattern

**Core domain:** Custom exceptions, no HTTP knowledge:
```java
public class StorageException extends Exception {
  public StorageException(String message) { super(message); }
  public StorageException(String message, Throwable cause) { super(message, cause); }
}
```

**Adapter translates SDK exceptions to domain exceptions:**
```java
@Component
public class S3StorageAdapter implements StoragePort {
  @Override
  public StorageObject store(String bucket, String key, String contentType, byte[] content) {
    try {
      s3Client.putObject(...);
      return new StorageObject(...);
    } catch (S3Exception ex) {
      throw new StorageException(
        "S3 store failed [bucket=%s key=%s]: %s".formatted(bucket, key, ex.awsErrorDetails().errorMessage()),
        ex
      );
    }
  }
}
```

**Controller translates domain exceptions to HTTP responses:**
```java
@RestController
@RequestMapping("/api/files")
public class FileController {
  @PostMapping("/upload")
  public ResponseEntity<StorageObject> upload(@RequestParam("file") MultipartFile file) throws IOException {
    if (file.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    StorageObject stored = fileService.upload(file.getOriginalFilename(), file.getContentType(), file.getBytes());
    return ResponseEntity.status(HttpStatus.CREATED).body(stored);
  }
  
  @ExceptionHandler(StorageException.class)
  public ResponseEntity<Map<String, String>> handleStorageException(StorageException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(Map.of("error", "Storage operation failed", "detail", ex.getMessage()));
  }
  
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest()
      .body(Map.of("error", "Invalid request", "detail", ex.getMessage()));
  }
}
```

**Flow:** AWS SDK exception → `StorageException` (core) → HTTP 500/400 (controller)

❌ **Adding Spring to core:**
```java
// FORBIDDEN in ecs-core/
@Service  // NO!
public class FileServiceImpl { }
```

❌ **Inbound calling outbound directly:**
```java
// FORBIDDEN in ecs-inbound-adapters/
@RestController
public class FileController {
  private S3StorageAdapter adapter;  // NO! Should be FileServicePort
}
```

❌ **Configuration outside application module:**
```java
// FORBIDDEN in ecs-outbound-adapters/
@Configuration  // NO!
public class StorageConfig { }
```

---

## 🎓 Key Files to Understand

| File | Purpose |
|------|---------|
| `ecs-core/ports/FileServicePort.java` | Inbound port (what REST calls) |
| `ecs-core/ports/StoragePort.java` | Outbound port (what adapters implement) |
| `ecs-core/usecases/FileServiceImpl.java` | Core use case (pure domain logic) |
| `ecs-inbound-adapters/rest/FileController.java` | REST controller (HTTP→domain) |
| `ecs-outbound-adapters/storage/S3StorageAdapter.java` | Storage adapter (domain→SDK) |
| `ecs-application/config/StorageConfiguration.java` | Wires ports & adapters |
| `ecs-tests/arch/HexagonalArchitectureTest.java` | Architecture enforcement (ArchUnit) |
| `ecs-tests/unit/FileServiceImplTest.java` | Domain use case tests (no Spring) |
| `ecs-tests/unit/FileControllerTest.java` | Adapter HTTP tests (MockMvc standalone) |
| `ecs-application/integration/FileUploadIT.java` | End-to-end tests with real MinIO (in ecs-application, not ecs-tests) |

---

**Last Updated:** July 22, 2026 · **Architecture:** Hexagonal · **Enforcement:** STRICT (ArchUnit)

