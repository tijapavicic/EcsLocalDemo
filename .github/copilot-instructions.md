# GitHub Copilot Instructions - EcsLocalDemo

**Last Updated:** July 19, 2026  
**Architecture:** Hexagonal Architecture (Ports & Adapters)  
**Enforcement:** STRICT (ArchUnit tests required)

---

## 🏗️ HEXAGONAL ARCHITECTURE - MANDATORY RULES

### Rule 1: Core Modules - ZERO Spring Dependencies ✅
**Modules:** `ecs-core/`, `ecs-storage-core/`

Core domain logic must have ZERO Spring Framework dependencies.

```java
// ✅ ALLOWED in core
import java.util.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class S3Object {
  String key;
  byte[] content;
  String contentType;
}

// ❌ FORBIDDEN in core
import org.springframework.stereotype.Component;  // NO!
import org.springframework.beans.factory.annotation.Autowired;  // NO!
```

**Enforcement:** ArchUnit tests verify NO Spring imports

---

### Rule 2: Inbound Adapters Call Inbound Ports ✅
**Pattern:** Adapter → Port (Interface) → Domain Logic

Inbound adapters (REST controllers, message listeners, CLI) MUST call inbound ports (interfaces defined in core).

```java
// In ecs-inbound-adapters/rest
@RestController
public class FileController {
  private final FileServicePort fileService;  // ✅ Port (interface)
  
  public FileController(FileServicePort fileService) {
    this.fileService = fileService;
  }
  
  @PostMapping("/files/upload")
  public ResponseEntity<?> uploadFile(@RequestParam MultipartFile file) {
    // Call port (use case)
    S3Object result = fileService.uploadObject(file.getBytes());
    return ResponseEntity.created(...).body(result);
  }
}

// ❌ WRONG - Direct adapter call
@RestController
public class FileController {
  private final AwsS3Repository s3Repo;  // NO! This is an adapter
  // ...
}
```

---

### Rule 3: Outbound Adapters Implement Outbound Ports ✅
**Pattern:** Domain Port (Interface) ← Adapter implements

Outbound adapters (repositories, external APIs) MUST implement outbound ports.

```java
// In ecs-core/ports
public interface StoragePort {
  S3Object uploadObject(String key, byte[] content);
  byte[] downloadObject(String key);
}

// In ecs-outbound-adapters/storage
@Component
public class AwsS3Repository implements StoragePort {
  private final S3Client s3Client;
  
  @Override
  public S3Object uploadObject(String key, byte[] content) {
    // Domain port interface implemented by adapter
    return new S3Object(key, content, "application/octet-stream");
  }
}

// ❌ WRONG - Adapter doesn't implement port
@Component
public class CustomRepository {  // Should implement StoragePort!
  // ...
}
```

---

### Rule 4: Configuration Lives ONLY in Application Module ✅
**Module:** `ecs-application/`

ALL Spring `@Configuration`, bean definitions, and dependency injection MUST be in the application module.

```java
// In ecs-application/config
@Configuration
public class StorageConfiguration {
  
  @Bean
  @ConditionalOnProperty(name = "storage.provider", havingValue = "aws")
  public StoragePort awsS3Repository(S3Client s3Client) {
    return new AwsS3Repository(s3Client);
  }
  
  @Bean
  public FileServicePort fileService(StoragePort storagePort) {
    return new FileService(storagePort);
  }
}

// ❌ WRONG - Configuration in adapter
@RestController
@Configuration  // NO!
public class FileController {
  @Bean  // NO!
  public StoragePort createStorage() {
    return new AwsS3Repository(...);
  }
}
```

---

### Rule 5: No Adapter-to-Adapter Dependencies ✅
**Enforcement:** ArchUnit prevents direct adapter-to-adapter calls

Adapters communicate ONLY through domain ports.

```
❌ WRONG
REST Controller → AWS S3 Repository → Azure Blob Repository

✅ CORRECT
REST Controller → FileServicePort → StoragePort → AWS S3 Repository
                                 → StoragePort → Azure Blob Repository
```

---

## 🗂️ Project Structure

