# Hexagonal Architecture Rules for EcsLocalDemo

**Enforced as of July 19, 2026**

---

## 🏗️ Architecture Principles

This project strictly follows **Hexagonal Architecture (Ports & Adapters)** pattern with ZERO exceptions.

---

## 📋 MANDATORY RULES

### Rule 1: Core Modules - ZERO Spring Dependencies ✅
**Location:** `ecs-core/`, `ecs-storage-core/`

```
ALLOWED:
✅ @FunctionalInterface
✅ @Value (Lombok)
✅ Custom annotations
✅ Java standard library
✅ Third-party domain libraries (Jackson, Lombok)

FORBIDDEN:
❌ @Component, @Service, @Repository
❌ @Autowired, @Inject
❌ Spring Framework imports
❌ Application context references
❌ Bean definitions
```

**Enforcement:** ArchUnit tests verify NO Spring dependencies in core modules

---

### Rule 2: Inbound Adapters Call Inbound Ports ✅
**Location:** `ecs-inbound-adapters/` (REST, WebSocket, CLI, etc.)

```
Pattern:
┌─────────────────┐
│  REST Controller│ (Inbound Adapter)
└────────┬────────┘
         │ calls
┌────────▼─────────┐
│  File Service    │ (Inbound Port/Use Case)
│  (Interface)     │
└────────┬─────────┘
         │ uses
┌────────▼──────────────┐
│ Domain Objects        │
│ (Entities, Values)    │
└───────────────────────┘
```

**Rules:**
- Adapter receives HTTP request
- Adapter calls inbound port (interface)
- Inbound port is domain logic (no framework)
- Domain returns domain objects
- Adapter converts to HTTP response

**Code Structure:**
```java
// ✅ CORRECT - Adapter calls port
@RestController
public class FileController {
  private final FileServicePort fileService;
  
  public FileController(FileServicePort fileService) {
    this.fileService = fileService;
  }
  
  @PostMapping("/upload")
  public ResponseEntity<?> upload(@RequestParam MultipartFile file) {
    S3Object result = fileService.uploadFile(file.getBytes());
    return ResponseEntity.created(...).body(result);
  }
}

// ❌ WRONG - Adapter calls another adapter
@RestController
public class FileController {
  private final AmazonS3 s3Client;  // VIOLATION!
  // ...
}
```

---

### Rule 3: Outbound Adapters Implement Outbound Ports ✅
**Location:** `ecs-outbound-adapters/` (Repositories, External APIs, etc.)

```
Pattern:
┌───────────────────────────┐
│ Domain (Use Case)         │ (Inbound Port)
└────────────┬──────────────┘
             │ uses
      ┌──────▼─────┐
      │ Port       │ (Interface - lives in core)
      │ (Interface)│
      └──────┬─────┘
             │ implemented by
┌────────────▼──────────────────┐
│ AwsS3Repository              │ (Outbound Adapter)
│ Implements StoragePort       │
└──────────────────────────────┘
             │ uses
      ┌──────▼────────────┐
      │ AWS S3 Client     │
      │ (External Library)│
      └───────────────────┘
```

**Rules:**
- Outbound port interface lives in `ecs-core`
- Outbound adapter implements port
- Adapter converts domain objects to/from external API format
- Domain NEVER calls external APIs directly

**Code Structure:**
```java
// In ecs-core/ports
public interface StoragePort {
  S3Object uploadObject(String key, byte[] content);
  byte[] downloadObject(String key);
}

// In ecs-outbound-adapters
@Component
public class AwsS3Repository implements StoragePort {
  private final S3Client s3Client;
  
  @Override
  public S3Object uploadObject(String key, byte[] content) {
    // Convert domain to AWS format
    PutObjectRequest request = PutObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .build();
    
    // Call AWS
    s3Client.putObject(request, RequestBody.fromBytes(content));
    
    // Convert back to domain
    return S3Object.builder().key(key).build();
  }
}

// ❌ WRONG - Domain calls external API directly
public class FileService {
  private final S3Client s3Client;  // VIOLATION!
  
  public void upload(String key, byte[] content) {
    s3Client.putObject(...);  // Domain touching external API
  }
}
```

---

### Rule 4: Configuration Lives ONLY in Application Module ✅
**Location:** `ecs-application/`

