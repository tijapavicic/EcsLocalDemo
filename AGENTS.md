# AGENTS.md — ECS Local Demo

**Project Type:** Multi-cloud S3 file upload service  
**Architecture:** Hexagonal (Ports & Adapters)  
**Stack:** Java 21 · Spring Boot 3.1.7 · Maven · AWS SDK v2 · TestContainers · ArchUnit  
**Enforcement:** STRICT — ArchUnit tests block builds on violations
**SOLID Principles:** Followed rigorously; no god classes, no cross-layer dependencies

When refactoring, follow these rules:
- **S** — Each class/method has one clear responsibility; no god classes.
- **O** — New behaviour added via extension (new implementations/strategies), not by editing existing logic.
- **L** — Implementations are interchangeable; no coercion of base types.
- **I** — Ports are narrow and focused; clients are not forced to depend on unused methods.
- **D** — High-level modules depend on port interfaces, never on concrete adapters.

---

## 🏗️ Core Architecture Pattern

This project implements **Hexagonal Architecture** with strict separation:

```
HTTP Request (with Authorization: Bearer token)
    ↓
[BearerTokenAuthenticationFilter] (security interceptor)
    ↓
[FileController] (inbound adapter)
    ↓
TokenExtractorPort (token validation port)
    ↓
[BearerTokenExtractor] (token adapter — extracts UserContext)
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

**Authentication Flow:** Bearer token → extracted and validated by `TokenExtractorPort` → `UserContext` injected into use cases → files scoped to `users/{userId}/...`

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
- ✅ Security adapters (Bearer token extraction, authorization) implement `TokenExtractorPort`
- ✅ Input validation delegated to validator classes (e.g., `FileUploadValidator`)
- ✅ ThreadLocal context storage via `RequestContextHolder` for downstream access to `UserContext`
- ❌ NO business logic (that's in core)
- ❌ NO direct calls to outbound adapters

**Example:** `FileController` extracts Bearer token → calls `TokenExtractorPort.extractUserContext()` → calls `FileServicePort.upload(UserContext, ...)` → returns `201 Created` with `StorageObject` (includes `userId`)

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

1. **Client sends:** `POST /api/files/upload` (multipart/form-data with `storyId` and `file` fields, `Authorization: Bearer token` header)
2. **Security filter** (`BearerTokenAuthenticationFilter`) intercepts request
3. **Token extraction:** `AuthorizationExtractor` parses `Authorization: Bearer <token>` header
4. **Token validation:** `TokenExtractorPort.extractUserContext(token)` → returns `UserContext` (userId, email)
5. **Spring routes to:** `FileController.upload(authHeader, storyId, file)`
6. **Controller validation:** `FileUploadValidator.validate(file)` checks file size, MIME type; validates `storyId` is not blank
7. **Controller calls:** `tokenExtractor.extractUserContext(token)` → gets `UserContext`
8. **Controller calls:** `fileService.upload(UserContext, storyId, filename, contentType, content)`
9. **FileServiceImpl validates:** all parameters (userContext, storyId, filename, contentType, content)
10. **FileServiceImpl delegates key generation:** calls `keyGenerator.generateKey(userId, storyId, filename)` via `StorageKeyGeneratorPort`
11. **UserScopedKeyGenerator generates:** user and story-scoped key `users/{userId}/{storyId}/2024-01-15/uuid-filename.pdf`
12. **FileServiceImpl calls:** `storagePort.store(bucket, key, contentType, content)`
13. **S3StorageAdapter (MinIO/AWS):** builds `PutObjectRequest`, uploads via `S3Client.putObject()`
14. **Returns:** `StorageObject` with metadata (key, bucket, contentType, size, timestamp, **userId**)
15. **Controller returns:** `201 Created` with JSON body (includes userId for audit trail)

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

## 🔐 Authentication & User Context (IDM)

### Bearer Token Flow

All `/api/files/**` endpoints require Bearer token authentication. Token format (development): `Base64(userId:email)`.

**Token Format (Base64-encoded, development only):**
```bash
# Generate token
TOKEN=$(echo -n "alice:alice@example.com" | base64)
echo "Authorization: Bearer $TOKEN"
```

**In production**, replace `BearerTokenExtractor` with JWT validation.

### RequestContextHolder — Thread-Safe Context Storage

`RequestContextHolder` is a ThreadLocal-based utility for storing/retrieving `UserContext` in request scope. This eliminates the need to pass `UserContext` through every method parameter.

**Flow:**
1. `BearerTokenAuthenticationFilter` extracts and validates Bearer token
2. Stores extracted `UserContext` in `RequestContextHolder` (ThreadLocal)
3. Downstream handlers (controllers, services, adapters) can retrieve context via `RequestContextHolder.get()`
4. Filter always calls `RequestContextHolder.clear()` in finally block to prevent ThreadLocal leaks

**Usage:**
```java
// In BearerTokenAuthenticationFilter (filter chain)
UserContext userContext = tokenExtractor.extractUserContext(token);
RequestContextHolder.set(userContext);  // Store in ThreadLocal

// Later in controller/service
UserContext userContext = RequestContextHolder.get();  // Retrieve from ThreadLocal

// In filter's finally block (CRITICAL)
RequestContextHolder.clear();  // Prevent ThreadLocal leaks in servlet pools
```

**Thread-Safety:** Each request runs on its own thread; ThreadLocal maintains separate context per thread. Always clear in finally blocks.

### Ports & Domain Objects

**Inbound Port (Token Validation):**
```java
// ecs-core/ports/TokenExtractorPort.java
public interface TokenExtractorPort {
  UserContext extractUserContext(String token) throws TokenValidationException;
  boolean isTokenValid(String token);
}
```

**Outbound Port (Storage Key Generation Strategy):**
```java
// ecs-core/ports/StorageKeyGeneratorPort.java — pluggable key generation
public interface StorageKeyGeneratorPort {
  /**
   * Generate user and story-scoped storage key.
   * @param userId   authenticated user ID (e.g., "alice")
   * @param storyId  story context ID (e.g., "story-123")
   * @param filename original filename (e.g., "report.pdf")
   * @return generated key (e.g., "users/alice/story-123/2024-01-15/abc123-report.pdf")
   */
  String generateKey(String userId, String storyId, String filename);
}
```

**Outbound Adapter (Key Generation Implementation):**
```java
// ecs-outbound-adapters/storage/UserScopedKeyGenerator.java
@Component
public class UserScopedKeyGenerator implements StorageKeyGeneratorPort {
  @Override
  public String generateKey(String userId, String storyId, String filename) {
    // Validates inputs, generates UUID prefix, formats date, sanitizes filename
    return String.format("users/%s/%s/%s/%s-%s", userId, storyId, date, uuid, safeFilename);
  }
}
```

**Why Pluggable Key Generation?**
- **Open/Closed Principle:** New key generation strategies (flat layout, cloud-specific formats, encryption prefixes) can be added without modifying `FileServiceImpl`
- **Single Responsibility:** `FileServiceImpl` orchestrates upload; `StorageKeyGeneratorPort` owns key generation strategy
- **Testability:** Key generation logic can be tested in isolation without mocking storage

**Domain Objects (Core):**
```java
// ecs-core/domain/UserContext.java — immutable, thread-safe
public class UserContext {
  private final String userId;      // "alice"
  private final String email;       // "alice@example.com"
  private final String token;       // raw bearer token
  // getters, equals, hashCode
}

