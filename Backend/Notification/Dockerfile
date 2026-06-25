# Build context: repo root
# docker build -f Notification/Dockerfile -t notification-service:latest .

FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY Auth/build.gradle Auth/build.gradle
COPY Audit/build.gradle Audit/build.gradle
COPY Notification/build.gradle Notification/build.gradle
COPY Notification/src Notification/src
RUN chmod +x gradlew && ./gradlew :Notification:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/Notification/build/libs/*.jar app.jar
RUN chgrp -R 0 /app && chmod -R g=u /app
EXPOSE 8084
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
