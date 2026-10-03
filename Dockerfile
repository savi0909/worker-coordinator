FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY coordinator-service/target/coordinator-service-0.1.0-SNAPSHOT.jar app.jar
USER 10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65"
ENTRYPOINT ["java","-jar","app.jar"]
