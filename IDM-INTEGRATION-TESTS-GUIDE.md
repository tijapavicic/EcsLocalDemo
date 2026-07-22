# IDM Integration Tests Guide

**Date:** July 22, 2026  
**Status:** ✅ COMPLETE - Test Framework Ready

---

## 🎯 Integration Testing Strategy

This guide covers integration testing for the IDM (Identity and Access Management) implementation with Bearer token authentication.

---

## 📊 Test Coverage

### Integration Test Scenarios (13 tests)

#### ✅ Success Cases (4 tests)
1. **Upload with valid Bearer token** - Returns 201 with userId
2. **Upload as different user** - Verifies user isolation
3. **Multiple uploads from same user** - All in same user folder
4. **User isolation** - Two users upload files in separate folders

#### ❌ Error Cases (9 tests)
1. Missing Authorization header → 400 Bad Request
2. Invalid Bearer token (not Base64) → 400 Bad Request
3. Malformed token (no colon separator) → 400 Bad Request
4. Empty Bearer token → 400 Bad Request
5. Wrong Authorization prefix (Basic vs Bearer) → 400 Bad Request
6. Valid token but empty file → 400 Bad Request
7. Valid token but no file parameter → 400 Bad Request

---

## 🏗️ Test Architecture

### Technology Stack
- **Framework:** Spring Boot Test + TestContainers
- **Storage:** Real MinIO (TestContainers)
- **HTTP Client:** TestRestTemplate
- **Assertions:** AssertJ

### Test Layer

```
HTTP Request (Test)
        ↓
TestRestTemplate
        ↓
Spring Boot Application (Real)
        ↓
BearerTokenAuthenticationFilter
        ↓
FileController
        ↓
FileServiceImpl
        ↓
S3StorageAdapter
        ↓
Real MinIO (TestContainers)
```

---

## 📝 Integration Test Implementation

### Running Integration Tests

```bash
# Run all tests (unit + integration)
mvn clean verify

# Run only integration tests
mvn clean verify -Dtest='*IT'

# Run specific integration test
mvn clean verify -Dtest=FileUploadIT
```

### Test File Location

```
ecs-application/src/test/java/com/example/application/integration/
├── FileUploadIT.java (existing - file upload without auth)
├── FileUploadWithIDMIT.java (TODO - with Bearer token auth)
└── SecurityTestConfiguration.java (TODO - test security config)
```

---

## 📚 Example Integration Test Structure

### Basic Template

```java
@Testcontainers
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local-minio")
@DisplayName("FileController — IDM integration tests")
class FileUploadWithIDMIT {

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
        // Create MinIO bucket before tests
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Upload with Bearer token returns 201")
    void uploadWithBearerToken_returns201() {
        // Arrange: Create Bearer token
        String token = createBearerToken("user123", "user@example.com");
        
        // Act: Upload file
        ResponseEntity<StorageObject> response = restTemplate.postForEntity(
                "/api/files/upload",
                createRequestWithAuth(token, fileContent),
                StorageObject.class
        );
        
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getUserId()).isEqualTo("user123");
    }
    
    private String createBearerToken(String userId, String email) {
        String raw = userId + ":" + email;
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }
}
```

---

## 🧪 What to Test

### Bearer Token Authentication
- ✅ Valid token format accepted
- ✅ Invalid Base64 rejected
- ✅ Missing colon separator rejected
- ✅ Empty token rejected
- ✅ Wrong prefix (Basic vs Bearer) rejected

### User Isolation
- ✅ Files stored in user-scoped paths
- ✅ Different users have separate folders
- ✅ userId included in response
- ✅ Multiple uploads from same user in same folder

### Error Handling
- ✅ Missing Authorization header returns 400
- ✅ Invalid token returns 400
- ✅ Empty file returns 400
- ✅ No file parameter returns 400

### File Storage
- ✅ File persisted in MinIO
- ✅ File metadata correct (size, content type)
- ✅ Storage path follows pattern: `users/{userId}/yyyy-MM-dd/uuid-filename`

---

## 🚀 Implementation Checklist

### Phase 1: Test Setup (Already Done)
- [x] Maven Failsafe plugin configured in pom.xml
- [x] TestContainers dependency added
- [x] Test directory structure created

### Phase 2: Integration Tests (Ready to Implement)
- [ ] Create FileUploadWithIDMIT.java
  - [ ] Test valid Bearer token upload
  - [ ] Test user isolation
  - [ ] Test error cases
  - [ ] Verify file storage in MinIO

### Phase 3: Security Configuration for Tests
- [ ] Create SecurityTestConfiguration.java
  - [ ] Disable Spring Security for tests
  - [ ] Allow Bearer token interceptor to work

### Phase 4: Verification
- [ ] All 13 integration tests pass
- [ ] ArchUnit rules still pass (4/4)
- [ ] Unit tests still pass (11/11)
- [ ] Total: 28 tests passing