**What Goes in Application Module:**
- Spring `@Configuration` classes
- Bean definitions
- Dependency injection setup
- Spring profiles configuration
- `application-{profile}.yml`
- Main application class

**What Does NOT Go There:**
- Domain logic
- Adapter implementations
- Controllers (go in inbound-adapters)
- Repositories (go in outbound-adapters)

**Code Structure:**
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
  @ConditionalOnProperty(name = "storage.provider", havingValue = "azure")
  public StoragePort azureBlobRepository(BlobClient blobClient) {
    return new AzureBlobRepository(blobClient);
  }
  
  @Bean
  public FileServicePort fileService(StoragePort storagePort) {
    return new FileService(storagePort);
  }
}

// ❌ WRONG - Configuration in adapter
@RestController
@Component
public class FileController {
  @Bean  // VIOLATION!
  public StoragePort createStorage() {
    return new AwsS3Repository(...);
  }
}
```

---

### Rule 5: No Adapter-to-Adapter Dependencies ✅
**Enforcement:** ArchUnit prevents direct calls between adapters

**Pattern:**
```
❌ WRONG - Adapter to Adapter
REST Controller → AWS S3 Repository → Azure Blob Repository

✅ CORRECT - All through Domain Port
REST Controller → File Service Port → Storage Port → AWS S3 Repository
             ↓ (adapter only)
             → Storage Port → Azure Blob Repository
             ↓ (adapter only)
```

**Code Structure:**
```java
// ✅ CORRECT - REST adapter calls use case
@RestController
public class FileController {
  private final FileServicePort fileService;  // ✅ Port (domain)
  
  @PostMapping("/upload")
  public ResponseEntity<?> upload(@RequestParam MultipartFile file) {
    return ResponseEntity.ok(fileService.uploadFile(file.getBytes()));
  }
}

// ❌ WRONG - REST adapter calls storage adapter directly
@RestController
public class FileController {
  private final AwsS3Repository s3Repo;  // ❌ Adapter
  private final AzureBlobRepository azureRepo;  // ❌ Adapter
  
  @PostMapping("/upload")
  public ResponseEntity<?> upload(@RequestParam MultipartFile file) {
    // Which adapter to use? This violates hexagonal architecture
    return ResponseEntity.ok(s3Repo.uploadObject(...));
  }
}
```

---

## 🗂️ Project Structure

```
ecs-local-demo/
│
├── ecs-core/                          # Core domain - ZERO Spring
│   ├── ports/
│   │   ├── FileServicePort.java       # Inbound port (use case)
│   │   └── StoragePort.java           # Outbound port
│   ├── domain/
│   │   ├── S3Object.java
│   │   ├── StorageException.java
│   │   └── StorageProvider.java
│   ├── usecases/                      # Inbound port implementations
│   │   └── FileService.java           # Business logic (NO Spring)
│   └── pom.xml                        # NO spring-core, spring-context
│
├── ecs-inbound-adapters/              # REST, messaging, etc.
│   ├── rest/
│   │   ├── FileController.java        # REST adapter
│   │   └── FileControllerExceptionHandler.java
│   ├── messaging/
│   │   └── FileUploadListener.java    # Message adapter
│   └── pom.xml                        # spring-boot-starter-web
│
├── ecs-outbound-adapters/             # Repositories, external APIs
│   ├── storage/
│   │   ├── AwsS3Repository.java       # AWS adapter
│   │   ├── AzureBlobRepository.java   # Azure adapter
│   │   └── MinIORepository.java       # MinIO adapter
│   ├── logging/
│   │   └── CloudWatchLogger.java
│   └── pom.xml                        # spring-boot-starter-data-jpa, aws-java-sdk, etc.
│
├── ecs-application/                   # Spring configuration
│   ├── config/
│   │   ├── StorageConfiguration.java
│   │   ├── ControllerConfiguration.java
│   │   └── ProfileConfiguration.java
│   ├── Application.java               # @SpringBootApplication
│   ├── resources/
│   │   ├── application.yml
│   │   ├── application-amazon.yml
│   │   ├── application-azure.yml
│   │   └── application-local-minio.yml
│   └── pom.xml                        # Parent pom imports all modules
│
├── ecs-infrastructure/                # Docker, Kubernetes, IaC
│   ├── docker/
│   ├── k8s/
│   └── terraform/
│
├── ecs-tests/                         # All tests
│   ├── unit/                          # Unit tests (no Spring)
│   ├── integration/                   # Integration tests
│   ├── acceptance/                    # API tests
│   └── arch/                          # ArchUnit tests (enforce rules)
│
└── pom.xml                            # Parent pom
```

---

## 🔍 ArchUnit Enforcement Tests

**File:** `ecs-tests/arch/HexagonalArchitectureTest.java`

```java
@AnalyzeClasses(packages = "com.example")
public class HexagonalArchitectureTest {

