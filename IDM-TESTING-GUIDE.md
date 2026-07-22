# IDM Testing & Verification Guide

**Quick Start:** Manual testing with curl commands for IDM functionality

---

## 🚀 Quick Start (5 minutes)

### Step 1: Start MinIO
```bash
cd /Users/copor/IdeaProjects/EcsLocalDemo
docker-compose up -d
sleep 3  # Wait for MinIO to start

# Verify MinIO is running
curl http://localhost:9001
# Should return MinIO web UI login page
```

### Step 2: Build & Run Spring Boot
```bash
cd /Users/copor/IdeaProjects/EcsLocalDemo

# Build
mvn clean package -DskipTests

# Run with local-minio profile
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local-minio"

# Spring Boot should start on http://localhost:8080
# Watch for: "Started Application in X seconds"
```

### Step 3: Test with Bearer Token
```bash
# In another terminal:

# Create Bearer token: base64("user123:user@example.com")
TOKEN=$(echo -n "user123:user@example.com" | base64)
echo "Token: $TOKEN"

# Upload file with Bearer token (SUCCESS - 201)
curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./pom.xml"

# Expected response:
# HTTP/1.1 201 Created
# {
#   "key": "users/user123/2024-07-22/abc123-pom.xml",
#   "bucket": "demo-bucket",
#   "contentType": "application/xml",
#   "sizeBytes": 2847,
#   "uploadedAt": "2024-07-22T07:30:45Z",
#   "userId": "user123"
# }
```

---

## 📋 Test Cases

### Test Case 1: Valid Bearer Token (Success - 201)
```bash
TOKEN=$(echo -n "user1:john@example.com" | base64)

curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./pom.xml"

# Expected:
# ✅ HTTP/1.1 201 Created
# ✅ Response includes userId: "user1"
# ✅ Key format: users/user1/yyyy-MM-dd/uuid-filename
```

### Test Case 2: Different User (Verify User Scoping)
```bash
TOKEN=$(echo -n "user2:alice@example.com" | base64)

curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./README.md"

# Expected:
# ✅ HTTP/1.1 201 Created
# ✅ Response includes userId: "user2"
# ✅ Key starts with: users/user2/
# ✅ Different from user1's files (users/user1/)
```

### Test Case 3: Missing Authorization Header (Fail - 400)
```bash
curl -v -X POST http://localhost:8080/api/files/upload \
  -F "file=@./pom.xml"

# Expected:
# ❌ HTTP/1.1 400 Bad Request
# ❌ Error: "Authorization header is missing"
```

### Test Case 4: Invalid Bearer Token (Fail - 400)
```bash
curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer invalid_not_base64" \
  -F "file=@./pom.xml"

# Expected:
# ❌ HTTP/1.1 400 Bad Request
# ❌ Error: "Invalid token encoding"
```

### Test Case 5: Malformed Bearer Token (not user:email format)
```bash
# Create base64 token WITHOUT colon separator
MALFORMED=$(echo -n "justauserwithoutcolon" | base64)

curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $MALFORMED" \
  -F "file=@./pom.xml"

# Expected:
# ❌ HTTP/1.1 400 Bad Request
# ❌ Error: "Invalid token format"
```

### Test Case 6: Empty Bearer Token (Fail - 400)
```bash
curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer" \
  -F "file=@./pom.xml"

# Expected:
# ❌ HTTP/1.1 400 Bad Request
# ❌ Error: "Bearer token is empty"
```

### Test Case 7: Wrong Prefix (not "Bearer ")
```bash
TOKEN=$(echo -n "user123:user@example.com" | base64)

curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Basic $TOKEN" \
  -F "file=@./pom.xml"

# Expected:
# ❌ HTTP/1.1 400 Bad Request
# ❌ Error: "Authorization header must start with 'Bearer '"
```

### Test Case 8: Empty File (Fail - 400)
```bash
TOKEN=$(echo -n "user123:user@example.com" | base64)

# Create empty file
touch empty.txt

curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./empty.txt"

# Expected:
# ❌ HTTP/1.1 400 Bad Request
# ❌ Error: "file is empty"
```

### Test Case 9: Multiple Files (Only One Expected)
```bash
TOKEN=$(echo -n "user123:user@example.com" | base64)

curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./pom.xml" \
  -F "file=@./README.md"

# Expected:
# ⚠️ Only first file is processed (form field name: "file", not "files")
```

### Test Case 10: Large File
```bash
TOKEN=$(echo -n "user123:user@example.com" | base64)

# Create 50MB test file
dd if=/dev/zero of=large_file.bin bs=1M count=50

curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./large_file.bin"

# Expected:
# ✅ HTTP/1.1 201 Created
# ✅ sizeBytes: 52428800
```

---

## 🔍 Verify File Storage

### MinIO Web Console
1. Open http://localhost:9001
2. Login: minioadmin / minioadmin
3. Browse bucket: "demo-bucket"
4. See files organized by userId:
   ```
   demo-bucket/
   ├── users/
   │   ├── user1/
   │   │   └── 2024-07-22/
   │   │       ├── a1b2c3d4-pom.xml
   │   │       └── e5f6g7h8-README.md
   │   ├── user2/
   │   │   └── 2024-07-22/
   │   │       └── i9j0k1l2-report.pdf
   │   └── user123/
   │       └── 2024-07-22/
   │           └── m3n4o5p6-presentation.pptx
   ```

