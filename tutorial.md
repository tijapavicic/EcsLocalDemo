# 🏗️ Tutorial: Multi-Cloud S3 File Upload with Hexagonal Architecture

**Audience:** Junior Developers  
**Presented by:** Principal Engineer  
**Project:** ECS Local Demo

---

## 🎯 What We're Building

A Spring Boot service that uploads files to cloud storage — supporting **MinIO** (local development) and **AWS S3** (production) — with **zero code changes** when switching between them.

```
POST /api/files/upload  →  file stored in S3 / MinIO  →  201 Created
```

---

## 🤔 The Problem We're Solving First

> *"Junior Dev asks: Why can't I just do this?"*

```java
@RestController
public class FileController {
    private final AmazonS3 s3Client;  // ❌ Direct AWS SDK dependency in controller

    @PostMapping("/upload")
    public void upload(MultipartFile file) {
        s3Client.putObject("my-bucket", file.getOriginalFilename(), ...);
    }
}
```

**Problems with this approach:**
1. 🔒 **Cannot swap to MinIO** without changing controller code
2. 🧪 **Cannot unit test** without a real S3 connection
3. 🔧 **Business logic and infrastructure are tangled**
4. 💥 **AWS SDK changes break the controller**

---

## 🏛️ The Solution: Hexagonal Architecture (Ports & Adapters)

The core idea: **isolate business logic from infrastructure**.

```
┌──────────────────────────────────────────────────────────────────┐
│                    ECS LOCAL DEMO                                │
│                                                                  │
│  ┌─────────────┐    ┌────────────────┐    ┌──────────────────┐  │
│  │  HTTP/REST  │    │   CORE DOMAIN  │    │  S3 / MinIO      │  │
│  │             │──▶ │                │──▶ │                  │  │
│  │FileController│   │ FileServiceImpl│    │ S3StorageAdapter │  │
│  │             │    │                │    │                  │  │
│  └─────────────┘    └────────────────┘    └──────────────────┘  │
│   Inbound            Ports & Use Cases     Outbound              │
│   Adapter            (pure Java)           Adapter               │
└──────────────────────────────────────────────────────────────────┘
```

The **Core Domain** knows nothing about HTTP or S3. It only knows about **ports** (interfaces).

---

## 📁 Project Structure Walk-Through

```
EcsLocalDemo/
├── ecs-core/               ← ZERO Spring. Pure Java business logic.
│   ├── domain/             ← Value objects (StorageObject, StorageException)
│   ├── ports/              ← Interfaces only (FileServicePort, StoragePort)
│   └── usecases/           ← FileServiceImpl — the actual business logic
│
├── ecs-inbound-adapters/   ← Spring Web. Converts HTTP → domain calls
│   └── FileController.java ← ONE controller, ONE endpoint
│
├── ecs-outbound-adapters/  ← AWS SDK. Converts domain → S3 API calls
│   └── S3StorageAdapter.java ← Works for BOTH MinIO and AWS
│
├── ecs-application/        ← Spring Boot wiring ONLY. No logic here.
│   ├── Application.java    ← Entry point
│   ├── S3Configuration.java ← Reads storage.s3.* from YAML
│   ├── StorageConfiguration.java ← Creates S3Client bean
│   └── resources/
│       ├── application.yml             ← Common config
│       ├── application-local-minio.yml ← MinIO settings
│       └── application-aws.yml         ← AWS settings
│
└── ecs-tests/              ← All tests: unit, integration, architecture
```

---

## 🔑 Key Concept 1: Ports (Interfaces)

> *"A port is a contract. It says what to do, NOT how to do it."*

```java
// FileServicePort.java — INBOUND port (what the REST layer calls)
public interface FileServicePort {
    StorageObject upload(String filename, String contentType, byte[] content)
        throws StorageException;
}

// StoragePort.java — OUTBOUND port (what storage adapters must implement)
public interface StoragePort {
    StorageObject store(String bucket, String key, String contentType, byte[] content)
        throws StorageException;
}
```

