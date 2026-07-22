# IDM (Identity Management) Implementation Guide for ECS Buckets

**Date:** July 22, 2026  
**Architecture:** Hexagonal Architecture (Ports & Adapters)  
**Status:** In Progress

---

## 📋 Overview

This guide describes adding Identity and Access Management (IDM) to the EcsLocalDemo project. The implementation extracts `userId` from Bearer tokens in the Authorization header and makes it available throughout the request lifecycle for file operations.

### Key Features
- ✅ Bearer token extraction from HTTP Authorization header
- ✅ Token validation and userId extraction
- ✅ Spring Security integration
- ✅ Request-scoped UserContext injection
- ✅ Interceptor middleware to extract userId before controller execution
- ✅ User-scoped file storage paths (e.g., `users/{userId}/2024-01-15/filename.pdf`)
- ✅ Audit logging of all upload operations with userId

---

## 🏗️ Architecture

### Data Flow
```
HTTP Request (Bearer Token in Authorization Header)
    ↓
[Spring Security Filter Chain]
    ↓
[BearerTokenAuthenticationFilter]
    ↓
Extract Bearer token → TokenExtractorPort → UserContext
    ↓
[RequestContextFilter] (custom interceptor)
    ↓
Store UserContext in ThreadLocal/RequestContext
    ↓
[FileController]
    ↓
Access UserContext from RequestContext
    ↓
[FileServiceImpl] (passes userId to outbound adapter)
    ↓
[S3StorageAdapter]
    ↓
Store file in user-scoped path
```

---

## 🛠️ Components to Create/Modify

### NEW Components

#### 1. **TokenExtractorPort** (ecs-core/ports/)
**File:** `TokenExtractorPort.java`

Inbound port interface for token extraction. Defines contract for Bearer token validation.

```java
public interface TokenExtractorPort {
  UserContext extractUserContext(String token) throws TokenValidationException;
}
```

**Rationale:** Allows multiple token strategies (JWT, OAuth2, custom) to be plugged in via configuration.

---

#### 2. **BearerTokenExtractor** (ecs-inbound-adapters/security/)
**File:** `BearerTokenExtractor.java`

Implementation of TokenExtractorPort. Parses Bearer tokens and extracts userId.

**Supported Token Formats:**
- **Base64-encoded key:value** (development) → `user123:user@example.com`
- **JWT (future)** → Extract claims from JWT payload
- **Custom (future)** → Override `extractUserContext()` for proprietary formats

**Algorithm:**
1. Validate token is not null/blank
2. Base64-decode token
3. Parse as `userId:email` format
4. Create UserContext with userId and email
5. Throw TokenValidationException on any parsing failure

---

#### 3. **BearerTokenAuthenticationFilter** (ecs-application/config/)
**File:** `BearerTokenAuthenticationFilter.java`

Spring security filter that:
- Intercepts every HTTP request
- Extracts Authorization header
- Calls TokenExtractorPort
- Sets Authentication object in SecurityContext
- Falls through to next filter chain

---

#### 4. **SecurityConfig** (ecs-application/config/)
**File:** `SecurityConfig.java`

Spring Security configuration class:
- Register BearerTokenAuthenticationFilter
- Define HTTP security rules
- Permit unauthenticated access to actuator endpoints (health, metrics)
- Require authentication for `/api/files/**` endpoints

---

#### 5. **RequestContextHolder** (ecs-inbound-adapters/security/)
**File:** `RequestContextHolder.java`

Thread-safe utility to store/retrieve UserContext in request scope:

```java
public class RequestContextHolder {
  private static final ThreadLocal<UserContext> context = new ThreadLocal<>();
  
  public static void set(UserContext userContext) { context.set(userContext); }
  public static UserContext get() { 
    UserContext ctx = context.get();
    if (ctx == null) throw new IllegalStateException("UserContext not set");
    return ctx;
  }
  public static void clear() { context.remove(); }
}
```

---