  // Rule 1: Core has NO Spring dependencies
  @ArchTest
  public static final ArchRule core_should_not_depend_on_spring =
    classes()
      .that().resideInAPackage("..core..")
      .should().notDependOnClassesThat()
      .resideInAnyPackage("org.springframework..")
      .because("Core modules must be framework-agnostic");

  // Rule 2: Inbound adapters call inbound ports
  @ArchTest
  public static final ArchRule inbound_adapters_call_ports =
    classes()
      .that().resideInAPackage("..inbound_adapters..")
      .should().dependOnClassesThat()
      .resideInAPackage("..ports..")
      .because("Inbound adapters must use ports");

  // Rule 3: Outbound adapters implement ports
  @ArchTest
  public static final ArchRule outbound_adapters_implement_ports =
    classes()
      .that().areAssignableTo(StoragePort.class)
      .and().resideInAPackage("..outbound_adapters..")
      .should().implement(StoragePort.class)
      .because("Outbound adapters must implement outbound ports");

  // Rule 4: No adapter-to-adapter dependencies
  @ArchTest
  public static final ArchRule no_adapter_to_adapter_dependencies =
    classes()
      .that().resideInAPackage("..inbound_adapters..")
      .should().notDependOnClassesThat()
      .resideInAPackage("..outbound_adapters..")
      .because("Adapters must communicate through ports");

  // Rule 5: Configuration only in application module
  @ArchTest
  public static final ArchRule configuration_only_in_application =
    classes()
      .that().areAnnotatedWith(Configuration.class)
      .should().resideInAPackage("..application.config..")
      .because("Spring @Configuration must be in application module");
}
```

---

## 📝 Dependency Declaration Order

```xml
<!-- Parent pom.xml -->
<modules>
  <module>ecs-core</module>
  <module>ecs-inbound-adapters</module>
  <module>ecs-outbound-adapters</module>
  <module>ecs-application</module>
  <module>ecs-infrastructure</module>
  <module>ecs-tests</module>
</modules>

<!-- ecs-application/pom.xml depends on all others -->
<dependencies>
  <dependency>
    <groupId>com.example</groupId>
    <artifactId>ecs-core</artifactId>
  </dependency>
  <dependency>
    <groupId>com.example</groupId>
    <artifactId>ecs-inbound-adapters</artifactId>
  </dependency>
  <dependency>
    <groupId>com.example</groupId>
    <artifactId>ecs-outbound-adapters</artifactId>
  </dependency>
</dependencies>
```

---

## 🚀 Implementation Checklist

- [ ] Create module structure (6 modules)
- [ ] Move domain logic to `ecs-core` (remove all Spring)
- [ ] Create inbound port interfaces
- [ ] Create outbound port interfaces
- [ ] Move controllers to `ecs-inbound-adapters`
- [ ] Move repositories to `ecs-outbound-adapters`
- [ ] Create `StorageConfiguration` in `ecs-application`
- [ ] Create ArchUnit tests
- [ ] Run tests to verify enforcement
- [ ] Update CI/CD workflows

---

## ✅ Compliance Checklist

Before committing:
- [ ] No Spring imports in `ecs-core/`
- [ ] All adapters call ports, never other adapters
- [ ] All outbound adapters implement ports
- [ ] Configuration only in `ecs-application/`
- [ ] ArchUnit tests pass
- [ ] All tests pass

---

## 📚 References

- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Ports & Adapters Pattern](https://www.youtube.com/watch?v=th4AgBcrEHA)
- [ArchUnit Documentation](https://www.archunit.org/)
- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

---

**Last Updated:** July 19, 2026  
**Enforcement Level:** STRICT (ArchUnit tests required to pass)  
**Violations:** Will block CI/CD builds

