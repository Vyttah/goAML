# Phase 14.2 — Dockerfile finalize + `.dockerignore`

> **Status: ✅ DONE (2026-06-07).**
> Part of [../plans/phase-14-infra.md](../plans/phase-14-infra.md). Second step of Phase 14 — the production
> container image: one image serving the REST API **and** the React SPA.

---

## 1. Goal & why
Replace the Phase-1 local-dev Dockerfile with a finalized, hardened, layered multi-stage build that bakes
the SPA into the jar and runs as non-root — the artifact Helm (14.3) and CI/CD (14.4) deploy.

## 2. What was built
- **`Dockerfile`** — three stages:
  1. **web** (`node:18-alpine`): `npm ci` + `npm run build` → `/web/dist`.
  2. **build** (`temurin:21-jdk`): copies `src`, **copies the SPA `dist/` onto the classpath**
     (`src/main/resources/static/` → served by `SpaWebConfig`), `bootJar -x test`, then explodes the
     **layered jar** (`-Djarmode=layertools extract`).
  3. **runtime** (`temurin:21-jre`): copies the extracted layers most-stable→most-volatile, runs as a
     **non-root** system user (`goaml`), `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`, launches via
     `JarLauncher`.
- **`.dockerignore`** — trims the context and **keeps `src/test/` out of the image** (so the real-PII
  sample XMLs never enter the build context).

## 3. Key understanding / decisions
- **SPA via the Dockerfile node stage** (D1) — Gradle stays Node-free; the SPA reaches the classpath by the
  build stage copying `dist/` into `src/main/resources/static/` before `bootJar`. (Local `./gradlew bootJar`
  still ships only the committed placeholder shell; the real SPA is built in the image.)
- **`.dockerignore` anchoring bug, found + fixed** — `build/` / `**/build/` also matched the
  **`engine/build/` Java package**, so the first image build failed `compileJava`
  (`package com.vyttah.goaml.engine.build does not exist`). Fixed to **`/build/`** (root Gradle output only).
- **Layered jar** for cache-friendly layers; **non-root** + container-aware heap (`MaxRAMPercentage`).
- **`JarLauncher` main class** is the Boot 3.2+ package `org.springframework.boot.loader.launch.JarLauncher`.

## 4. Verification (real `docker build` + run)
- `docker build -t goaml:dev .` → **succeeds** (web + Gradle + layered extract).
- Ran the image against a throwaway Postgres on a shared docker network:
  - `/` serves the **SPA shell** (`id="root"`).
  - `/actuator/prometheus` → **200**.
  - every response carries **`X-Correlation-Id`**.
  - `/actuator/health/liveness` **UP**, `/actuator/health/readiness` **UP** (the groups Helm probes use).
  - container runs as **non-root** (`uid=100(goaml)`).
  - (Top-level `/actuator/health` was DOWN only because the bare smoke env had no Redis — the Redis health
    contributor; it's excluded from the readiness group, so orchestration is unaffected.)

## 5. Outcome
✅ A finalized, hardened, SPA-bundled image that boots and serves correctly. Next: **14.3** — the Helm chart
(`helm/goaml/`) with probes wired to the readiness/liveness groups.