// ecs-core/domain/StorageObject.java — immutable, returned from upload
public class StorageObject {
  private final String key;         // "users/alice/story-123/2024-01-15/abc123-report.pdf"
  private final String bucket;      // "demo-bucket"
  private final String contentType; // "application/pdf"
  private final long sizeBytes;     // file size
  private final Instant uploadedAt; // timestamp
  private final String userId;      // user identity for audit trail
  // getters, equals, hashCode
}
```

**Updated Use Case Signature:**
```java
// ecs-core/ports/FileServicePort.java
public StorageObject upload(
  UserContext userContext,           // authenticated user
  String storyId,                    // story context (e.g., "story-123")
  String filename,                   // original filename
  String contentType,                // MIME type
  byte[] content                     // file bytes
) throws StorageException;
```

### Security Configuration

**Feature Flag — Enable/Disable Authentication:**
```yaml
# application.yml
app:
  security:
    bearer-token-auth-enabled: true  # Set to false for testing without auth
```

**When Enabled:**
- `BearerTokenAuthenticationFilter` intercepts all requests
- Validates Bearer token via `TokenExtractorPort`
- Stores `UserContext` in ThreadLocal (via `RequestContextHolder`) for downstream access
- `/api/files/**` endpoints require authentication
- `/actuator/**` and `/swagger-ui/**` are public

**When Disabled (testing/debugging):**
- Bearer token filter is skipped entirely
- All requests allowed without authentication
- ⚠️ **WARNING:** Do NOT use disabled mode in production

### Storage Key Format (User & Story-Scoped)

Files are stored in user and story-scoped paths to prevent cross-user access, organize files by context, and enable audit trails:

```
users/{userId}/{storyId}/{yyyy-MM-dd}/{uuid}-{filename}
```

**Example:**
```
users/alice/story-123/2026-07-22/3f4a1b2c-report.pdf
users/alice/story-456/2026-07-22/2e8d9a7f-photo.jpg
users/bob/story-123/2026-07-22/7f2d8a9e-document.pdf
```

**Key Generation Strategy (Pluggable via `StorageKeyGeneratorPort`):**
- Filename is sanitized to remove path traversal and invalid characters: `[^a-zA-Z0-9._-]` → `_`
- UUID prefix (8 chars) prevents collisions from simultaneous uploads
- Date partition enables time-series queries and cleanup policies
- Story context isolates files for different use cases/projects within user's space

Each user's files are isolated within their `users/{userId}/` prefix, and organized by story context, enabling:
- 🔒 Cross-user access prevention (authorization boundary)
- 📂 Per-story file organization (separate uploads for different projects/contexts)
- 📋 Audit trails (who uploaded what, when, in which story context)
- 📊 Per-user and per-story storage quotas (future enhancement)

---

## 🛡️ Input Validation

Validation is delegated to `FileUploadValidator` (separation of concerns):

```java
// ecs-inbound-adapters/rest/FileUploadValidator.java
public void validate(MultipartFile file) {
  if (file == null || file.isEmpty()) {
    throw new IllegalArgumentException("file is required and must not be empty");
  }
  if (file.getSize() > MAX_FILE_SIZE) {
    throw new IllegalArgumentException("file size exceeds limit");
  }
  // Additional MIME type, content checks, etc.
}
```

Called early in the request lifecycle:
```java
// FileController.java
@PostMapping("/upload")
public ResponseEntity<StorageObject> upload(...) {
  fileValidator.validate(file);        // ← Fail fast
  UserContext user = tokenExtractor.extractUserContext(token);
  StorageObject result = fileService.upload(user, ...);
  return ResponseEntity.status(HttpStatus.CREATED).body(result);
}
```

---

## ⚡ Rate Limiting

Rate limiting is implemented via `RateLimitInterceptor` (HTTP interceptor pattern).

**Configuration:**
```yaml
# application.yml
app:
  rate-limit:
    requests-per-minute: 60
```

**How it works:**
1. `RateLimitInterceptor` extends `HandlerInterceptor`
2. Registered in `WebMvcConfiguration` as a Spring Bean
3. Tracks request count per client (by IP or user ID)
4. Returns `HTTP 429 Too Many Requests` when limit exceeded
5. Transparent to business logic (no Spring in core)

**Response when rate-limited:**
```json
{
  "error": "Too Many Requests",
  "detail": "Rate limit exceeded: 60 requests per minute"
}
```

---

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
  private static final String USER_ID = "alice";
  private static final String STORY_ID = "story-123";
  private static final byte[] CONTENT = "hello world".getBytes();
  
  private StoragePort storagePort;
  private StorageKeyGeneratorPort keyGenerator;
  private FileServiceImpl fileService;
  
  @BeforeEach
  void setUp() {
    storagePort = mock(StoragePort.class);
    keyGenerator = mock(StorageKeyGeneratorPort.class);
    fileService = new FileServiceImpl(storagePort, keyGenerator, BUCKET);
  }
  
  @Test
  @DisplayName("upload() — delegates key generation to StorageKeyGeneratorPort")
  void upload_delegatesKeyGeneration_toPort() throws StorageException {
    UserContext userContext = new UserContext(USER_ID, "alice@example.com", "token");
    
    when(keyGenerator.generateKey(USER_ID, STORY_ID, "report.pdf"))
      .thenReturn("users/alice/story-123/2024-01-15/abc123-report.pdf");
    
    when(storagePort.store(any(), any(), any(), any()))
      .thenAnswer(inv -> new StorageObject(inv.getArgument(1), BUCKET, "application/pdf", CONTENT.length, Instant.now(), USER_ID));
    
    fileService.upload(userContext, STORY_ID, "report.pdf", "application/pdf", CONTENT);
    
    verify(keyGenerator).generateKey(USER_ID, STORY_ID, "report.pdf");
    verify(storagePort).store(eq(BUCKET), eq("users/alice/story-123/2024-01-15/abc123-report.pdf"), any(), any());
  }
  
  @Test
  @DisplayName("upload() — throws IllegalArgumentException when storyId is blank")
  void upload_throwsIllegalArgument_whenStoryIdIsBlank() {
    UserContext userContext = new UserContext(USER_ID, "alice@example.com", "token");
    
    assertThatThrownBy(() -> fileService.upload(userContext, "", "file.txt", "text/plain", CONTENT))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("storyId");
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
  private TokenExtractorPort tokenExtractor;
  
  @BeforeEach
  void setUp() {
    fileService = mock(FileServicePort.class);
    tokenExtractor = mock(TokenExtractorPort.class);
    mockMvc = MockMvcBuilders
      .standaloneSetup(new FileController(fileService, tokenExtractor, new FileUploadValidator()))
      .build();
  }
  
  @Test
  @DisplayName("POST /api/files/upload — returns 201 with StorageObject JSON")
  void upload_returns201_withStorageObjectBody() throws Exception {
    String token = "dXNlcjEyMzp1c2VyQGV4YW1wbGUuY29t";  // base64-encoded userId:email
    UserContext userContext = new UserContext("user123", "user@example.com", token);
    
    StorageObject stored = new StorageObject(
      "users/user123/story-123/2024-01-15/abc-report.pdf", "demo-bucket",
      "application/pdf", 1024L, Instant.now(), "user123");
    
    when(tokenExtractor.extractUserContext(token)).thenReturn(userContext);
    when(fileService.upload(any(), any(), any(), any(), any())).thenReturn(stored);
    
    MockMultipartFile file = new MockMultipartFile(
      "file", "report.pdf", "application/pdf", "PDF content".getBytes());
    
    mockMvc.perform(multipart("/api/files/upload")
      .file(file)
      .param("storyId", "story-123")
      .header("Authorization", "Bearer " + token))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.key").value("users/user123/story-123/2024-01-15/abc-report.pdf"))
      .andExpect(jsonPath("$.userId").value("user123"));
  }
  
  @Test
  @DisplayName("POST /api/files/upload — returns 400 when storyId is missing")
  void upload_returns400_whenStoryIdIsMissing() throws Exception {
    String token = "dXNlcjEyMzp1c2VyQGV4YW1wbGUuY29t";
    
    MockMultipartFile file = new MockMultipartFile(
      "file", "file.txt", MediaType.TEXT_PLAIN_VALUE, "data".getBytes());
    
    mockMvc.perform(multipart("/api/files/upload")
      .file(file)
      .header("Authorization", "Bearer " + token))
      .andExpect(status().isBadRequest());
  }
  
  @Test
  @DisplayName("POST /api/files/upload — returns 500 when StorageException is thrown")
  void upload_returns500_whenStorageExceptionThrown() throws Exception {
    String token = "dXNlcjEyMzp1c2VyQGV4YW1wbGUuY29t";
    UserContext userContext = new UserContext("user123", "user@example.com", token);
    
    when(tokenExtractor.extractUserContext(token)).thenReturn(userContext);
    when(fileService.upload(any(), any(), any(), any(), any()))
      .thenThrow(new StorageException("MinIO unreachable"));
    
    MockMultipartFile file = new MockMultipartFile(
      "file", "file.txt", MediaType.TEXT_PLAIN_VALUE, "data".getBytes());
    
    mockMvc.perform(multipart("/api/files/upload")
      .file(file)
      .param("storyId", "story-123")
      .header("Authorization", "Bearer " + token))
      .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.error").value("Storage operation failed"));
  }
}
```

**Key:** Standalone `MockMvcBuilders.standaloneSetup()` — NO Spring context needed. Tests HTTP serialization, deserialization, and exception mapping. Faster than integration tests; covers adapter concerns (multipart parsing, JSON responses, HTTP status codes).

### Layer 3: Integration Tests (Real MinIO + Full Spring)

**Location:** `ecs-application/src/test/` — Integration tests live with the application module, not in ecs-tests. This follows clean hexagonal architecture: tests that need the full Spring context belong where Spring starts.

#### Basic File Upload (without authentication)
```java
@Testcontainers
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local-minio")
class FileUploadIT {
  // ... TestContainers MinIO setup ...
  
  @Test
  void upload_endToEnd_persistsFileInMinio_returns201() {
    // Test file upload
  }
}
```

#### File Upload with IDM (Bearer Token Authentication)
```java
@Testcontainers
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local-minio")
class FileUploadWithIDMIT {
  @Autowired private TestRestTemplate restTemplate;
  
  @Test
  void uploadWithBearerToken_returns201_withUserScopedKey() {
    // Generate Bearer token
    String token = Base64.getEncoder().encodeToString("alice:alice@example.com".getBytes());
    
    // Upload with Bearer token and storyId
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + token);
    
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("storyId", "story-123");
    body.add("file", new FileSystemResource(new File("test.txt")));
    
    HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
    ResponseEntity<StorageObject> response = restTemplate.postForEntity("/api/files/upload", request, StorageObject.class);
    
    // Assert: 201 Created
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    
    // Assert: key is user and story-scoped
    assertThat(response.getBody().getKey()).matches("users/alice/story-123/\\d{4}-\\d{2}-\\d{2}/.+");
    
    // Assert: response includes userId for audit trail
    assertThat(response.getBody().getUserId()).isEqualTo("alice");
  }
  
  @Test
  void uploadWithInvalidToken_returns401_unauthorized() {
    // Test invalid/expired token handling
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer invalid-token");
    
    // Should be rejected by BearerTokenExtractor
  }
}
```

**Key:** TestContainers spins up real MinIO. `@DynamicPropertySource` injects container endpoint. Full Spring context. Tests real HTTP → security → domain → adapter → storage flow.

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
# Generate Bearer token (base64-encoded userId:email)
TOKEN=$(echo -n "alice:alice@example.com" | base64)

# Upload a file with storyId
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "storyId=story-123" \
  -F "file=@./pom.xml" \
  -H "Accept: application/json"

# Response: 201 Created
# {
#   "key": "users/alice/story-123/2024-01-15/abc123-pom.xml",
#   "bucket": "demo-bucket",
#   "contentType": "application/xml",
#   "sizeBytes": 2847,
#   "uploadedAt": "2024-01-15T10:30:45Z",
#   "userId": "alice"
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
| `ecs-core/ports/FileServicePort.java` | Inbound port (what REST calls) — `upload(UserContext, storyId, ...)` with story context |
| `ecs-core/ports/StoragePort.java` | Outbound port (what adapters implement) for cloud storage operations |
| `ecs-core/ports/TokenExtractorPort.java` | Inbound port for Bearer token extraction & validation |
| `ecs-core/ports/StorageKeyGeneratorPort.java` | Outbound port for storage key generation strategy (pluggable) |
| `ecs-core/domain/UserContext.java` | Immutable domain object representing authenticated user |
| `ecs-core/domain/StorageObject.java` | Storage metadata (includes `userId` field for audit trail) |
| `ecs-core/usecases/FileServiceImpl.java` | Core use case (pure domain logic, user+story-scoped file storage) |
| `ecs-inbound-adapters/rest/FileController.java` | REST controller (HTTP↔domain), requires `storyId` form param |
| `ecs-inbound-adapters/rest/FileUploadValidator.java` | Input validation (file size, content checks) |
| `ecs-inbound-adapters/security/BearerTokenExtractor.java` | Token extraction adapter (implements `TokenExtractorPort`) |
| `ecs-inbound-adapters/security/BearerTokenAuthenticationFilter.java` | Spring Security filter for Bearer token interception & ThreadLocal storage |
| `ecs-inbound-adapters/security/AuthorizationExtractor.java` | Utility to extract Bearer token from Authorization header |
| `ecs-inbound-adapters/security/RequestContextHolder.java` | ThreadLocal-based context storage for request-scoped `UserContext` retrieval |
| `ecs-outbound-adapters/storage/S3StorageAdapter.java` | Storage adapter (domain→SDK) implementing `StoragePort` |
| `ecs-outbound-adapters/storage/UserScopedKeyGenerator.java` | Key generation adapter implementing `StorageKeyGeneratorPort` — generates `users/{userId}/{storyId}/{date}/{uuid}-{filename}` |
| `ecs-application/config/SecurityConfiguration.java` | Wires token extractor & security filter chain |
| `ecs-application/config/StorageConfiguration.java` | Wires storage adapter & key generator based on profile |
| `ecs-application/config/S3Configuration.java` | YAML properties binding for S3 credentials and endpoints |
| `ecs-application/integration/SecurityTestConfiguration.java` | Test configuration disabling Spring Security for integration tests (allows Bearer token mocking) |
| `ecs-tests/arch/HexagonalArchitectureTest.java` | Architecture enforcement (ArchUnit) |
| `ecs-tests/unit/FileServiceImplTest.java` | Domain use case tests (no Spring) with key generator mocking |
| `ecs-tests/unit/FileControllerTest.java` | Adapter HTTP tests (MockMvc standalone) with storyId validation |
| `ecs-application/integration/FileUploadIT.java` | End-to-end tests with real MinIO |
| `ecs-application/integration/FileUploadWithIDMIT.java` | End-to-end tests with Bearer token authentication & storyId |

---

**Last Updated:** July 24, 2026 · **Architecture:** Hexagonal · **Enforcement:** STRICT (ArchUnit)