### Check S3 via AWS CLI (Optional)
```bash
# Configure AWS CLI for MinIO
aws s3 --endpoint-url http://localhost:9000 ls s3://demo-bucket/users/ --recursive

# Should show files organized by userId and date
```

---

## 📊 Log Verification

### Watch Spring Boot Logs
```bash
# In the Spring Boot terminal, look for:

# 1. Filter processing
[DEBUG] Bearer token authenticated: userId=user123

# 2. Token extraction
[INFO] Upload request: userId=user123 filename=pom.xml size=2847

# 3. File service
[DEBUG] Storing object: bucket=demo-bucket key=users/user123/...

# 4. Storage success
[INFO] Upload success: userId=user123 key=users/user123/2024-07-22/abc-pom.xml
```

### Check Request/Response Flow
```bash
# Add verbose logging to see full HTTP exchange
TOKEN=$(echo -n "user123:user@example.com" | base64)

curl -vvv -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./pom.xml" 2>&1 | head -50
```

---

## 🧪 Automated Test Suite

### Run All Tests
```bash
# Compile + unit tests + integration tests + ArchUnit
mvn verify

# Expected output:
# [INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
# [INFO] BUILD SUCCESS
```

### Run Specific Test
```bash
# Test FileController
mvn test -Dtest=FileControllerTest

# Test FileServiceImpl
mvn test -Dtest=FileServiceImplTest

# Test architecture rules
mvn test -Dtest=HexagonalArchitectureTest
```

---

## 🔧 Troubleshooting

### Issue: "Connection refused" when uploading
```
Solution: Verify Spring Boot is running on http://localhost:8080
  - Check Spring Boot terminal for "Started Application"
  - Verify MinIO is running: docker ps | grep minio
  - Restart: docker-compose restart
```

### Issue: "Authorization header is missing"
```
Solution: Always include Bearer token in Authorization header
  - Correct: curl -H "Authorization: Bearer $TOKEN" ...
  - Wrong:   curl -H "Token: $TOKEN" ...
  - Wrong:   curl -H "token: $TOKEN" ...
```

### Issue: "Invalid token encoding"
```
Solution: Ensure token is valid Base64
  - Generate correctly: echo -n "user123:user@example.com" | base64
  - Verify: echo "dXNlcjEyMzp1c2VyQGV4YW1wbGUuY29t" | base64 -d
  - Should output: user123:user@example.com
```

### Issue: Files not appearing in MinIO
```
Solution: Check Spring Boot logs for storage errors
  - Look for [ERROR] in logs
  - Verify bucket exists: docker exec minio-container mc ls minio/demo-bucket
  - Check MinIO connectivity: curl http://localhost:9000
```

### Issue: "Token is blank"
```
Solution: Ensure Base64 token is not empty
  - Empty string encoded: echo -n "" | base64  →  "" (empty)
  - Fix: Use valid user:email format
  - Example: echo -n "user123:user@example.com" | base64
```

---

## 📝 Advanced Testing

### Test with Different Content Types
```bash
TOKEN=$(echo -n "user1:john@example.com" | base64)

# PDF file
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./document.pdf"

# Image file
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./photo.jpg"

# Archive file
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./backup.zip"

# All should return 201 Created with correct contentType
```

### Test with Special Characters in Filename
```bash
TOKEN=$(echo -n "user1:john@example.com" | base64)

# Create file with special characters
echo "test" > "My Document (2024) v2.txt"

curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./My Document (2024) v2.txt"

# Expected: Filename sanitized to "My_Document_2024__v2.txt" in key
```

### Performance Test (Multiple Users)
```bash
#!/bin/bash

for i in {1..10}; do
  USER="user$i"
  EMAIL="user$i@example.com"
  TOKEN=$(echo -n "$USER:$EMAIL" | base64)
  
  curl -s -X POST http://localhost:8080/api/files/upload \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@./pom.xml" | jq '.userId'
done

# Expected: user1, user2, user3, ... user10
```

---

## ✅ Verification Checklist

- [ ] MinIO is running (docker-compose up -d)
- [ ] Spring Boot is running on port 8080
- [ ] Test Case 1 passes (Valid Bearer Token → 201)
- [ ] Test Case 2 passes (Different user → separate folder)
- [ ] Test Case 3 passes (Missing header → 400)
- [ ] Test Case 4 passes (Invalid token → 400)
- [ ] Files appear in MinIO console under users/{userId}/
- [ ] Response includes userId field
- [ ] Spring Boot logs show Bearer token validation
- [ ] Run: mvn verify (all tests pass)

---

## 🧹 Cleanup

```bash
# Stop Spring Boot
# (Ctrl+C in Spring Boot terminal)

# Stop MinIO
docker-compose down

# Clean build artifacts
mvn clean

# Remove test files
rm -f empty.txt large_file.bin "My Document (2024) v2.txt"
```

---

**Last Updated:** July 22, 2026  
**Tested On:** macOS with Docker

