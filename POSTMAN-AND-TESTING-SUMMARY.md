# Postman Collection & Manual Testing Guide - Update Summary

**Date:** July 22, 2025  
**Status:** ✅ COMPLETE

---

## 📋 What Was Updated

### 1. ✅ Postman Collection (EcsLocalDemo.postman_collection.json)

**Major Updates:**
- ✅ Added Bearer token authentication to all API requests
- ✅ Created 5 pre-configured user tokens in Variables
- ✅ Added 11 test requests (5 success + 6 error cases)
- ✅ Added user-scoped file path examples
- ✅ Collection now supports full IDM testing workflow
- ✅ Valid JSON format (verified)

**Variables Added (5 tokens):**
```
- baseUrl: http://localhost:8080
- user1_token: dXNlcjEyMzp1c2VyQGV4YW1wbGUuY29t (user123:user@example.com)
- user2_token: anNvbjp0ZXN0QGV4YW1wbGUuY29t (json:test@example.com)
- user3_token: ZGV2ZWxvcGVyOmRldkBleGFtcGxlLmNvbQ== (developer:dev@example.com)
- custom_token: dXNlcm5hbWU6ZW1haWxAZXhhbXBsZS5jb20= (for custom testing)
```

**Test Requests (11 total):**

1. **1️⃣ Health Check** - Verify app is running (no auth)
2. **2️⃣ Upload File (User 1)** - ✅ Success case with Bearer token
3. **3️⃣ Upload File (User 2)** - ✅ Different user (user isolation)
4. **4️⃣ Upload File (User 3)** - ✅ Another user test
5. **❌ Missing Authorization Header** - 400 Bad Request
6. **❌ Invalid Bearer Token** - 400 Invalid encoding
7. **❌ Malformed Token** - 400 Invalid format (no colon)
8. **❌ Empty Bearer Token** - 400 Empty token
9. **❌ Wrong Prefix** - 400 Uses "Basic" instead of "Bearer"
10. **❌ Empty File Upload** - 400 No file content
11. **📋 Generate Bearer Token Guide** - Instructions for creating tokens

---

### 2. ✅ Manual Testing Guide (MANUAL-TESTING-GUIDE.md)

**Content (14KB document):**

#### Sections:
1. **Quick Start (5 minutes)** - Copy-paste ready commands
2. **Postman Setup** - Step-by-step import & configuration
3. **Test Cases** - 10+ detailed test scenarios with:
   - Expected responses
   - Verification steps
   - Purpose of each test

4. **curl Commands** - 9 examples for terminal testing:
   - Health check
   - Upload with different users
   - Multiple user uploads (isolation verification)
   - Error test cases
   - Large file uploads

5. **Verification** - How to verify results:
   - MinIO console checks
   - Spring Boot log patterns
   - AWS CLI verification

6. **Troubleshooting** - Common issues & fixes:
   - Missing authorization header
   - Invalid token encoding
   - Malformed token format
   - Empty token
   - Storage connectivity issues

7. **Test Coverage Matrix** - All 10 scenarios mapped
8. **Test Report Template** - For documenting results
9. **Cleanup Guide** - How to reset after testing

---

## 🎯 Test Scenarios Covered

### ✅ Success Cases (3)
- [ ] Upload with valid Bearer token (User 1)
- [ ] Upload with different user (User 2)
- [ ] Upload with third user (User 3)

### ❌ Error Cases (6)
- [ ] Missing Authorization header → 400
- [ ] Invalid Base64 token → 400
- [ ] Malformed token (no colon) → 400
- [ ] Empty Bearer token → 400
- [ ] Wrong prefix ("Basic" instead of "Bearer") → 400
- [ ] Empty file upload → 400

### 🔍 Verification Cases (2)
- [ ] Health check (app is running)
- [ ] User isolation (different users have separate folders)

---

## 📮 How to Use Postman Collection

### Import Collection
```bash
# File location
postman/EcsLocalDemo.postman_collection.json

# Steps:
1. Open Postman
2. File → Import
3. Select the JSON file
4. Collection appears in left sidebar
```

### Run Requests
```
1. Click request in left sidebar
2. Review Headers (should have Bearer token)
3. For upload requests: Select file in Body tab
4. Click Send
5. Check response status and body
```

### Test All at Once
```
1. Select collection
2. Click "Run" (play icon)
3. All tests execute sequentially
4. Review test results
```

---

## 💻 How to Use Manual Testing Guide

### Quick Start (5 minutes)
```bash
# 1. Start services
docker-compose up -d
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local-minio"

# 2. In another terminal, test
TOKEN=$(echo -n "user123:user@example.com" | base64)
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./pom.xml"

# 3. Verify response
# Expected: 201 Created with userId: user123
```

