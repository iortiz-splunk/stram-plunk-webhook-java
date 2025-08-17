# Use a Java 17 base image
FROM eclipse-temurin:17-jre-focal

# Set the working directory
WORKDIR /app

# Copy the AppDynamics agent into the image
COPY appdynamics-agent /opt/appdynamics-agent

# Copy the Maven build (JAR file) from the build stage
# Assuming your JAR is named stream-splunk-webhook-0.0.1-SNAPSHOT.jar
COPY target/stream-splunk-webhook-0.0.1-SNAPSHOT.jar app.jar

# Expose the port the application runs on
EXPOSE 8000

# Command to run the Spring Boot application with the AppDynamics agent
# Using SHELL form to allow environment variable expansion
ENTRYPOINT java -javaagent:/opt/appdynamics-agent/javaagent.jar \
            -Dappdynamics.controller.host=${APPD_CONTROLLER_HOST} \
            -Dappdynamics.controller.port=${APPD_CONTROLLER_PORT} \
            -Dappdynamics.controller.ssl.enabled=${APPD_CONTROLLER_SSL_ENABLED} \
            -Dappdynamics.agent.accountName=${APPD_ACCOUNT_NAME} \
            -Dappdynamics.agent.accountAccessKey=${APPD_ACCESS_KEY} \
            -Dappdynamics.agent.applicationName=${APPD_APPLICATION_NAME} \
            -Dappdynamics.agent.tierName=${APPD_TIER_NAME} \
            -Dappdynamics.agent.nodeName=${APPD_NODE_NAME} \
            -jar app.jar