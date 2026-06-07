# goAML platform — production container image (Phase 14).
# One image serves the REST API + the React SPA. Three stages:
#   1) web     — build the Vite SPA (Node)
#   2) build   — bundle the SPA onto the classpath, build a layered Spring Boot jar (Gradle, JDK)
#   3) runtime — extracted layers on a slim JRE, non-root.
# Build:  docker build -t goaml:dev .
# Run:    docker run --rm -p 8080:8080 --env-file .env goaml:dev   (needs Postgres + config via env)

# ---- 1) SPA build ----------------------------------------------------------------------------------
FROM node:18-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build      # -> /web/dist

# ---- 2) backend build (+ embed SPA) ----------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
# Put the built SPA on the classpath so Spring serves it (config/web/SpaWebConfig).
COPY --from=web /web/dist/ ./src/main/resources/static/
RUN ./gradlew --no-daemon clean bootJar -x test
# Explode the layered jar so Docker can cache dependency layers separately from app code.
RUN java -Djarmode=layertools -jar build/libs/*.jar extract --destination build/extracted

# ---- 3) runtime ------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# Run as a non-root system user.
RUN addgroup --system goaml && adduser --system --ingroup goaml --no-create-home goaml
# Ordered most-stable → most-volatile for layer caching.
COPY --from=build /workspace/build/extracted/dependencies/ ./
COPY --from=build /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/build/extracted/application/ ./
USER goaml
EXPOSE 8080
# Container-aware heap sizing; override at runtime via JAVA_TOOL_OPTIONS or JVM flags.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
# Liveness/readiness are driven by the orchestrator (Helm probes hit /actuator/health/*).
# Default: the web app (REST API + SPA + MCP server). The SAME image also runs the Phase-12 CLI — pass
# `--cli` as the first argument (e.g. `docker run … goaml:tag --cli lookups --token …`); it boots a non-web
# context, runs the command, and exits.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
