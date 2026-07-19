# 🧪 Test-Me Guide — ECS Local Demo

Step-by-step manual testing guide for the `local-minio` profile using Docker Compose.

---

## Prerequisites

| Tool | Version | Check with |
|------|---------|------------|
| Docker | 24+ | `docker --version` |
| Docker Compose | 2.20+ | `docker compose version` |
| Postman | Any | or use `curl` |
| Java | 21 | `java --version` (for running tests) |
| Maven | 3.9+ | `mvn --version` |

---

## Step 1 — Build and Start

```bash
# From project root
cd /Users/copor/IdeaProjects/EcsLocalDemo

# Build the JAR
mvn clean package -DskipTests

# Start all services (MinIO + app)
docker compose up -d
```

**Wait ~30 seconds** for services to become healthy.

```bash
# Check status
docker compose ps

# Expected output:
# NAME              STATUS          PORTS
# minio             healthy         0.0.0.0:9000->9000/tcp, 0.0.0.0:9001->9001/tcp
# minio-init        exited (0)
# ecs-local-demo    healthy         0.0.0.0:8080->8080/tcp
```

---

## Step 2 — Verify Health

```bash
curl http://localhost:8080/actuator/health
```

Expected:
```json
{
  "status": "UP"
}
```

---

## Step 3 — Upload a File via `curl`

```bash
# Upload a text file
curl -X POST http://localhost:8080/api/files/upload \
     -F "file=@README.md" \
     -v

# Upload any file
curl -X POST http://localhost:8080/api/files/upload \
     -F "file=@/path/to/your/photo.jpg"
```

Expected response (`201 Created`):
```json
{
  "key": "2024-01-15/a3f1b2c4-README.md",
  "bucket": "demo-bucket",
  "contentType": "text/plain",
  "sizeBytes": 4096,
  "uploadedAt": "2024-01-15T10:30:00.000Z"
}
```

---

## Step 4 — Upload via Postman

1. Open Postman
2. **Import collection**: `File → Import → postman/EcsLocalDemo.postman_collection.json`
3. Select **"Upload File"** request
4. Click the **Body** tab → select **form-data**
5. Set the `file` field type to **File**
6. Click **Select Files** → pick any file
7. Click **Send**
8. Verify `201 Created` response with `StorageObject` JSON body

---

## Step 5 — Verify in MinIO Console

1. Open browser: **http://localhost:9001**
2. Login: `minioadmin` / `minioadmin`
3. Click **Buckets** → `demo-bucket`
4. Click **Browse** — you should see your uploaded file under `2024-MM-DD/` prefix

---

## Step 6 — Test Error Cases

### Empty file (expect `400 Bad Request`)
```bash
curl -X POST http://localhost:8080/api/files/upload \
     -F "file=@/dev/null" \
     -v
```

### No file field (expect `400 Bad Request`)
```bash
curl -X POST http://localhost:8080/api/files/upload \
     -v
```

---

## Step 7 — Run All Automated Tests

```bash
# Unit tests only (fast, no Docker needed)
mvn test -pl ecs-tests -Dtest="*Test" -DfailIfNoTests=false

# Integration tests (starts TestContainers MinIO automatically)
mvn test -pl ecs-tests -Dtest="*IT" -DfailIfNoTests=false

# Architecture tests
mvn test -pl ecs-tests -Dtest="HexagonalArchitectureTest" -DfailIfNoTests=false

# All tests
mvn test -pl ecs-tests
```

Expected output:
```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 (unit)
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 (integration)
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 (arch)
```

---

## Step 8 — View App Logs

```bash
# Real-time logs
docker compose logs -f app

# MinIO logs
docker compose logs -f minio
```

---

## Step 9 — Switch to AWS Profile (optional)

If you have AWS credentials:

```bash
# Set environment variables
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_S3_BUCKET=your-bucket-name
export AWS_REGION=eu-west-1

# Run directly (outside Docker)
java -jar ecs-application/target/*.jar \
     --spring.profiles.active=aws
```

---

## Step 10 — Cleanup

```bash
# Stop containers
docker compose down

# Stop and remove volumes (deletes MinIO data)
docker compose down -v
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `Connection refused :8080` | Run `docker compose ps` — is `app` service healthy? Wait 30s more. |
| `500 Storage failure` | Run `docker compose logs app` — endpoint misconfigured? |
| MinIO console not accessible | Run `docker compose ps minio` — check port 9001 binding |
| `demo-bucket` not found | Run `docker compose restart init` |
| Tests fail with `Container failed to start` | Ensure Docker daemon is running |

