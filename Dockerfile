# ── Stage 1: build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Cache dependencies separately so rebuilds are fast
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /app/target/distributed-cache-1.0-SNAPSHOT-exec.jar app.jar

# Default config baked in — overridable by mounting a file or setting env vars
COPY src/main/resources/application.yml ./application.yml

# Environment variables (all overridable at runtime)
ENV CACHE_SERVER_ID=""
ENV CACHE_SERVER_PORT=""
ENV CACHE_SERVER_ROLE=""
ENV CACHE_NODE_COUNT=""
ENV CACHE_REPLICAS=""
ENV CACHE_PRIMARY=""

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
# Default config file — override with: docker run ... --config=/app/my-config.yml
CMD ["--config=application.yml"]