The controller ONLY knows about `FileServicePort`. It never sees S3.  
The use-case ONLY knows about `StoragePort`. It never sees HTTP.

---

## 🔑 Key Concept 2: The Use-Case (FileServiceImpl)

> *"Business logic with no framework. This is what survives AWS → Azure migrations."*

```java
// No @Service, no @Component, no @Autowired — plain Java class!
public class FileServiceImpl implements FileServicePort {

    private final StoragePort storagePort;  // Interface, not S3StorageAdapter
    private final String bucket;

    public StorageObject upload(String filename, String contentType, byte[] content) throws StorageException {
        validateFilename(filename);    // Business rule
        validateContent(content);      // Business rule
        String key = generateKey(filename);  // Business rule: date/uuid-filename
        return storagePort.store(bucket, key, contentType, content);  // Delegate to adapter
    }
}
```

**Why does this matter?**  
You can test this class without Docker, without AWS, without Spring — with just:
```java
StoragePort mockStorage = mock(StoragePort.class);
FileServiceImpl service = new FileServiceImpl(mockStorage, "test-bucket");
```

---

## 🔑 Key Concept 3: Spring Profiles + S3Configuration

> *"The magic of zero-code switching between MinIO and AWS."*

```yaml
# application-local-minio.yml
storage.s3:
  bucket: demo-bucket
  endpoint: http://localhost:9000  # ← This line = MinIO mode
  access-key: minioadmin
  secret-key: minioadmin
  region: us-east-1

# application-aws.yml
storage.s3:
  bucket: ${AWS_S3_BUCKET}
  # No endpoint here = AWS mode
  access-key: ${AWS_ACCESS_KEY_ID}
  secret-key: ${AWS_SECRET_ACCESS_KEY}
  region: ${AWS_REGION}
```

`S3Configuration` reads these properties. `StorageConfiguration` uses them to build the right S3Client:

```java
@Bean
public S3Client s3Client(S3Configuration cfg) {
    var builder = S3Client.builder()
        .credentials(...)
        .region(cfg.getRegion());

    if (cfg.getEndpoint() != null) {
        // endpoint set → MinIO mode (path-style access)
        builder.endpointOverride(URI.create(cfg.getEndpoint()))
               .serviceConfiguration(S3Config.builder().pathStyleAccessEnabled(true).build());
    }
    // no endpoint → AWS mode (standard SDK behavior)

    return builder.build();
}
```

**To switch profiles:**
```bash
java -jar app.jar --spring.profiles.active=local-minio   # MinIO
java -jar app.jar --spring.profiles.active=aws           # AWS S3
```

---

## 🔑 Key Concept 4: The One Controller

> *"One endpoint does one thing. SRP at its finest."*

```java
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileServicePort fileService;  // Interface — never the concrete class!

    @PostMapping("/upload")
    public ResponseEntity<StorageObject> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) return ResponseEntity.badRequest().build();

        StorageObject stored = fileService.upload(
            file.getOriginalFilename(),
            file.getContentType(),
            file.getBytes()
        );

        return ResponseEntity.status(201).body(stored);
    }
}
```

The controller:
- ✅ Converts HTTP → domain call
- ✅ Converts domain result → HTTP response
- ❌ Does NOT contain business logic
- ❌ Does NOT know about S3 or MinIO

---

## 🧪 Testing Pyramid

```
         /\
        /  \  Architecture Tests (ArchUnit)
       /────\  — Rules enforced on every build
      /      \ 
     /────────\ Integration Tests (TestContainers)
    /          \ — Real MinIO via Docker, no mocks
   /────────────\
  /              \ Unit Tests (JUnit 5 + Mockito)
 /────────────────\ — Fast, no Docker, no Spring context
/                  \
```

