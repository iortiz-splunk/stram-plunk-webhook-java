# Use a Java 17 base image
FROM eclipse-temurin:17-jre-focal

# Set the working directory
WORKDIR /app

# Copy the Maven build (JAR file) from the build stage
# Assuming your JAR is named stream-splunk-webhook-0.0.1-SNAPSHOT.jar
COPY target/stream-splunk-webhook-0.0.1-SNAPSHOT.jar app.jar

# Expose the port the application runs on
EXPOSE 8000

# Command to run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]