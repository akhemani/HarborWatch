# - Runs the fat jar built by Spring Boot

FROM eclipse-temurin:21-jre

# Create a non-root user for better security
RUN useradd -ms /bin/bash appuser

WORKDIR /app

# Copy the built jar into the image
COPY target/harborwatch-0.0.1-SNAPSHOT.jar app.jar

# Expose internal port
EXPOSE 8080

# Drop privileges
USER appuser

# Reasonable JVM memory handling inside containers
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# Start the app
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
