# Observability Quick Start - ECS Local Demo

**Implementation Status:** ✅ COMPLETE  
**Build Status:** ✅ SUCCESS  

---

## 🎯 What You Can Do Right Now

### 1. View Application Metrics (Prometheus Format)

**Start the application:**
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local-minio"
```

**Access metrics:**
```bash
# Human-readable list
curl http://localhost:8080/actuator/metrics

# Prometheus format (for scraping)
curl http://localhost:8080/actuator/prometheus | head -50
```

**Example output:**
```
# HELP http_server_requests_seconds HTTP requests
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{method="POST",status="201",uri="/api/files/upload"} 1.0
http_server_requests_seconds_sum{method="POST",status="201",uri="/api/files/upload"} 0.234
```

### 2. Test File Upload & Generate Metrics

```bash
# Upload a file
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@pom.xml" \
  -H "Accept: application/json"

# Check metrics for upload
curl http://localhost:8080/actuator/prometheus | grep "http_server_requests.*upload"
```

### 3. Health Check

```bash
curl http://localhost:8080/actuator/health | jq .
```

Output shows:
- Overall status (UP/DOWN)
- Disk space available
- Database connectivity (if applicable)

---

## 📊 Available Metrics Out of the Box

### HTTP Metrics
- `http_server_requests_seconds` — Request duration
- `http_server_requests_seconds_count` — Request count
- Breakdown by: `method`, `status`, `uri`

### JVM Metrics
- `jvm_memory_used_bytes` — Memory usage (heap/non-heap)
- `jvm_threads_live` — Active thread count
- `process_cpu_usage` — CPU usage %
- `process_uptime_seconds` — Application uptime

### Prometheus Registry
- `promhttp_*` — Prometheus client library metrics

---

## 🚀 Local Monitoring Setup (5 minutes)

### Jaeger (Distributed Tracing)

```bash
docker run -d \
  -p 6831:6831/udp \
  -p 16686:16686 \
  jaegertracing/all-in-one:latest
```

Access Jaeger UI: http://localhost:16686

### Prometheus + Grafana

```bash
# Prometheus
docker run -d -p 9090:9090 \
  -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus

# Grafana
docker run -d -p 3000:3000 \
  -e GF_SECURITY_ADMIN_PASSWORD=admin \
  grafana/grafana
```

Access:
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

### prometheus.yml

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'ecs-local-demo'
    static_configs:
      - targets: ['host.docker.internal:8080']
    metrics_path: '/actuator/prometheus'
```

---

## 📈 Next: Enable Distributed Tracing

Install OpenTelemetry Java Agent:

```bash
# Download agent (one-time)
curl -L -o opentelemetry-javaagent.jar \
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

# Run app with tracing
java -javaagent:opentelemetry-javaagent.jar \
  -Dotel.traces.exporter=jaeger \
  -Dotel.exporter.jaeger.endpoint=http://localhost:14250 \
  -jar ecs-application/target/ecs-application-1.0.0.jar \
  --spring.profiles.active=local-minio
```

Then:
1. Upload a file: `curl -X POST -F "file=@pom.xml" http://localhost:8080/api/files/upload`
2. View trace in Jaeger: http://localhost:16686 → Select `ecs-local-demo` service

---

## 🔍 Key Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Overall health status |
| `/actuator/metrics` | Available metrics list |
| `/actuator/prometheus` | Prometheus-format metrics (for scraping) |
| `/actuator/info` | Application information |

---

## 🎓 Architecture

```
HTTP Request
    ↓
[Spring Actuator] (managed by Spring Boot)
    ├→ Micrometer
    │   └→ Prometheus Registry
    │       └→ /actuator/prometheus endpoint
    └→ OpenTelemetry API (available for instrumentation)
        └→ Can be wired to Jaeger/OTLP via Java Agent
```

**Key Point:** Core domain (`ecs-core`) remains Spring-free. Observability hooks are in adapters.

---

## 📋 Implementation Files

| File | Change |
|------|--------|
| `ecs-application/pom.xml` | Added Micrometer + OpenTelemetry dependencies |
| `pom.xml` (parent) | Added dependency management for OTel |
| `application.yml` | Enabled Prometheus metrics endpoint |
| `application-local-minio.yml` | OTel environment variable configuration |
| `application-aws.yml` | Production-ready sampling + tracing |

---

## ✅ Verification

```bash
# Should all succeed:
mvn clean package -DskipTests
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus | head
```

---

**For detailed setup & instrumentation:** See `OPENTELEMETRY-IMPLEMENTATION.md`

