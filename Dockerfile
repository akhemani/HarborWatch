# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

# Copy source code and pom.xml
COPY . .

# Build the application (skip tests for faster build)
RUN mvn -q -DskipTests package

# Stage 2: Run the application
FROM eclipse-temurin:21-jre

# Create a non-root user for better security
RUN useradd -ms /bin/bash appuser

WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /workspace/target/harborwatch-0.0.1-SNAPSHOT.jar app.jar

# Expose internal port
EXPOSE 8080

# Drop privileges
USER appuser

# Reasonable JVM memory handling inside containers
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# Start the app
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
