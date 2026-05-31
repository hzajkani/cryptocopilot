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
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
