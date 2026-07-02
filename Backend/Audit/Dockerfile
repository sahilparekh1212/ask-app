# Build context: repo root
# docker build -f Audit/Dockerfile -t audit-service:latest .

FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
# Sibling module build files so Gradle can resolve the multi-project graph (settings.gradle
# includes common + Audit + Auth). Survey was removed from the project — do not COPY it.
COPY Auth/build.gradle Auth/build.gradle
# common is a real compile-time dependency of Audit (AuditEvent + ratelimit/), not just a stub.
COPY common/build.gradle common/build.gradle
COPY common/src common/src
COPY Audit/build.gradle Audit/build.gradle
COPY Audit/src Audit/src
RUN chmod +x gradlew && ./gradlew :Audit:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/Audit/build/libs/*.jar app.jar
RUN chgrp -R 0 /app && chmod -R g=u /app
EXPOSE 8083
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
