# README.md & curl Syntax Fix — Summary

**Date:** July 22, 2026  
**Status:** ✅ COMPLETE

---

## 🔧 Problem Fixed

### Broken curl Command
```bash
curl --request POST \
  --url http://localhost:8080/api/files/upload \
  --header 'content-type: multipart/form-data' \
  --form=@/Users/copor/Desktop/test-files/test-gizmo.txt
```

**Error:**
```
Warning: Illegally formatted input field
curl: option --form=@/Users/copor/Desktop/test-files/test-gizmo.txt: is badly used here
curl: try 'curl --help' or 'curl --manual' for more information
```

### Root Cause
The `--form` flag requires a **key=value** pair, not just `@/path`.

### Solution
Provide proper form field syntax: `--form "fieldname=@/path"`

---

## ✅ Correct curl Syntax

### Option 1: Long Form
```bash
TOKEN=$(echo -n "alice:alice@example.com" | base64)

curl --request POST \
  --url http://localhost:8080/api/files/upload \
  --form "file=@/Users/copor/Desktop/test-files/test-gizmo.txt" \
  --header "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json"
```

### Option 2: Short Form
```bash
TOKEN=$(echo -n "alice:alice@example.com" | base64)

curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/Users/copor/Desktop/test-files/test-gizmo.txt"
```

### Key Differences

| Aspect | Broken | Fixed |
|--------|--------|-------|
| Form syntax | `--form=@/path` | `--form "file=@/path"` |
| Field name | Missing | `file=` |
| Quotes | None | Required |
| Bearer token | None (now required) | `Authorization: Bearer` |

---

## 📝 README.md Updates

### 1. Title & Overview
**Before:**
```markdown
# EcsLocalDemo

**Multi-cloud S3 file upload service — built with Hexagonal Architecture**
```

**After:**
```markdown
# EcsLocalDemo

**Multi-cloud S3 file upload service — built with Hexagonal Architecture**

**✨ NEW:** Bearer token authentication with user-scoped file storage (IDM)
```

---

### 2. Tech Stack Updated

| Component | Before | After |
|-----------|--------|-------|
| Java | 17 | **21** |
| Spring Security | ❌ None | ✅ 6.1 |
| Authentication | ❌ None | ✅ Bearer Tokens (IDM) |

---

### 3. Table of Contents (Added)

```markdown
- [IDM & Bearer Token Authentication](#idm--bearer-token-authentication) ✨ NEW
```

Inserted between "API Reference" and "Configuration"

---

### 4. New Section: IDM & Bearer Token Authentication

**Content includes:**

#### How it works (4 steps)
```
1. Generate a Bearer token — Base64(userId:email)
2. Include in Authorization header
3. Files are user-scoped → separate S3 prefixes
4. userId in response → audit trail
```

#### Architecture components table
```
| Component | File | Role |
| TokenExtractorPort | ecs-core/ports | Inbound port for token validation |
| BearerTokenExtractor | ecs-inbound-adapters/security | Token validation implementation |
| BearerTokenAuthenticationFilter | ecs-inbound-adapters/security | Spring Security filter |
| ... etc ...
```

#### Testing examples
```bash
TOKEN_ALICE=$(echo -n "alice:alice@example.com" | base64)
TOKEN_BOB=$(echo -n "bob:bob@example.com" | base64)

# Upload as Alice
curl -H "Authorization: Bearer $TOKEN_ALICE" \
  http://localhost:8080/api/files/upload -F "file=@file1.pdf"
# → Stored in: users/alice/2026-07-22/...

# Upload as Bob
curl -H "Authorization: Bearer $TOKEN_BOB" \
  http://localhost:8080/api/files/upload -F "file=@file2.pdf"
# → Stored in: users/bob/2026-07-22/...
```

#### Error cases documented
- Missing Authorization header
- Invalid Bearer token (not Base64)
- Malformed token (missing colon)

#### Links to detailed docs
- IDM-IMPLEMENTATION-GUIDE.md
- IDM-QUICK-REFERENCE.md
- MANUAL-TESTING-GUIDE.md
- Postman collection

---

### 5. API Reference Updated

#### Before
```markdown
**Example with curl:**
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@/path/to/photo.jpg"
```

#### After
```markdown
**Authentication**
| Field | Value | Description |
| Authorization header | Bearer <token> | Required. Token format: Base64(userId:email) |

**Example:**
TOKEN=$(echo -n "user1:user@example.com" | base64)
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./document.pdf"
```

#### Response fields updated
```diff
  "key": "2026-07-21/3f4a1b2c-pom.xml"
- → "2026-07-22/3f4a1b2c-pom.xml"
+ → "users/alice/2026-07-22/3f4a1b2c-pom.xml"

+ "userId": "alice"  ← NEW: audit trail
```

