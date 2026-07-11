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
# Auth/src is not a compile dependency of :Audit, but Audit's processResources bundles the
# backend source (Auth + Audit + common) into the RAG corpus so the assistant can read the
# actual deployed code — so Auth's source must exist in the build stage.
COPY Auth/src Auth/src
# common is a real compile-time dependency of Audit (AuditEvent + ratelimit/), not just a stub.
COPY common/build.gradle common/build.gradle
COPY common/src common/src
COPY Audit/build.gradle Audit/build.gradle
COPY Audit/src Audit/src
# RAG corpus inputs: Audit's processResources bundles the backend README + docs/ (and the
# backend source above) into the jar (rag-corpus/), so they must exist in the build stage
# (.dockerignore un-ignores them).
COPY README.md README.md
COPY docs docs
# More RAG corpus inputs — the observability + deployment topology (in this Backend context):
# Prometheus/Loki/Tempo/Grafana configs, the compose stack, and the OpenShift manifests.
COPY monitoring monitoring
COPY docker-compose.yml docker-compose.prod.yml docker-compose.ghcr.yml docker-compose.scale.yml ./
COPY kafka kafka
COPY openshift openshift
# The Angular UI source lives ABOVE this build context (UI/ is a sibling of Backend/), so it is
# supplied as a named build context and copied to /UI — a sibling of the Gradle root (/workspace)
# — so processResources' "${rootProject}/../UI" resolves to it exactly as it does on a host build.
# Callers must pass it: docker build --build-context ui=../UI ...  (compose: additional_contexts).
COPY --from=ui . /UI
RUN chmod +x gradlew && ./gradlew :Audit:bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-alpine
# Patch Alpine OS packages beyond what the base tag shipped (the base-image CVEs Trivy flags —
# e.g. p11-kit-trust — are fixed in newer apk packages before the temurin image is rebuilt).
RUN apk --no-cache upgrade
WORKDIR /app
COPY --from=builder /workspace/Audit/build/libs/*.jar app.jar
RUN chgrp -R 0 /app && chmod -R g=u /app
EXPOSE 8083
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