```
ecs-local-demo/
├── ecs-core/                   # Core domain - ZERO Spring
│   ├── ports/
│   │   ├── FileServicePort.java
│   │   └── StoragePort.java
│   ├── domain/
│   │   ├── S3Object.java
│   │   └── exceptions/
│   └── usecases/
│       └── FileService.java    # NO Spring @Service!
│
├── ecs-inbound-adapters/       # REST, messaging, CLI
│   ├── rest/
│   │   └── FileController.java
│   └── messaging/
│       └── FileUploadListener.java
│
├── ecs-outbound-adapters/      # Repositories, external APIs
│   ├── storage/
│   │   ├── AwsS3Repository.java
│   │   ├── AzureBlobRepository.java
│   │   └── MinIORepository.java
│   └── logging/
│       └── CloudWatchLogger.java
│
├── ecs-application/            # Spring configuration ONLY
│   ├── config/
│   │   ├── StorageConfiguration.java
│   │   └── ControllerConfiguration.java
│   ├── Application.java
│   └── resources/
│       ├── application.yml
│       ├── application-amazon.yml
│       ├── application-azure.yml
│       └── application-local-minio.yml
│
├── ecs-tests/                  # All tests
│   ├── arch/
│   │   └── HexagonalArchitectureTest.java
│   ├── unit/
│   ├── integration/
│   └── acceptance/
│
└── pom.xml
```

---

## 📋 When Writing Code

### Core Domain Logic (ecs-core/)
- ✅ Write pure Java, NO Spring
- ✅ Define port interfaces
- ✅ Implement use cases
- ✅ Create domain objects and exceptions
- ❌ NO @Component, @Service, @Repository, @Autowired
- ❌ NO Spring imports

**Example:**
```java
// In ecs-core/usecases
public class FileService implements FileServicePort {
  private final StoragePort storagePort;
  
  public FileService(StoragePort storagePort) {
    this.storagePort = storagePort;
  }
  
  @Override
  public S3Object uploadFile(byte[] content) {
    return storagePort.uploadObject("file-key", content);
  }
}
```

