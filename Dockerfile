# goAML platform — multi-stage container image.
# Phase 1: minimal image so the jar can boot in a container.
# Phase 13 adds a frontend build stage (Vite → static/).
# Phase 14 finalizes layered/optimized image + non-root user + JVM tuning.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
