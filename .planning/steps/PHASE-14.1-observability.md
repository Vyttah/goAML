# Phase 14.1 — Observability baseline

> **Status: ✅ DONE (2026-06-07).**
> Part of [../plans/phase-14-infra.md](../plans/phase-14-infra.md). First step of Phase 14 — Prometheus
> metrics, JSON logging, and per-request correlation IDs on top of the existing Actuator probes.

---

## 1. Goal & why
Make the running app observable in production: a Prometheus scrape endpoint, structured (JSON) logs for
aggregation, and a correlation id on every request so logs tie together. The Actuator health/liveness/
readiness probes already exist (Phase 2 + the readiness `db` group) — this fills the metrics + logging gaps.

## 2. What was built

| File | Role |
|---|---|
| `build.gradle` | +`io.micrometer:micrometer-registry-prometheus` (runtimeOnly) + `net.logstash.logback:logstash-logback-encoder:7.4`; added `config/observability/**` to the JaCoCo gate. |
| `config/observability/CorrelationIdFilter` | `OncePerRequestFilter` at `HIGHEST_PRECEDENCE`: reads/generates `X-Correlation-Id` → SLF4J MDC (`correlationId`) → response header; clears MDC in `finally`. |
| `resources/logback-spring.xml` | Non-`prod` → human console pattern (honors `logging.pattern.console`, incl. `%X{correlationId}`); `prod` profile → JSON via `LogstashEncoder` (includes `correlationId` + an `app` field). |
| `resources/application.yml` | +`management.prometheus.metrics.export.enabled: true` (see decision below). |

## 3. Key understanding / decisions
- **`/actuator/prometheus` needed the registry** — exposure already listed `prometheus`, but without
  `micrometer-registry-prometheus` on the classpath the endpoint 404'd. The artifact ships the **new**
  client (`io.micrometer.prometheusmetrics.PrometheusMeterRegistry`), which Boot 3.3 auto-configures.
- **Tests disable metrics export by default** — the condition report showed
  `@ConditionalOnEnabledMetricsExport … management.defaults.metrics.export.enabled is considered false`:
  Spring Boot turns metrics export off in `@SpringBootTest` (the old `@AutoConfigureMetrics` enabler was
  **removed in Boot 3.x**). Fix = set the **specific** key `management.prometheus.metrics.export.enabled=true`
  in application.yml; the condition checks the specific key *before* the test-disabled `defaults`, so it's
  honored in **both** tests and production (where it's the default anyway).
- **Correlation id runs first** — `HIGHEST_PRECEDENCE` so the id is in the MDC for the whole chain
  (including Spring Security), and is always cleared to avoid leaking across pooled threads.
- **JSON logs are `prod`-only** — dev/test keep the readable pattern; Boot 3.3 has no built-in structured
  logging (that's 3.4+), hence logstash-logback-encoder, scoped to the `prod` profile.

## 4. Tests
- `CorrelationIdFilterTest` (3, unit): generates an id when absent; reuses an inbound id; treats blank as
  absent; echoes the header + clears the MDC afterwards. (Covers the gated `config/observability/**`.)
- `ObservabilityIT` (3, `@SpringBootTest` + Testcontainers + MockMvc): `/actuator/prometheus` → 200 with
  `jvm_` metrics; every response carries `X-Correlation-Id`; an inbound id is echoed.

## 5. Verification
`./gradlew test jacocoTestCoverageVerification` → **BUILD SUCCESSFUL** (full suite + gate, incl. the new
metrics/filter tests). `git status` scoped to the observability files.

---

## Outcome
✅ Metrics + JSON logging + correlation IDs are wired (probes already existed). Next: **14.2** — finalize the
Dockerfile (node SPA stage → layered bootJar → non-root JRE runtime) + `.dockerignore`.