---

## 📊 Test Results Expected

### Before Integration Tests
```
mvn clean verify
Results: 15 tests passed
  - Unit tests: 11/11 ✅
  - ArchUnit tests: 4/4 ✅
```

### After Integration Tests
```
mvn clean verify
Results: 28 tests passed
  - Unit tests: 11/11 ✅
  - ArchUnit tests: 4/4 ✅
  - Integration tests (IDM): 13/13 ✅ (new)
```

---

## 🔧 Common Issues & Solutions

### Issue: 403 Forbidden in Integration Tests
**Cause:** Spring Security blocking requests  
**Solution:** 
- Disable CSRF for tests
- Use SecurityTestConfiguration to permit all requests
- Or use test authorization headers properly

### Issue: TestContainers timeout
**Cause:** MinIO container slow to start  
**Solution:**
- Increase timeout: `withStartupTimeout(Duration.ofSeconds(60))`
- Verify Docker is running
- Check network connectivity

### Issue: Bearer token not recognized
**Cause:** Filter not processing token correctly  
**Solution:**
- Verify TokenExtractorPort is injected
- Check BearerTokenAuthenticationFilter is registered
- Verify base64 encoding format

---

## 📚 Integration Test Examples

### Test 1: Valid Upload with Bearer Token

```java
@Test
@DisplayName("Upload with valid Bearer token")
void uploadWithValidBearerToken_returns201WithUserId() {
    // Arrange
    String token = createBearerToken("alice", "alice@example.com");
    byte[] content = "Test content".getBytes();
    
    // Act
    ResponseEntity<StorageObject> response = uploadFile(token, "document.pdf", content);
    
    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().getUserId()).isEqualTo("alice");
    assertThat(response.getBody().getKey()).startsWith("users/alice/");
}
```

### Test 2: User Isolation

```java
@Test
@DisplayName("Different users - files in separate folders")
void userIsolation_twoUsersUpload_filesInSeparateFolders() {
    // Arrange & Act
    StorageObject file1 = uploadFile(token1, "file1.txt", content1);
    StorageObject file2 = uploadFile(token2, "file2.txt", content2);
    
    // Assert
    assertThat(file1.getKey()).startsWith("users/alice/");
    assertThat(file2.getKey()).startsWith("users/bob/");
    assertThat(file1.getUserId()).isEqualTo("alice");
    assertThat(file2.getUserId()).isEqualTo("bob");
}
```

### Test 3: Error - Missing Token

```java
@Test
@DisplayName("Missing Authorization header - 400")
void missingAuthorizationHeader_returns400() {
    // Arrange: No Authorization header
    
    // Act
    ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/files/upload",
            createRequestWithoutAuth(content),
            String.class
    );
    
    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
}
```

---

## 🏃 Running the Tests

### 1. Prerequisites
```bash
# Verify Docker is running
docker --version

# Verify Maven
mvn --version

# Verify Java 21
java --version
```

### 2. Build & Run
```bash
# Clean build
mvn clean compile

# Run all tests (including integration)
mvn clean verify

# Watch for output
# Should see:
# - ecs-application module runs integration-test phase
# - TestContainers starts MinIO container
# - 13 integration tests execute
# - All tests pass ✅
```

### 3. Check Results
```bash
# View test reports
open target/failsafe-reports/  # or your file viewer

# Or from command line
mvn verify -X | grep -i "FileUpload"
```

---

## 📝 Future Enhancements

### Integration Tests Phase 2
- [ ] Token expiration tests
- [ ] Concurrent user uploads
- [ ] Large file uploads (>100MB)
- [ ] Different content types
- [ ] File collision handling

### Integration Tests Phase 3
- [ ] Rate limiting per userId
- [ ] Quota enforcement
- [ ] File deletion and permissions
- [ ] Sharing between users
- [ ] JWT token support (instead of Base64)

---

## ✅ Quality Metrics

| Metric | Target | Status |
|--------|--------|--------|
| Unit Tests | 100% passing | ✅ 11/11 |
| Integration Tests | 100% passing | 🔄 Ready (13 tests) |
| ArchUnit Rules | 100% passing | ✅ 4/4 |
| Code Coverage | >80% | 📊 TBD |
| Build Time | <60s | ✅ ~3s |

---

## 🎓 Learning Resources

### Spring Boot Testing
- https://spring.io/guides/gs/testing-web/
- https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing

### TestContainers
- https://www.testcontainers.org/
- https://www.testcontainers.org/modules/gettingstarted/

### JUnit 5
- https://junit.org/junit5/docs/current/user-guide/

### AssertJ
- https://assertj.github.io/assertj-core/

---

**Status:** ✅ Integration Test Framework Ready to Implement  
**Next Step:** Create FileUploadWithIDMIT.java with 13 test methods  
**Expected:** All 13 tests passing with real MinIO container

