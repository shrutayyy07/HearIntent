# --- Build stage ---
# NOTE: build context for this image must be the repository ROOT (see docker-compose.yml),
# because the Maven build needs access to ../proto relative to backend/pom.xml.
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY backend/pom.xml ./backend/pom.xml
COPY proto ./proto

WORKDIR /build/backend
RUN mvn -q dependency:go-offline -B || true

COPY backend/src ./src

RUN mvn -q clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-jammy

RUN useradd -ms /bin/bash hearintent
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/backend/target/backend-1.0.0.jar app.jar

RUN mkdir -p /var/log/hearintent/backend && chown -R hearintent:hearintent /var/log/hearintent /app

USER hearintent

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=3 \
    CMD curl -sf http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xms256m", "-Xmx768m", "-jar", "app.jar"]
