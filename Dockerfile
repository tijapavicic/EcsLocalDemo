# ─── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy POMs first for layer caching — dependencies are downloaded only when POMs change
COPY pom.xml .
COPY ecs-core/pom.xml               ecs-core/
COPY ecs-inbound-adapters/pom.xml   ecs-inbound-adapters/
COPY ecs-outbound-adapters/pom.xml  ecs-outbound-adapters/
COPY ecs-application/pom.xml        ecs-application/
COPY ecs-tests/pom.xml              ecs-tests/

RUN mvn dependency:go-offline -B -q

# Copy source code
COPY ecs-core/src               ecs-core/src
COPY ecs-inbound-adapters/src   ecs-inbound-adapters/src
COPY ecs-outbound-adapters/src  ecs-outbound-adapters/src
COPY ecs-application/src        ecs-application/src

# Build, skip tests (tests run in CI, not in image build)
RUN mvn clean package -DskipTests -B -q

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="ecs-local-demo"
LABEL org.opencontainers.image.description="Multi-cloud S3 file upload — Hexagonal Architecture"

WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/ecs-application/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]

