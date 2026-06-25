# Build context: repo root
# docker build -f Audit/Dockerfile -t audit-service:latest .

FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY Auth/build.gradle Auth/build.gradle
COPY Account/build.gradle Account/build.gradle
COPY Notification/build.gradle Notification/build.gradle
COPY Audit/build.gradle Audit/build.gradle
COPY Audit/src Audit/src
RUN chmod +x gradlew && ./gradlew :Audit:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/Audit/build/libs/*.jar app.jar
RUN chgrp -R 0 /app && chmod -R g=u /app
EXPOSE 8083
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