### Unit Test Example (no framework, blazing fast)
```java
@Test
void upload_delegatesToStoragePort_withCorrectArguments() throws StorageException {
    StoragePort mockStorage = mock(StoragePort.class);
    FileServiceImpl service = new FileServiceImpl(mockStorage, "bucket");
    when(mockStorage.store(...)).thenReturn(storedObject);

    service.upload("file.txt", "text/plain", "hello".getBytes());

    verify(mockStorage).store(eq("bucket"), any(), eq("text/plain"), any());
}
```

### Integration Test Example (real MinIO via TestContainers)
```java
@Testcontainers
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("local-minio")
class FileUploadIT {
    @Container
    static GenericContainer<?> minio = new GenericContainer<>("minio/minio")
        .withCommand("server /data")
        .withExposedPorts(9000);

    @DynamicPropertySource
    static void overrideEndpoint(DynamicPropertyRegistry r) {
        r.add("storage.s3.endpoint", () -> "http://localhost:" + minio.getMappedPort(9000));
    }

    @Test
    void upload_persistsFileInRealMinio() {
        // Real HTTP request → real MinIO — no mocks!
    }
}
```

### Architecture Test Example (ArchUnit — prevents regressions)
```java
@ArchTest
public static final ArchRule core_must_have_no_spring_dependencies =
    noClasses()
        .that().resideInAPackage("com.example.core..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework..")
        .because("Core domain must be framework-agnostic");
```

If anyone accidentally adds `@Service` to `FileServiceImpl`, this test **breaks the build immediately**.

---

## 🐳 Docker Compose — Local Development

```yaml
services:
  minio:   # S3-compatible storage
  init:    # Creates the bucket automatically
  app:     # Spring Boot with profile=local-minio
```

```bash
docker compose up -d
# → MinIO API at :9000
# → MinIO Console at :9001 (browser UI)
# → App at :8080
```

---

## 🗺️ Request Flow (one upload, start to finish)

```
Client                Controller            FileServiceImpl        S3StorageAdapter      MinIO
  │                       │                       │                      │                 │
  │ POST /api/files/upload │                       │                      │                 │
  │ multipart/form-data    │                       │                      │                 │
  │──────────────────────▶│                       │                      │                 │
  │                       │ fileService.upload()   │                      │                 │
  │                       │──────────────────────▶│                      │                 │
  │                       │                       │ validate input        │                 │
  │                       │                       │ generate key          │                 │
  │                       │                       │ storagePort.store()   │                 │
  │                       │                       │─────────────────────▶│                 │
  │                       │                       │                      │ s3Client.put()  │
  │                       │                       │                      │────────────────▶│
  │                       │                       │                      │◀────────────────│
  │                       │                       │◀─────────────────────│                 │
  │                       │◀──────────────────────│                      │                 │
  │◀──────────────────────│                       │                      │                 │
  │ 201 Created            │                       │                      │                 │
  │ {key, bucket, ...}     │                       │                      │                 │
```

---

## 📚 Key Takeaways

| Concept | Rule |
|---------|------|
| **Core domain** | Zero Spring annotations. Ever. |
| **Ports** | Interfaces define contracts between layers |
| **Inbound adapters** | Only call inbound ports |
| **Outbound adapters** | Only implement outbound ports |
| **Configuration** | Only in `ecs-application` |
| **Profiles** | Switch infrastructure, not code |
| **Testing** | Unit → Integration → Architecture pyramid |

---

## 🚀 Next Steps for Juniors

1. **Run it**: `docker compose up -d` → upload a file via Postman
2. **Read it**: trace one upload from `FileController` → `FileServiceImpl` → `S3StorageAdapter`
3. **Break it**: add `@Service` to `FileServiceImpl` → run `mvn test` — watch ArchUnit catch it
4. **Extend it**: add a `DELETE /api/files/{key}` endpoint by following the same pattern
5. **Switch it**: run with `--spring.profiles.active=aws` and a real AWS bucket

> *"Good architecture makes the right thing easy and the wrong thing impossible."*