### Full Test Sequence
1. Read: MANUAL-TESTING-GUIDE.md sections in order
2. Follow: Step-by-step instructions for each test
3. Verify: Expected outcomes and user isolation
4. Document: Results in provided test report template

### Troubleshoot Issues
1. Reference: Troubleshooting section (by symptom)
2. Apply: Suggested fix
3. Verify: Test passes

---

## 📊 File Statistics

| File | Size | Lines | Purpose |
|------|------|-------|---------|
| EcsLocalDemo.postman_collection.json | 13KB | 500+ | 11 requests with full IDM support |
| MANUAL-TESTING-GUIDE.md | 14KB | 400+ | Complete manual testing guide |
| Total | 27KB | 900+ | Comprehensive testing documentation |

---

## 🚀 Quick Access

### Documentation
- **Start here:** `MANUAL-TESTING-GUIDE.md` → Quick Start section
- **Detailed tests:** `MANUAL-TESTING-GUIDE.md` → Test Cases section
- **Command line:** `MANUAL-TESTING-GUIDE.md` → curl Commands section
- **Troubleshooting:** `MANUAL-TESTING-GUIDE.md` → Troubleshooting section

### Postman
- **Import:** `postman/EcsLocalDemo.postman_collection.json`
- **Variables tab:** Edit bearer tokens as needed
- **Run all:** Click collection → Run button

---

## ✅ Pre-Test Checklist

- [ ] Docker is installed: `docker --version`
- [ ] Maven is installed: `mvn --version`
- [ ] Postman is installed
- [ ] Read MANUAL-TESTING-GUIDE.md Quick Start section
- [ ] MinIO running: `docker-compose up -d`
- [ ] Spring Boot running: `mvn spring-boot:run`
- [ ] Postman collection imported
- [ ] Bearer token variables set
- [ ] baseUrl set to http://localhost:8080

---

## 📋 Test Results Template

```markdown
# Test Execution Report

**Date:** [Date]
**Tester:** [Name]
**Environment:** Local MinIO + Spring Boot

## Summary
- Total Tests: 10
- ✅ Passed: __/10
- ❌ Failed: __/10

## Health Check
- [ ] Status 200 OK

## Success Cases
- [ ] User 1 upload → 201 Created
- [ ] User 2 upload → 201 Created
- [ ] User 3 upload → 201 Created

## Error Cases
- [ ] Missing header → 400
- [ ] Invalid token → 400
- [ ] Malformed token → 400
- [ ] Empty token → 400
- [ ] Wrong prefix → 400
- [ ] Empty file → 400

## User Isolation Verification
- [ ] User 1 files in users/user1/
- [ ] User 2 files in users/user2/
- [ ] User 3 files in users/user3/
- [ ] Files appear in MinIO console

## Issues Found
- [ ] [Issue 1]
- [ ] [Issue 2]

## Approved By
- Tester: _________________
- Reviewer: _________________
- Date: _________________
```

---

## 🔄 Integration with CI/CD

### Automated Testing
These files support:
- Manual testing in Postman (UI-based)
- Automated testing with Newman (CLI-based)
- Integration with GitHub Actions

### Newman Example (Optional)
```bash
# Install Newman
npm install -g newman

# Run Postman collection
newman run postman/EcsLocalDemo.postman_collection.json \
  --environment postman/environment.json

# Generate report
newman run postman/EcsLocalDemo.postman_collection.json \
  -r cli,json,junit
```

---

## 🎓 Learning Value

### What You Learn
1. How Bearer token authentication works
2. How to structure REST API requests
3. Error handling and validation
4. User isolation in multi-user systems
5. API testing best practices

### Skills Practiced
- Postman collection management
- curl command line usage
- JSON payload inspection
- HTTP status code understanding
- Debugging authentication issues

---

## 📚 Related Documentation

- **IDM-IMPLEMENTATION-GUIDE.md** - Architecture & design
- **IDM-TESTING-GUIDE.md** - Automated testing with TestContainers
- **IDM-QUICK-REFERENCE.md** - Bearer token format & generation
- **IDM-CHANGELOG.md** - All code changes & files modified

---

## ✨ Summary

You now have:

✅ **Postman Collection** with:
- 11 test requests (health, success, errors)
- 5 pre-configured bearer tokens
- Full IDM workflow support
- Valid JSON format

✅ **Manual Testing Guide** with:
- 5-minute quick start
- 10+ detailed test cases
- 9 curl command examples
- Comprehensive troubleshooting
- Test report template

✅ **Complete Testing Coverage**:
- Success scenarios (3 users)
- Error scenarios (6 error cases)
- Verification steps (user isolation)
- Troubleshooting guide

---

**Status:** ✅ COMPLETE & READY TO USE  
**Files:** 2 new files, 1 existing file updated  
**Test Coverage:** 10+ manual test scenarios

