# EcsLocalDemo

**Multi-cloud S3 file upload service — built with Hexagonal Architecture**

Upload files to AWS S3 or a local MinIO instance via a single REST endpoint.  
Switch cloud providers by changing a Spring profile — zero code changes required.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Modules](#project-modules)
- [Prerequisites](#prerequisites)
- [Quick Start — Local MinIO](#quick-start--local-minio)
- [API Reference](#api-reference)
- [Configuration](#configuration)
- [Running Tests](#running-tests)
- [Docker](#docker)
- [Profiles — Local vs AWS](#profiles--local-vs-aws)
- [Architecture Rules (ArchUnit)](#architecture-rules-archunit)
- [Project Structure](#project-structure)

---

## Overview

EcsLocalDemo is a reference implementation of a file upload service that demonstrates:

- **Hexagonal Architecture** (Ports & Adapters) — the domain is completely isolated from Spring, AWS, and HTTP
- **Multi-cloud portability** — swap AWS S3 for MinIO, Azure Blob, or any S3-compatible provider by adding one adapter
- **ArchUnit enforcement** — architecture rules are verified on every build; violations break CI
- **Three-layer testing** — unit tests (no Spring), MockMvc controller tests, and end-to-end integration tests with real MinIO via TestContainers

---

## Architecture

```
HTTP Request
    │
    ▼
┌──────────────────────┐
│   FileController     │  Inbound Adapter  — translates HTTP ↔ domain
│   @RestController    │
└──────────┬───────────┘
           │ calls
           ▼
┌──────────────────────┐
│   FileServicePort    │  Inbound Port     — interface defined in core
└──────────┬───────────┘
           │ implements
           ▼
┌──────────────────────┐
│   FileServiceImpl    │  Use Case         — pure Java, zero Spring annotations
│   (no @Service!)     │  validates input, generates storage key, delegates
└──────────┬───────────┘
           │ calls
           ▼
┌──────────────────────┐
│   StoragePort        │  Outbound Port    — interface defined in core
└──────────┬───────────┘
           │ implements
           ▼
┌──────────────────────┐
│   S3StorageAdapter   │  Outbound Adapter — wraps AWS SDK v2, handles exceptions
│   @Component         │
└──────────┬───────────┘
           │
           ▼
     AWS S3 / MinIO
```

**The golden rule:** adapters never call other adapters. All communication flows through domain ports (interfaces).

---

## Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.1.7 |
| Build | Maven 3.9+ |
| Storage | AWS SDK v2 · MinIO (S3-compatible) |
| Testing | JUnit 5 · Mockito · AssertJ · TestContainers · ArchUnit |
| Containerization | Docker · Docker Compose |
| Architecture | Hexagonal (Ports & Adapters) |

---

## Project Modules

```
ecs-local-demo-parent
├── ecs-core               Pure domain — ZERO Spring. Ports, use cases, domain objects.
├── ecs-inbound-adapters   REST controller. Translates HTTP ↔ domain.
├── ecs-outbound-adapters  S3StorageAdapter. Translates domain ↔ AWS SDK.
├── ecs-application        Spring Boot entry point + all @Configuration + YAML files.
└── ecs-tests              All test suites: unit, integration, architecture.
```

**Module dependency rules:**

- `ecs-core` → depends on nothing (no Spring, no AWS)
- `ecs-inbound-adapters` → depends on `ecs-core` only
- `ecs-outbound-adapters` → depends on `ecs-core` only
- `ecs-application` → depends on all modules (wires everything together)
- `ecs-tests` → depends on all modules (needs full classpath)

The `ecs-core` POM includes a `maven-enforcer-plugin` rule that **fails the build** if any Spring dependency is added:

```xml
<bannedDependencies>
    <message>ERROR: Core module must NOT depend on Spring Framework!</message>
    <excludes>
        <exclude>org.springframework:*</exclude>
        <exclude>org.springframework.boot:*</exclude>
    </excludes>
</bannedDependencies>
```

---

## Prerequisites

| Tool | Minimum Version |
|------|----------------|
| Java | 17 |
| Maven | 3.9 |
| Docker | any recent version |
| Docker Compose | v2 |

```bash
java -version    # openjdk 17+
mvn -version     # Apache Maven 3.9+
docker version   # Docker Engine
```

---

## Quick Start — Local MinIO

### 1. Clone and build

```bash
git clone <repo-url>
cd EcsLocalDemo
mvn clean package -DskipTests
```

### 2. Start MinIO with Docker Compose

```bash
docker compose up -d
```

This starts three services:
- **`minio`** — S3-compatible object storage on port `9000`
- **`init`** — one-shot container that creates the `demo-bucket`
- **`app`** — the Spring Boot app on port `8080` (profile: `local-minio`)

Check everything is healthy:
```bash
docker compose ps
```

### 3. Upload a file

```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@./pom.xml" \
  -H "Accept: application/json"
```

**Response — `201 Created`:**
```json
{
  "key": "2026-07-21/3f4a1b2c-pom.xml",
  "bucket": "demo-bucket",
  "contentType": "application/xml",
  "sizeBytes": 2847,
  "uploadedAt": "2026-07-21T10:30:45Z"
}
```

### 4. Browse the file in MinIO Console

Open [http://localhost:9001](http://localhost:9001)  
Login: `minioadmin` / `minioadmin`  
Navigate to **demo-bucket** to see the uploaded file.

### 5. Stop

```bash
docker compose down          # stop containers, keep volume
docker compose down -v       # stop containers and delete MinIO data
```

---

## API Reference

### `POST /api/files/upload`

Upload a file to S3 / MinIO.

**Request**

| Field | Type | Description |
|-------|------|-------------|
| `file` | `multipart/form-data` | The file to upload |

**Responses**

| Status | When | Body |
|--------|------|------|
| `201 Created` | File uploaded successfully | `StorageObject` JSON |
| `400 Bad Request` | File is empty, or filename/content invalid | `{ "error": "...", "detail": "..." }` |
| `500 Internal Server Error` | Storage operation failed | `{ "error": "...", "detail": "..." }` |

**Example with curl:**
```bash
# Upload a file
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@/path/to/photo.jpg"

# Upload from stdin
echo "hello world" | curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@-;filename=hello.txt;type=text/plain"
```

**Successful response fields:**

| Field | Type | Example |
|-------|------|---------|
| `key` | `string` | `"2026-07-21/3f4a1b2c-photo.jpg"` |
| `bucket` | `string` | `"demo-bucket"` |
| `contentType` | `string` | `"image/jpeg"` |
| `sizeBytes` | `number` | `204800` |
| `uploadedAt` | `string` (ISO 8601) | `"2026-07-21T10:30:45Z"` |

**Storage key format:**  
`{yyyy-MM-dd}/{8-char-uuid}-{original-filename}`  
Date-partitioned for S3 performance and natural sorting.

---

### `GET /actuator/health`

Returns application health status including liveness.

```bash
curl http://localhost:8080/actuator/health
```

```json
{ "status": "UP" }
```

---

## Configuration

All configuration lives in `ecs-application/src/main/resources/`.

### `application.yml` — base config (all profiles)

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100MB        # Maximum upload size
      max-request-size: 110MB

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

### `application-local-minio.yml` — local development

```yaml
storage:
  s3:
    bucket: demo-bucket
    region: us-east-1
    endpoint: http://localhost:9000   # MinIO endpoint
    access-key: minioadmin
    secret-key: minioadmin
```

### `application-aws.yml` — production on AWS

```yaml
storage:
  s3:
    bucket: ${AWS_S3_BUCKET:my-demo-bucket}
    region: ${AWS_REGION:eu-west-1}
    endpoint:                          # intentionally blank → SDK auto-discovers
    access-key: ${AWS_ACCESS_KEY_ID:}
    secret-key: ${AWS_SECRET_ACCESS_KEY:}
```

### Environment variables (Docker / ECS)

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active profile (`local-minio` or `aws`) | — |
| `STORAGE_S3_ENDPOINT` | Override S3 endpoint URL (MinIO inside Docker) | — |
| `AWS_S3_BUCKET` | Target bucket name | `my-demo-bucket` |
| `AWS_REGION` | AWS region | `eu-west-1` |
| `AWS_ACCESS_KEY_ID` | AWS access key (prefer IAM role for ECS) | — |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key (prefer IAM role for ECS) | — |

---

## Running Tests

### Unit and architecture tests (no Docker required)

```bash
mvn test -pl ecs-core,ecs-inbound-adapters,ecs-outbound-adapters
```

```bash
# Run only architecture enforcement tests
mvn test -pl ecs-tests -Dtest=HexagonalArchitectureTest

# Run only core domain unit tests
mvn test -pl ecs-tests -Dtest=FileServiceImplTest

# Run only controller unit tests
mvn test -pl ecs-tests -Dtest=FileControllerTest
```

### Full test suite including integration tests (Docker required)

```bash
mvn verify
```

TestContainers automatically pulls the MinIO image, starts a container, runs the tests, and tears it down. Docker must be running.

### What each test class covers

| Class | Type | What it tests |
|-------|------|---------------|
| `FileServiceImplTest` | Unit | Core use case: key generation, validation, StoragePort delegation |
| `FileControllerTest` | Unit (MockMvc standalone) | HTTP mapping: 201/400/500, JSON serialization |
| `HexagonalArchitectureTest` | Architecture (ArchUnit) | 4 hexagonal architecture rules |
| `FileUploadIT` | Integration (TestContainers) | Full HTTP → Spring → S3Adapter → real MinIO flow |

---

## Docker

### Build the image

```bash
mvn clean package -DskipTests
docker build -t ecs-local-demo:latest .
```

### Run with Docker Compose (recommended for local dev)

```bash
docker compose up -d           # start in background
docker compose logs -f app     # tail application logs
docker compose down            # stop
```

### Run the image manually

```bash
# Against a running MinIO on localhost
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local-minio \
  -e STORAGE_S3_ENDPOINT=http://host.docker.internal:9000 \
  ecs-local-demo:latest

# Against AWS S3
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=aws \
  -e AWS_S3_BUCKET=my-bucket \
  -e AWS_REGION=eu-west-1 \
  -e AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE \
  -e AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY \
  ecs-local-demo:latest
```

### Dockerfile highlights

The Dockerfile uses a **two-stage build**:

1. **Stage 1 (builder):** Maven + JDK image. POMs are copied first to cache the dependency download layer. Source compiled with `-DskipTests`.
2. **Stage 2 (runtime):** Minimal JRE Alpine image. Non-root user. JVM tuned for containers (`-XX:+UseContainerSupport`, `-XX:MaxRAMPercentage=75.0`).

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS builder
# ... build ...

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=builder /build/ecs-application/target/*.jar app.jar
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

---

## Profiles — Local vs AWS

| Behaviour | `local-minio` | `aws` |
|-----------|--------------|-------|
| S3 endpoint | `http://localhost:9000` | SDK auto-discovery |
| Path-style access | Enabled (MinIO requires it) | Disabled |
| Credentials | Static (`minioadmin`) | IAM role or env vars |
| Bucket | `demo-bucket` | `${AWS_S3_BUCKET}` |

The only difference in code is in `StorageConfiguration`:

```java
if (cfg.getEndpoint() != null && !cfg.getEndpoint().isBlank()) {
    // MinIO: custom endpoint + path-style
    builder.endpointOverride(URI.create(cfg.getEndpoint()))
           .serviceConfiguration(S3Configuration.builder()
                   .pathStyleAccessEnabled(true).build());
} else {
    // AWS: default SDK behaviour
}
```

`S3StorageAdapter` is identical for both — no `if/else` for cloud provider anywhere in the adapter.

---

## Architecture Rules (ArchUnit)

Four rules are verified on every `mvn test` run by `HexagonalArchitectureTest`. A violation **fails the build**.

| Rule | What it checks |
|------|---------------|
| **Rule 1** | Classes in `com.example.core..` must not import `org.springframework..` or `jakarta.inject..` |
| **Rule 2** | Classes in `com.example.adapters.inbound..` must not import classes from `com.example.adapters.outbound..` |
| **Rule 3** | Classes in `com.example.adapters.outbound..` named `*Adapter` must implement `StoragePort` |
| **Rule 4** | Only `com.example.application..` may use `@Configuration` — forbidden in adapters and core |

**Double enforcement on `ecs-core`:** the maven-enforcer-plugin also bans Spring as a compile-time dependency, so a Spring import would fail to compile before ArchUnit even runs.

---

## Project Structure

```
EcsLocalDemo/
│
├── pom.xml                              Parent POM — dependency management
│
├── ecs-core/                            Pure domain — ZERO Spring
│   ├── pom.xml                          maven-enforcer bans Spring deps
│   └── src/main/java/com/example/core/
│       ├── domain/
│       │   ├── StorageObject.java       Immutable value object (@Value)
│       │   └── StorageException.java    Checked domain exception
│       ├── ports/
│       │   ├── FileServicePort.java     Inbound port (what REST calls)
│       │   └── StoragePort.java         Outbound port (what adapters implement)
│       └── usecases/
│           └── FileServiceImpl.java     Core business logic — no Spring
│
├── ecs-inbound-adapters/                REST adapter
│   └── src/main/java/.../rest/
│       └── FileController.java          @RestController — HTTP ↔ domain
│
├── ecs-outbound-adapters/               Storage adapter
│   └── src/main/java/.../storage/
│       └── S3StorageAdapter.java        @Component — domain ↔ AWS SDK v2
│
├── ecs-application/                     Spring Boot application
│   ├── pom.xml
│   └── src/main/
│       ├── java/.../application/
│       │   ├── Application.java         @SpringBootApplication entry point
│       │   └── config/
│       │       ├── StorageConfiguration.java   @Configuration — wires beans
│       │       └── S3Configuration.java         @ConfigurationProperties
│       └── resources/
│           ├── application.yml
│           ├── application-local-minio.yml
│           └── application-aws.yml
│
├── ecs-tests/                           All test suites
│   ├── pom.xml
│   └── src/test/java/.../tests/
│       ├── arch/
│       │   └── HexagonalArchitectureTest.java   ArchUnit — 4 rules
│       ├── unit/
│       │   ├── FileServiceImplTest.java          Domain unit tests
│       │   └── FileControllerTest.java           MockMvc standalone
│       └── integration/
│           └── FileUploadIT.java                 TestContainers + MinIO
│
├── docker-compose.yml                   MinIO + app for local dev
├── Dockerfile                           Two-stage build, non-root user
├── code-walkthrough.md                  Detailed code walkthrough
└── README.md                            This file
```

---

## Common Commands

```bash
# Build (skip tests)
mvn clean package -DskipTests

# Build + run all tests
mvn clean verify

# Run locally with MinIO
mvn spring-boot:run \
  -pl ecs-application \
  -Dspring-boot.run.arguments="--spring.profiles.active=local-minio"

# Run with Docker Compose (full stack)
docker compose up -d

# Check app health
curl http://localhost:8080/actuator/health

# Upload a file
curl -X POST http://localhost:8080/api/files/upload -F "file=@README.md"
```

```bash
curl --request POST \
  --url http://localhost:8080/api/files/upload \
  --header 'content-type: multipart/form-data' \
  --form=@/Users/copor/Desktop/test-files/test-gizmo.txt
```
# Run architecture tests only
```shell
mvn test -pl ecs-tests -Dtest=HexagonalArchitectureTest
```
# View MinIO console
```shell
open http://localhost:9001
```
---
