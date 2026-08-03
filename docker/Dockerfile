# syntax=docker/dockerfile:1.7
#
# One file builds all three LinkFlow images, selected with `--target`:
#
#   docker build -f docker/Dockerfile --target app     -t linkflow-app .
#   docker build -f docker/Dockerfile --target gateway -t linkflow-gateway .
#   docker build -f docker/Dockerfile --target web     -t linkflow-web .
#
# They previously had a Dockerfile each, which meant three full Maven builds of the same reactor
# and three copies of the same boilerplate that had to be kept in step. Sharing one build stage
# compiles the project once; the runtime stages differ only in which artifact they pick up.

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Deliberately no "copy the poms first, then dependency:go-offline" layer. That trick needs every
# module listed by hand, and the list silently rotted when a module was added — the pre-fetch then
# fails on a parent that references a missing child. The cache mount below keeps the local
# repository between builds, which is what made the pre-fetch worth having in the first place.
COPY . .

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -e package -DskipTests

# Unpack each fat jar into Spring Boot's layers so that dependencies, which rarely change, land in
# earlier image layers than application code, which changes on every commit. A code-only change
# then rewrites a few hundred kilobytes instead of the whole ~70 MB jar.
RUN set -eu; \
    for service in app gateway web; do \
        java -Djarmode=tools -jar "linkflow-${service}"/target/"linkflow-${service}"-*.jar \
            extract --layers --launcher --destination "/layers/${service}"; \
    done

# ---------------------------------------------------------------------------
# Runtime base
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# A numeric, non-root UID: a container escape should not begin with root, and stating the UID
# numerically (rather than only by name) is what lets an orchestrator enforce runAsNonRoot.
RUN addgroup -S -g 1001 linkflow \
    && adduser -S -u 1001 -G linkflow -h /app linkflow

WORKDIR /app

# MaxRAMPercentage, not -Xmx: the JVM then tracks whatever memory limit the container is given
# instead of needing the Dockerfile rebuilt when the limit changes.
#
# ExitOnOutOfMemoryError because a JVM that survives an OOM is the worst outcome — it stays
# "healthy" while failing requests unpredictably. Dying lets the orchestrator replace it.
#
# Headless because QR generation goes through zxing's MatrixToImageWriter, which touches
# java.awt.image; without it the JVM may try to reach a display that does not exist.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 \
-XX:+ExitOnOutOfMemoryError \
-Djava.awt.headless=true \
-Dfile.encoding=UTF-8"

USER 1001:1001

# `exec` hands PID 1 to the JVM so it receives SIGTERM directly. Without it the shell would hold
# PID 1, swallow the signal, and Spring's graceful shutdown would never run — in-flight requests
# would be cut off on every deploy. The shell is still needed to expand JAVA_OPTS.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

# ---------------------------------------------------------------------------
# linkflow-app — the modular monolith
# ---------------------------------------------------------------------------
FROM runtime AS app

# Ordered least- to most-frequently changed, so a code change invalidates only the last layer.
COPY --from=build --chown=1001:1001 /layers/app/dependencies/ ./
COPY --from=build --chown=1001:1001 /layers/app/spring-boot-loader/ ./
COPY --from=build --chown=1001:1001 /layers/app/snapshot-dependencies/ ./
COPY --from=build --chown=1001:1001 /layers/app/application/ ./

EXPOSE 8081

# Readiness, not plain health: this reports whether the instance can actually serve, which is what
# dependent services should wait for. wget and grep come from busybox, so nothing extra is
# installed just to answer this.
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8081/actuator/health/readiness | grep -q '"status":"UP"'

# ---------------------------------------------------------------------------
# linkflow-gateway — public entry point
# ---------------------------------------------------------------------------
FROM runtime AS gateway

COPY --from=build --chown=1001:1001 /layers/gateway/dependencies/ ./
COPY --from=build --chown=1001:1001 /layers/gateway/spring-boot-loader/ ./
COPY --from=build --chown=1001:1001 /layers/gateway/snapshot-dependencies/ ./
COPY --from=build --chown=1001:1001 /layers/gateway/application/ ./

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q '"status":"UP"'

# ---------------------------------------------------------------------------
# linkflow-web — server-rendered UI
# ---------------------------------------------------------------------------
FROM runtime AS web

COPY --from=build --chown=1001:1001 /layers/web/dependencies/ ./
COPY --from=build --chown=1001:1001 /layers/web/spring-boot-loader/ ./
COPY --from=build --chown=1001:1001 /layers/web/snapshot-dependencies/ ./
COPY --from=build --chown=1001:1001 /layers/web/application/ ./

EXPOSE 8082

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8082/actuator/health/readiness | grep -q '"status":"UP"'
