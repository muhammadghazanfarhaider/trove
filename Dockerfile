
# ==================================
# Stage 1: Build Stage
# ==================================
FROM maven:3.9.9-eclipse-temurin-17 AS builder

# Set working directory
WORKDIR /build

# Copy Maven settings first (rarely changes)
COPY settings.xml ./

# Copy only POM files first (for dependency caching)
# This layer will be cached unless pom.xml changes
COPY pom.xml ./

# Download dependencies (cached layer unless pom.xml changes)
RUN mvn dependency:go-offline -B -s settings.xml

# Now copy source code
COPY src ./src

# Build the application (skip tests in Docker build)
# Tests should run in CI/CD pipeline separately
RUN mvn clean package -B -s settings.xml -DskipTests

# Verify the JAR was created
RUN ls -lh /build/target/*.jar

# ==================================
# Stage 2: Runtime Stage
# ==================================
FROM eclipse-temurin:17.0.10_7-jre-alpine

# Install curl for health checks (alpine doesn't include it by default)
RUN apk add --no-cache curl

# Create a non-root user to run the application
# This improves security by limiting permissions
# RUN addgroup -g 1000 appuser && \
#     adduser -D -u 1000 -G appuser appuser

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /build/target/*.jar ./app.jar

# Change ownership to non-root user
# RUN chown -R appuser:appuser /app

# Switch to non-root user
# USER appuser

# Expose application port (Spring Boot default)
EXPOSE 8080

# Expose gRPC port
EXPOSE 6565

# Health check - checks if actuator health endpoint responds
# Starts checking after 60s, checks every 30s, timeout 3s, max 3 retries
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/audit/manage/health || exit 1

# Environment variables for JVM configuration
# These can be overridden at runtime
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -Djava.security.egd=file:/dev/./urandom"

# ENTRYPOINT with exec ensures proper signal handling
ENTRYPOINT exec java ${JAVA_OPTS} -jar app.jar