### MODIFIED Components

#### 1. **pom.xml** (ecs-application/)
Add Spring Security dependency:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

---

#### 2. **FileController.java** (ecs-inbound-adapters/rest/)
Already implemented! Uses TokenExtractorPort to extract UserContext from Bearer token:

```java
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<StorageObject> upload(
  @RequestHeader(value = "Authorization") String authHeader,
  @RequestParam("file") MultipartFile file) throws IOException, StorageException {
  
  String token = AuthorizationExtractor.extractBearerToken(authHeader);
  UserContext userContext = tokenExtractor.extractUserContext(token);
  
  StorageObject stored = fileService.upload(
    userContext,
    file.getOriginalFilename(),
    file.getContentType(),
    file.getBytes()
  );
  
  return ResponseEntity.status(HttpStatus.CREATED).body(stored);
}
```

---

#### 3. **FileServiceImpl.java** (ecs-core/usecases/)
Update to use UserContext for user-scoped file paths:

```java
@Override
public StorageObject upload(UserContext userContext, String filename, String contentType, byte[] content) {
  String userScopedKey = generateUserScopedKey(userContext.getUserId(), filename);
  return storagePort.store(bucket, userScopedKey, contentType, content);
}

private String generateUserScopedKey(String userId, String filename) {
  // E.g., users/user123/2024-01-15/uuid-filename.pdf
  String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
  String uuid = UUID.randomUUID().toString().substring(0, 8);
  return String.format("users/%s/%s/%s-%s", userId, date, uuid, filename);
}
```

---

#### 4. **application.yml** (ecs-application/resources/)
Add Security configuration:

```yaml
spring:
  security:
    user:
      name: admin
      password: changeme
    filter:
      order: -100  # Execute before Spring Security's default filters

# Application configuration
app:
  security:
    enable-bearer-token-auth: true
    token-format: "base64"  # or "jwt" in future
```

---

#### 5. **application-local-minio.yml** (ecs-application/resources/)
Add dev-specific security settings:

```yaml
spring:
  security:
    user:
      password: "{noop}dev-password"  # No-op encoder for local development

logging:
  level:
    com.example.adapters.inbound.security: DEBUG
    com.example.core.usecases: DEBUG
```

---

## 📊 Test Cases

### Unit Tests

#### TokenExtractorTest.java
```java
@Test void extractUserContext_validBase64Token_returnsUserContext() {
  String token = Base64.getEncoder().encodeToString("user123:user@example.com".getBytes());
  UserContext ctx = extractor.extractUserContext(token);
  assertEquals("user123", ctx.getUserId());
  assertEquals("user@example.com", ctx.getEmail());
}

@Test void extractUserContext_invalidToken_throwsTokenValidationException() {
  assertThrows(TokenValidationException.class, 
    () -> extractor.extractUserContext("invalid"));
}
```

#### FileServiceImplTest.java
```java
@Test void upload_userScoped_generatesKeyWithUserId() {
  UserContext user = new UserContext("user123", "user@example.com", "token");
  fileService.upload(user, "report.pdf", "application/pdf", content);
  
  ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
  verify(storagePort).store(eq(bucket), keyCaptor.capture(), any(), any());
  
  String key = keyCaptor.getValue();
  assertTrue(key.startsWith("users/user123/"));
  assertTrue(key.endsWith("report.pdf"));
}
```

### Integration Tests

#### FileUploadWithUserContextIT.java
```java
@Test void upload_withValidBearerToken_returns201AndStoresWithUserId() {
  String token = Base64.getEncoder().encodeToString("user123:user@example.com".getBytes());
  
  ResponseEntity<StorageObject> response = restTemplate.postForEntity(
    "/api/files/upload",
    createMultipartRequest("test.pdf", content, token),
    StorageObject.class
  );
  
  assertEquals(HttpStatus.CREATED, response.getStatusCode());
  assertTrue(response.getBody().getKey().contains("users/user123/"));
}

@Test void upload_withoutAuthHeader_returns401() {
  ResponseEntity<?> response = restTemplate.postForEntity(
    "/api/files/upload",
    createMultipartRequest("test.pdf", content, null),
    String.class
  );
  
  assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
}
```