### Inbound Adapters (ecs-inbound-adapters/)
- ✅ Use Spring web framework
- ✅ Inject inbound ports
- ✅ Convert HTTP/events to domain calls
- ✅ Call inbound ports (interfaces)
- ❌ NO direct outbound adapter calls
- ❌ NO business logic (that's in core)

**Example:**
```java
// In ecs-inbound-adapters/rest
@RestController
@RequestMapping("/api/files")
public class FileController {
  private final FileServicePort fileService;
  
  @PostMapping("/upload")
  public ResponseEntity<?> upload(@RequestParam MultipartFile file) throws IOException {
    S3Object result = fileService.uploadFile(file.getBytes());
    return ResponseEntity.created(...).build();
  }
}
```

### Outbound Adapters (ecs-outbound-adapters/)
- ✅ Implement outbound port interfaces
- ✅ Inject external clients (S3Client, BlobClient, etc.)
- ✅ Convert domain objects to/from external format
- ✅ Use @Component to register as bean
- ❌ NO business logic
- ❌ NO direct adapter-to-adapter calls

**Example:**
```java
// In ecs-outbound-adapters/storage
@Component
public class AwsS3Repository implements StoragePort {
  private final S3Client s3Client;
  private final String bucketName;
  
  public AwsS3Repository(S3Client s3Client, @Value("${aws.s3.bucket}") String bucketName) {
    this.s3Client = s3Client;
    this.bucketName = bucketName;
  }
  
  @Override
  public S3Object uploadObject(String key, byte[] content) {
    s3Client.putObject(PutObjectRequest.builder()
      .bucket(bucketName)
      .key(key)
      .build(), RequestBody.fromBytes(content));
    
    return new S3Object(key, content, "application/octet-stream");
  }
}
```

### Configuration (ecs-application/config/)
- ✅ Define @Configuration classes
- ✅ Create @Bean methods
- ✅ Wire adapters and ports
- ✅ Conditional bean creation based on profiles
- ❌ NO business logic
- ❌ NO controllers
- ❌ NO repositories

**Example:**
```java
// In ecs-application/config
@Configuration
public class StorageConfiguration {
  
  @Bean
  @ConditionalOnProperty(name = "storage.provider", havingValue = "aws")
  public StoragePort awsS3Repository(S3Client s3Client, @Value("${aws.s3.bucket}") String bucket) {
    return new AwsS3Repository(s3Client, bucket);
  }
  
  @Bean
  public FileServicePort fileService(StoragePort storagePort) {
    return new FileService(storagePort);
  }
}
```

---

## 🧪 Testing Strategy

### Unit Tests (ecs-tests/unit/)
- Test core domain logic WITHOUT Spring
- Mock dependencies via constructor injection
- NO TestContext, NO @SpringBootTest
- Use plain JUnit5 + Mockito

```java
class FileServiceTest {
  private StoragePort storageMock;
  private FileService fileService;
  
  @BeforeEach
  void setUp() {
    storageMock = mock(StoragePort.class);
    fileService = new FileService(storageMock);
  }
  
  @Test
  void shouldUploadFile() {
    // Arrange
    byte[] content = "test".getBytes();
    when(storageMock.uploadObject(any(), any()))
      .thenReturn(new S3Object("key", content, "text/plain"));
    
    // Act
    S3Object result = fileService.uploadFile(content);
    
    // Assert
    assertNotNull(result);
    verify(storageMock).uploadObject(any(), any());
  }
}
```

### Integration Tests (ecs-tests/integration/)
- Test adapters with real external services
- Use TestContainers for MinIO/localstack
- Test port implementations

```java
@Testcontainers
class AwsS3RepositoryIT {
  @Container
  static LocalStackContainer localstack = new LocalStackContainer()
    .withServices(S3);
  
  @Test
  void shouldUploadToS3() {
    // Test AwsS3Repository with real localstack
  }
}
```

### Architecture Tests (ecs-tests/arch/)
- Enforce hexagonal architecture rules
- Use ArchUnit
- Run on every build

```java
@AnalyzeClasses(packages = "com.example")
public class HexagonalArchitectureTest {
  
  @ArchTest
  public static final ArchRule core_no_spring =
    classes().that().resideInAPackage("..core..")
    .should().notDependOnClassesThat()
    .resideInAnyPackage("org.springframework..");
}
```

---

## 🚀 When Deploying

### Local Development
```bash
mvn clean package
docker-compose up -d
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local-minio"
```

### AWS ECS
```bash
# Build Docker image
mvn clean package -DskipTests -Dspring.profiles.active=amazon

# Push to ECR
aws ecr get-login-password | docker login --username AWS --password-stdin ECR_URI
docker tag ecs-local-demo:latest ECR_URI/ecs-local-demo:latest
docker push ECR_URI/ecs-local-demo:latest

# Deploy via GitHub Actions (CI/CD)
# Automatically triggered on push to main
```

### Azure ACI
```bash
# Build Docker image
mvn clean package -DskipTests -Dspring.profiles.active=azure

# Push to ACR
az acr login --name myacr
docker tag ecs-local-demo:latest myacr.azurecr.io/ecs-local-demo:latest
docker push myacr.azurecr.io/ecs-local-demo:latest

# Deploy via GitHub Actions (CI/CD)
```

---

## 📚 Technology Stack

- **Language:** Java 21
- **Framework:** Spring Boot 3.1.7
- **Build:** Maven 3.9+
- **Testing:** JUnit 5, Mockito, TestContainers, ArchUnit
- **Storage:** AWS S3 SDK v2, MinIO, Azure Storage SDK v12
- **Containerization:** Docker, Docker Compose
- **Infrastructure:** CloudFormation (AWS), Bicep (Azure)
- **CI/CD:** GitHub Actions
- **Architecture:** Hexagonal (Ports & Adapters)

---

## 🔍 Code Review Checklist

- [ ] Core modules have ZERO Spring imports
- [ ] Inbound adapters call inbound ports (interfaces)
- [ ] Outbound adapters implement outbound ports (interfaces)
- [ ] Configuration ONLY in ecs-application/config/
- [ ] ArchUnit tests pass
- [ ] No adapter-to-adapter dependencies
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Javadoc on all public APIs

---

## 📖 References

- **Hexagonal Architecture:** https://alistair.cockburn.us/hexagonal-architecture/
- **Ports & Adapters:** https://www.dddcommunity.org/resources/ddd_resources/
- **Clean Architecture:** https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html
- **ArchUnit:** https://www.archunit.org/
- **Spring Boot:** https://spring.io/projects/spring-boot

---

**Version:** 2.0 (Hexagonal Architecture)  
**Enforcement:** STRICT via ArchUnit  
**CI/CD Integration:** YES - blocks builds on violation


