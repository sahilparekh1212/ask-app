# Build context: repo root
# docker build -f Auth/Dockerfile -t auth-service:latest .

FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
# Stub other modules so Gradle can resolve the project graph without their sources
COPY Audit/build.gradle Audit/build.gradle
COPY Survey/build.gradle Survey/build.gradle
# Full source only for this service
COPY Auth/build.gradle Auth/build.gradle
COPY Auth/src Auth/src
RUN chmod +x gradlew && ./gradlew :Auth:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/Auth/build/libs/*.jar app.jar
# OpenShift runs containers as an arbitrary UID in the root group
RUN chgrp -R 0 /app && chmod -R g=u /app
EXPOSE 8085
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