---

### 6. Quick Start — Updated Step 3

**Before:**
```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@./pom.xml" \
  -H "Accept: application/json"
```

**After:**
```bash
# Create Bearer token first
TOKEN=$(echo -n "alice:alice@example.com" | base64)

# Upload with authentication
curl --request POST \
  --url http://localhost:8080/api/files/upload \
  --form "file=@./pom.xml" \
  --header "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json"
```

Response now includes:
```json
{
  "key": "users/alice/2026-07-22/3f4a1b2c-pom.xml",
  "userId": "alice"
}
```

---

### 7. Common Commands Section

**Added:**
```shell
# Generate Bearer token
TOKEN=$(echo -n "user1:user@example.com" | base64)

# Upload file with Bearer token
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/file.txt"
```

---

## 📊 Documentation Statistics

| Metric | Value |
|--------|-------|
| README.md lines | 707 (before) → ~800 (after) |
| New sections | 1 (IDM & Bearer Token Auth) |
| Updated sections | 7 |
| Code examples | 15+ |
| Architecture components documented | 6 |
| Error cases covered | 3 |

---

## 🎯 What Now Works

### ✅ File Uploads with Authentication
```bash
TOKEN=$(echo -n "alice:alice@example.com" | base64)
curl -X POST http://localhost:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./document.pdf"
# → 201 Created with userId: "alice"
```

### ✅ User Isolation
```
Alice's files: s3://bucket/users/alice/2026-07-22/...
Bob's files:   s3://bucket/users/bob/2026-07-22/...
```

### ✅ Audit Trail
```json
{
  "key": "users/alice/2026-07-22/abc-document.pdf",
  "userId": "alice",
  "uploadedAt": "2026-07-22T10:30:45Z"
}
```

---

## 📚 Related Documentation

The README now links to:
1. **IDM-IMPLEMENTATION-GUIDE.md** — Complete IDM architecture
2. **IDM-QUICK-REFERENCE.md** — Bearer token formats & commands
3. **MANUAL-TESTING-GUIDE.md** — 10+ manual test cases
4. **Postman collection** — `postman/EcsLocalDemo.postman_collection.json`
5. **code-walkthrough.md** — Step-by-step code explanation with IDM

---

## ✨ Key Improvements

| Before | After |
|--------|-------|
| No authentication | ✅ Bearer token IDM |
| Global file storage | ✅ User-scoped paths |
| No audit trail | ✅ userId in all responses |
| Curl examples broken | ✅ Correct syntax documented |
| No Java 21 support | ✅ Java 21 documented |
| No Spring Security | ✅ Spring Security 6.1 |
| Basic documentation | ✅ Comprehensive IDM docs |

---

## 🔗 File Updated

**Path:** `/Users/copor/IdeaProjects/EcsLocalDemo/README.md`

**Changes:**
- ✅ Fixed Java version (Java 17 → Java 21)
- ✅ Added Spring Security 6.1 to tech stack
- ✅ Added Bearer token authentication section (6 subsections)
- ✅ Fixed curl syntax with proper --form field syntax
- ✅ Updated API reference with Authentication section
- ✅ Updated response format to include userId
- ✅ Added user isolation examples
- ✅ Added error case documentation
- ✅ Linked to detailed IDM documentation

---

## 📋 Before & After Comparison

### Curl Command

**❌ BROKEN:**
```bash
curl --request POST \
  --url http://localhost:8080/api/files/upload \
  --form=@/Users/copor/Desktop/test-files/test-gizmo.txt
# Error: Illegally formatted input field
```

**✅ FIXED:**
```bash
TOKEN=$(echo -n "alice:alice@example.com" | base64)
curl --request POST \
  --url http://localhost:8080/api/files/upload \
  --form "file=@/Users/copor/Desktop/test-files/test-gizmo.txt" \
  --header "Authorization: Bearer $TOKEN"
# Response: 201 Created
```

### File Path

**❌ BEFORE:**
```
s3://bucket/2026-07-22/abc-document.pdf
```

**✅ AFTER (User-Scoped):**
```
s3://bucket/users/alice/2026-07-22/abc-document.pdf
```

### Response JSON

**❌ BEFORE:**
```json
{
  "key": "2026-07-22/abc-document.pdf",
  "bucket": "demo-bucket",
  "uploadedAt": "2026-07-22T10:30:45Z"
}
```

**✅ AFTER:**
```json
{
  "key": "users/alice/2026-07-22/abc-document.pdf",
  "bucket": "demo-bucket",
  "uploadedAt": "2026-07-22T10:30:45Z",
  "userId": "alice"
}
```

---

**Status:** ✅ COMPLETE  
**Date Updated:** July 22, 2026  
**Documentation:** Comprehensive

