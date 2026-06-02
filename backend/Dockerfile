# ---- build stage: compile + package the Spring Boot fat jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# copy the POM first so the dependency layer caches across source-only changes
COPY pom.xml .
COPY src ./src
# tests need Docker (Testcontainers); they run via `mvn test` on the host / CI, not in the image build
RUN mvn -q -B -DskipTests package

# ---- run stage: slim JRE ----
FROM eclipse-temurin:21-jre
# curl is used by the compose healthcheck (GET /actuator/health); slim install, no apt cache.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# Run as a non-root user (defence in depth). The jar is world-readable and the app only
# writes to /tmp, so an unprivileged UID is a one-line change with no volume implications.
RUN useradd -u 10001 appuser
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