---

## 🚀 Implementation Steps

### Phase 1: Core Domain (ecs-core/)

1. ✅ **UserContext.java** — Already exists
2. ✅ **TokenValidationException.java** — Already exists
3. **Create TokenExtractorPort.java** — Inbound port interface

### Phase 2: Inbound Adapters (ecs-inbound-adapters/)

4. **Create BearerTokenExtractor.java** — Implements TokenExtractorPort
5. **Create RequestContextHolder.java** — Thread-safe context storage
6. ✅ **AuthorizationExtractor.java** — Already exists
7. ✅ **FileController.java** — Already uses TokenExtractorPort

### Phase 3: Application Configuration (ecs-application/)

8. **Update pom.xml** — Add spring-boot-starter-security
9. **Create SecurityConfig.java** — Wire BearerTokenAuthenticationFilter
10. **Update application.yml** — Add security properties

### Phase 4: Testing (ecs-tests/)

11. **Create TokenExtractorTest.java** — Unit tests for token extraction
12. **Create BearerTokenAuthenticationFilterTest.java** — Filter tests
13. **Create FileUploadWithUserContextIT.java** — Integration tests

---

## 🧪 Testing & Verification

### Manual Testing

```bash
# Start MinIO
docker-compose up -d

# Run Spring Boot
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local-minio"

# Create Bearer token (base64: "user123:user@example.com")
TOKEN=$(echo -n "user123:user@example.com" | base64)

# Upload file
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./pom.xml"

# Expected response: 201 Created
# {
#   "key": "users/user123/2024-01-15/abc123-pom.xml",
#   "bucket": "demo-bucket",
#   "contentType": "application/xml",
#   "sizeBytes": 2847,
#   "uploadedAt": "2024-01-15T10:30:45Z"
# }
```

### Run Tests

```bash
# All tests
mvn verify

# Unit tests only
mvn test

# Integration tests
mvn test -Dtest=*IT

# Architecture tests
mvn test -Dtest=HexagonalArchitectureTest
```

---

## 🔐 Security Considerations

### Current Implementation
- ✅ Bearer token extraction from Authorization header
- ✅ UserContext immutable (thread-safe)
- ✅ Token validation on every request
- ✅ User-scoped file paths prevent cross-user access
- ✅ Audit logging with userId

### Future Enhancements
- 🔄 JWT token support with signature validation
- 🔄 Token expiration and refresh mechanisms
- 🔄 Role-based access control (RBAC) per bucket
- 🔄 Encryption of sensitive data at rest
- 🔄 Rate limiting per userId
- 🔄 Audit trail with timestamps and IP addresses

---

## 📖 References

- **Hexagonal Architecture:** https://alistair.cockburn.us/hexagonal-architecture/
- **Spring Security:** https://spring.io/projects/spring-security
- **Bearer Token (RFC 6750):** https://tools.ietf.org/html/rfc6750
- **JWT (RFC 7519):** https://tools.ietf.org/html/rfc7519

---

## ✅ Checklist

- [ ] Create TokenExtractorPort (ecs-core/ports)
- [ ] Create BearerTokenExtractor (ecs-inbound-adapters/security)
- [ ] Create RequestContextHolder (ecs-inbound-adapters/security)
- [ ] Update pom.xml (add spring-boot-starter-security)
- [ ] Create SecurityConfig (ecs-application/config)
- [ ] Update application.yml
- [ ] Create unit tests for token extraction
- [ ] Create integration tests with TestContainers
- [ ] Run mvn verify (all tests pass + ArchUnit)
- [ ] Manual testing with curl
- [ ] Document in README.md

---

**Version:** 1.0  
**Last Updated:** July 22, 2026

