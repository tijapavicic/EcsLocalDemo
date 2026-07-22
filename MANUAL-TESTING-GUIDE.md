# Manual Testing Guide - IDM with Bearer Token Authentication

**Date:** July 22, 2026  
**Updated for:** IDM Implementation  
**Tools:** Postman + curl commands

---

## 📋 Table of Contents

1. [Quick Start (5 minutes)](#quick-start)
2. [Postman Setup](#postman-setup)
3. [Test Cases (Success & Failures)](#test-cases)
4. [curl Command Examples](#curl-commands)
5. [Verification](#verification)
6. [Troubleshooting](#troubleshooting)

---

## 🚀 Quick Start

### Prerequisites
```bash
# Terminal 1: Start MinIO + Spring Boot
docker-compose up -d
sleep 3
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local-minio"

# Wait for: "Started Application in X seconds"
```

### Test Upload (Terminal 2)
```bash
# Generate Bearer token
TOKEN=$(echo -n "user123:user@example.com" | base64)

# Upload file
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./pom.xml"

# Expected: 201 Created with userId in response ✅
```

---

## 📮 Postman Setup

### Step 1: Import Collection

1. **Download** the collection:
   - Location: `/postman/EcsLocalDemo.postman_collection.json`

2. **Open Postman**
   - Click **File** → **Import**
   - Select `EcsLocalDemo.postman_collection.json`
   - Collection appears in left sidebar

3. **Review Variables**
   - Click collection name → **Variables** tab
   - Verify Bearer token variables are set:
     - `user1_token` = `dXNlcjEyMzp1c2VyQGV4YW1wbGUuY29t` (user123:user@example.com)
     - `user2_token` = `anNvbjp0ZXN0QGV4YW1wbGUuY29t` (json:test@example.com)
     - `user3_token` = `ZGV2ZWxvcGVyOmRldkBleGFtcGxlLmNvbQ==` (developer:dev@example.com)
     - `baseUrl` = `http://localhost:8080`

### Step 2: Use in Requests

All requests use variables:
- Authorization header: `Bearer {{user1_token}}`
- Base URL: `{{baseUrl}}/api/files/upload`

Change variables anytime by editing the collection.

---

## ✅ Test Cases

### 1️⃣ HEALTH CHECK (No Authentication)

**Request:** `GET /actuator/health`

**Steps:**
1. Click request: "1️⃣ Health Check"
2. Click **Send**

**Expected:**
```
Status: 200 OK
Body:
{
  "status": "UP"
}
```

**Purpose:** Verify app is running

---

### 2️⃣ SUCCESS: Upload with User 1 Bearer Token

**Request:** `POST /api/files/upload` with Bearer token

**Steps:**
1. Click request: "2️⃣ Upload File (User 1 - With Bearer Token)"
2. Verify **Headers** tab shows: `Authorization: Bearer {{user1_token}}`
3. Click **Body** tab → `form-data`
4. Set **file** field type to **File**
5. Click **Select Files** → Choose any file (e.g., pom.xml)
6. Click **Send**

**Expected:**
```
Status: 201 Created
Body:
{
  "key": "users/user123/2024-07-22/abc123-pom.xml",
  "bucket": "demo-bucket",
  "contentType": "application/xml",
  "sizeBytes": 2847,
  "uploadedAt": "2024-07-22T07:30:45Z",
  "userId": "user123"
}
```

**Verification:**
- ✅ Status is 201
- ✅ userId is "user123"
- ✅ Key starts with "users/user123/"
- ✅ Response includes all 6 fields

---

### 3️⃣ SUCCESS: Upload with User 2 (Different User)

**Request:** `POST /api/files/upload` with different Bearer token

**Steps:**
1. Click request: "3️⃣ Upload File (User 2 - Different User)"
2. Verify **Headers** tab shows: `Authorization: Bearer {{user2_token}}`
3. Upload a file (e.g., README.md)
4. Click **Send**

**Expected:**
```
Status: 201 Created
{
  "key": "users/json/2024-07-22/def456-README.md",
  "bucket": "demo-bucket",
  "userId": "json"
}
```

**Purpose:** Verify user isolation
- ✅ userId is "json" (not "user123")
- ✅ File stored in users/json/ (not users/user123/)
- ✅ Different users have separate folders

---

### ❌ ERROR: Missing Authorization Header

**Request:** `POST /api/files/upload` WITHOUT Bearer token

**Steps:**
1. Click request: "❌ Error: Missing Authorization Header"
2. Verify **Headers** tab is EMPTY (no Authorization header)
3. Select a file in Body
4. Click **Send**

**Expected:**
```
Status: 400 Bad Request
Body: (empty or error message)
```

**Purpose:** Verify authentication is required

---

### ❌ ERROR: Invalid Bearer Token (Not Base64)

**Request:** `POST /api/files/upload` with invalid Base64 token

**Steps:**
1. Click request: "❌ Error: Invalid Bearer Token"
2. Verify **Headers** tab shows: `Authorization: Bearer invalid_token_not_base64`
3. Select a file
4. Click **Send**

**Expected:**
```
Status: 400 Bad Request
Error: "Invalid token encoding"
```

**Purpose:** Verify token validation

---

### ❌ ERROR: Malformed Token (Missing Colon)

**Request:** `POST /api/files/upload` with token missing colon

**Steps:**
1. Click request: "❌ Error: Malformed Token"
2. Token is Base64("justauserwithoutcolon") - no colon separator
3. Click **Send**

**Expected:**
```
Status: 400 Bad Request
Error: "Invalid token format"
```

**Purpose:** Verify userId:email format is enforced

---

### ❌ ERROR: Empty Bearer Token

**Request:** `POST /api/files/upload` with `Bearer ` (no token)

**Steps:**
1. Click request: "❌ Error: Empty Bearer Token"
2. Header: `Authorization: Bearer ` (ends with space, no token)
3. Click **Send**

**Expected:**
```
Status: 400 Bad Request
Error: "Bearer token is empty"
```

---

### ❌ ERROR: Wrong Prefix (Basic instead of Bearer)

**Request:** `POST /api/files/upload` with wrong prefix

**Steps:**
1. Click request: "❌ Error: Wrong Authorization Prefix"
2. Header: `Authorization: Basic {{user1_token}}` (uses "Basic" not "Bearer")
3. Click **Send**

**Expected:**
```
Status: 400 Bad Request
Error: "Authorization header must start with 'Bearer '"
```

---

### ❌ ERROR: Empty File Upload

**Request:** `POST /api/files/upload` with empty file

**Steps:**
1. Click request: "❌ Error: Empty File Upload"
2. Correct Bearer token in headers
3. Body: Select empty file OR don't select any file
4. Click **Send**

**Expected:**
```
Status: 400 Bad Request
Error: "file is empty"
```

---

## 💻 curl Commands

### Generate Bearer Token

```bash
# Create token for custom user
echo -n "john:john@example.com" | base64
# Output: am9objpqb2huQGV4YW1wbGUuY29t

# Use in commands
TOKEN=$(echo -n "user123:user@example.com" | base64)
echo $TOKEN
```

### 1. Health Check
```bash
curl -v http://localhost:8080/actuator/health

# Expected: 200 OK
```

### 2. Upload File (User 1)
```bash
TOKEN=$(echo -n "user123:user@example.com" | base64)

curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./pom.xml"

# Expected: 201 Created with userId: user123
```

### 3. Upload File (User 2)
```bash
TOKEN=$(echo -n "json:test@example.com" | base64)

curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./README.md"

# Expected: 201 Created with userId: json
```

### 4. Upload Multiple Users (Verify Isolation)
```bash
# User 1
TOKEN1=$(echo -n "alice:alice@example.com" | base64)
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN1" \
  -F "file=@./file1.txt"

# User 2
TOKEN2=$(echo -n "bob:bob@example.com" | base64)
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN2" \
  -F "file=@./file2.txt"

# User 3
TOKEN3=$(echo -n "charlie:charlie@example.com" | base64)
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN3" \
  -F "file=@./file3.txt"

# Result: 3 different folders in MinIO
# - users/alice/2024-07-22/
# - users/bob/2024-07-22/
# - users/charlie/2024-07-22/
```

### 5. Missing Authorization Header (Error)
```bash
curl -v -X POST http://localhost:8080/api/files/upload \
  -F "file=@./pom.xml"

# Expected: 400 Bad Request
```

### 6. Invalid Bearer Token (Error)
```bash
curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer invalid_not_base64" \
  -F "file=@./pom.xml"

# Expected: 400 Bad Request - "Invalid token encoding"
```

### 7. Wrong Prefix (Error)
```bash
TOKEN=$(echo -n "user123:user@example.com" | base64)

curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Basic $TOKEN" \
  -F "file=@./pom.xml"

# Expected: 400 Bad Request - "must start with 'Bearer '"
```

### 8. Empty File (Error)
```bash
TOKEN=$(echo -n "user123:user@example.com" | base64)

# Create empty file
touch empty.txt

curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./empty.txt"

# Expected: 400 Bad Request - "file is empty"
```

### 9. Large File Upload
```bash
TOKEN=$(echo -n "user123:user@example.com" | base64)

# Create 50MB test file
dd if=/dev/zero of=large_file.bin bs=1M count=50

curl -v -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./large_file.bin"

# Expected: 201 Created with sizeBytes: 52428800
```

---

## 🔍 Verification

### In MinIO Console

1. **Open:** http://localhost:9001
2. **Login:** minioadmin / minioadmin
3. **Browse:** demo-bucket
4. **Verify structure:**
   ```
   demo-bucket/
   ├── users/
   │   ├── user123/
   │   │   └── 2024-07-22/
   │   │       ├── abc123-pom.xml
   │   │       └── def456-README.md
   │   ├── json/
   │   │   └── 2024-07-22/
   │   │       └── ghi789-report.pdf
   │   └── developer/
   │       └── 2024-07-22/
   │           └── jkl012-data.csv
   ```

### In Spring Boot Logs

Watch for authentication logs:
```
[DEBUG] Bearer token authenticated: userId=user123
[INFO] Upload request: userId=user123 filename=pom.xml size=2847
[INFO] Upload success: userId=user123 key=users/user123/2024-07-22/abc-pom.xml
```

### Using AWS CLI (Optional)

```bash
# Configure AWS CLI for MinIO
aws s3 --endpoint-url http://localhost:9000 ls s3://demo-bucket/users/ --recursive

# Output:
# 2024-07-22 07:30:45       2847 users/user123/2024-07-22/abc-pom.xml
# 2024-07-22 07:31:12      15364 users/json/2024-07-22/def-report.pdf
```

---

## 🛠️ Troubleshooting

### Issue: "Authorization header is missing"
```
Cause: No Authorization header in request
Fix: Add header: Authorization: Bearer <token>
```

### Issue: "Invalid token encoding"
```
Cause: Token is not valid Base64
Fix: Use: echo -n "userId:email" | base64
```

### Issue: "Invalid token format"
```
Cause: Token doesn't have userId:email format (missing colon)
Fix: Ensure format: Base64("userId:email")
      Example: Base64("john:john@example.com")
```

### Issue: "Bearer token is empty"
```
Cause: Header has "Bearer " with no token after space
Fix: Add valid token: Bearer <base64_token>
```

### Issue: "Must start with 'Bearer '"
```
Cause: Using wrong prefix (e.g., "Basic" instead of "Bearer")
Fix: Change to: Authorization: Bearer <token>
```

### Issue: Files not appearing in MinIO
```
Cause: Storage error or invalid bucket
Fix: 
  1. Check Spring Boot logs for [ERROR]
  2. Verify MinIO is running: docker ps | grep minio
  3. Verify bucket exists: http://localhost:9001
```

### Issue: 500 Internal Server Error
```
Cause: Storage connectivity issue
Fix:
  1. Verify MinIO is running: docker-compose ps
  2. Check MinIO logs: docker-compose logs minio
  3. Restart: docker-compose restart
```

### Issue: Can't decode Base64 token
```
Solution: Use online tool: https://www.base64decode.org/
Or command line: echo "dXNlcjEyMzp1c2VyQGV4YW1wbGUuY29t" | base64 -d
```

---

## 📊 Test Coverage Matrix

| Scenario | Method | Token | File | Expected | Test? |
|----------|--------|-------|------|----------|-------|
| Health check | GET | N/A | N/A | 200 OK | ✅ |
| Valid upload (user1) | POST | Valid | Yes | 201 + userId | ✅ |
| Different user (user2) | POST | Valid | Yes | 201 + different userId | ✅ |
| Missing header | POST | None | Yes | 400 Error | ✅ |
| Invalid Base64 | POST | Invalid | Yes | 400 Error | ✅ |
| Wrong format (no colon) | POST | Malformed | Yes | 400 Error | ✅ |
| Empty token | POST | Empty | Yes | 400 Error | ✅ |
| Wrong prefix (Basic) | POST | Valid (Basic) | Yes | 400 Error | ✅ |
| Empty file | POST | Valid | Empty | 400 Error | ✅ |
| Large file | POST | Valid | Yes (50MB) | 201 + size | ✅ |

---

## 📝 Test Report Template

```markdown
# Test Report - ECS IDM Implementation

**Date:** [Date]
**Tester:** [Name]
**Environment:** Local MinIO + Spring Boot

## Summary
- Total Tests: 10
- Passed: 10 ✅
- Failed: 0
- Skipped: 0

## Detailed Results

### 1. Health Check
Status: ✅ PASS
Timestamp: [time]

### 2. Valid Upload (User 1)
Status: ✅ PASS
Response: 201 Created, userId=user123
File path: users/user123/2024-07-22/...

[Continue for each test]

## Observations
- [Note any issues or observations]

## Sign-off
- Tested by: [Name]
- Approved by: [Name]
- Date: [Date]
```

---

## ✅ Checklist for Manual Testing

- [ ] MinIO is running: docker-compose ps
- [ ] Spring Boot is running: Started Application
- [ ] Postman collection is imported
- [ ] Variables are set correctly (baseUrl, tokens)
- [ ] Health check passes (200 OK)
- [ ] User 1 upload succeeds (201 Created)
- [ ] User 2 upload succeeds (201 Created)
- [ ] Files appear in MinIO console
- [ ] Files organized by userId (users/user123/, users/json/)
- [ ] Missing header error test passes
- [ ] Invalid token error test passes
- [ ] Malformed token error test passes
- [ ] Empty token error test passes
- [ ] Wrong prefix error test passes
- [ ] Empty file error test passes

---

## 🧹 Cleanup

```bash
# Stop Spring Boot
# (Ctrl+C in Spring Boot terminal)

# Stop MinIO
docker-compose down

# Clean up test files
rm -f empty.txt large_file.bin

# Clean test data
mvn clean
```

---

## 📚 Related Documentation

- `IDM-IMPLEMENTATION-GUIDE.md` - Architecture overview
- `IDM-TESTING-GUIDE.md` - Automated test guide
- `IDM-QUICK-REFERENCE.md` - Bearer token format
- `IDM-CHANGELOG.md` - All code changes

---

**Last Updated:** July 22, 2026  
**Status:** Complete with 10+ test cases

