# Multi-stage Backend Build
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copy gradle files first for heavy dependency caching
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew
# Pre-download dependencies
RUN ./gradlew dependencies --no-daemon || true

# Copy source and build
# -PskipFrontend: Skips the npm build inside the JVM-only Docker image (frontend built separately in compose)
# --no-daemon: Ensures a clean, one-off build suitable for ephemeral Docker containers
COPY . .
RUN ./gradlew bootJar -x test -PskipFrontend --no-daemon


# Runtime stage (needs JDK for javac compilation)
FROM eclipse-temurin:25-jdk

WORKDIR /app

# Copy the build artifact from builder stage
# Using wildcard to be version-agnostic, renaming to app.jar
COPY --from=builder /app/build/libs/*web.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
