# Phase 14 — Infra (Docker · Helm · observability · CI/CD)

> **Status: 🔲 PROPOSED (2026-06-07) — awaiting approval before implementation.**
> Roadmap **Phase 14** (runs after 13, before 12). Turns the built product into a **deployable**: one
> container image that serves the REST API **and** the React SPA, a Helm chart for EKS, an observability
> baseline, and GitHub Actions CI. No new product features — packaging, deployment, and ops only.

---

## 1. What this phase is, and why
Phases 1–13 produced a working backend + SPA but only a **local-dev** Dockerfile + `docker-compose`. Phase
14 makes it shippable per docs/00 + docs/02 §6: a finalized **multi-stage image** (build the SPA → bundle
into the jar → run as non-root), a **Helm chart** (Deployment/Service/Ingress/HPA/ConfigMap/SA+IRSA/secret
wiring), an **observability baseline** (Prometheus metrics + JSON logs + correlation IDs on top of the
existing Actuator probes), and **GitHub Actions** that actually run the backend + frontend gates.

## 2. Scope (your decisions this session)
- **SPA → jar = Dockerfile-only node stage** (D1). Gradle stays Node-free; the image's node stage builds the
  SPA and the JDK stage copies `dist/` onto the classpath (`src/main/resources/static`) before `bootJar`.
- **CI/CD = real CI gates + image build; deploy as a gated template** (D5). `ci.yml` runs the backend +
  frontend gates; `cd.yml` builds the image and has ECR-push + `helm upgrade` steps **gated on secrets**
  (inert until an AWS account/EKS/GitHub secrets exist — none are wired today).
- **Helm = full chart** (D7): Deployment, Service, Ingress+TLS, HPA, ConfigMap, ServiceAccount (IRSA),
  secret wiring, `values.yaml`.

## 3. Current state (researched)
- `Dockerfile` exists but is **local-dev only** (JDK build → JRE run; no SPA stage, no non-root, no layering).
- `docker-compose.yml` exists (postgres/localstack/redis dev deps) — stays as-is (dev), maybe a doc note.
- **No** `.github/`, **no** `helm/`, **no** Gradle node task.
- Actuator probes + `health,info,prometheus` exposure are configured, **but `micrometer-registry-prometheus`
  is not on the classpath** → `/actuator/prometheus` 404s today. The log pattern already references
  `%X{correlationId}` but **nothing populates that MDC key yet**.

## 4. Decisions

| # | Decision | Choice |
|---|----------|--------|
| D1 | SPA packaging | **Dockerfile node stage** builds the SPA; JDK stage copies `dist/` → `src/main/resources/static` before `bootJar`, so the SPA ships on the classpath (served by the 13.1 `SpaWebConfig`). Gradle stays Node-free; `./gradlew test` unaffected. (Local `./gradlew bootJar` serves only the committed placeholder shell — the real SPA is built in the image.) |
| D2 | Image hardening | Multi-stage: **node** (SPA) → **JDK** (layered `bootJar`) → **JRE runtime**. Spring Boot **layered-jar extraction** for cache-friendly layers; **non-root** user; container-aware JVM (`-XX:MaxRAMPercentage`); `.dockerignore`. |
| D3 | Metrics | Add `io.micrometer:micrometer-registry-prometheus` (runtime) so `/actuator/prometheus` serves; it stays permitted (13.1) + unauthenticated for scraping. |
| D4 | Logging | `logback-spring.xml`: keep the human pattern for local/dev, **JSON logs under a `prod` profile** (logstash-logback-encoder). A `CorrelationIdFilter` (early `OncePerRequestFilter`) reads/generates `X-Correlation-Id` → MDC (`correlationId`) → response header, so logs correlate per request. |
| D5 | CI/CD | `.github/workflows/ci.yml` (real gates) + `cd.yml` (image build now; ECR push + `helm upgrade` **gated on secrets**, no-op without AWS). |
| D6 | Probes/health | Reuse existing Actuator config (readiness group already includes `db`); Helm wires liveness=`/actuator/health/liveness`, readiness=`/actuator/health/readiness`. |
| D7 | Helm | Full `helm/goaml/` chart (below). Secrets via `ExternalSecret` (External Secrets Operator) **or** a `secretRef` to a pre-created K8s secret — templated, not committed values. |
| D8 | Config | App config stays env-driven (already is: `SPRING_DATASOURCE_*`, `GOAML_*`); Helm ConfigMap supplies non-secret env, the SA+IRSA supplies AWS access (no static keys), secret wiring supplies `GOAML_JWT_SECRET` etc. |

## 5. Step breakdown (one commit per step, each green; per-step doc in `steps/`)

- **14.1 — Observability baseline.** Add `micrometer-registry-prometheus` (runtime); `logback-spring.xml`
  (dev pattern + `prod` JSON profile via logstash-logback-encoder); `CorrelationIdFilter` + register it
  early in the chain. Tests: MockMvc `/actuator/prometheus` → 200 with metrics; filter sets the MDC +
  echoes `X-Correlation-Id`. JaCoCo: add the filter's package to the gate.
- **14.2 — Dockerfile finalize + `.dockerignore`.** Multi-stage node→JDK→JRE per D1/D2 (layered extract,
  non-root, JVM container opts). Verify: `docker build` (if the daemon + network allow) → image boots
  (`/actuator/health` UP) and serves the SPA at `/`; else structural review + the existing local-dev compose
  still works. Update `docker-compose.yml` header note (dev-only).
- **14.3 — Helm chart (`helm/goaml/`).** `Chart.yaml`, `values.yaml`, `templates/` (deployment, service,
  ingress+TLS, hpa, configmap, serviceaccount[+IRSA], secret/externalsecret, `_helpers.tpl`, `NOTES.txt`).
  Probes per D6. Verify: `helm lint` + `helm template` (if helm installed) else structural YAML review.
- **14.4 — GitHub Actions.** `ci.yml`: backend job (Temurin 21 → `./gradlew test jacocoTestCoverageVerification`,
  Testcontainers uses the runner's Docker) + frontend job (Node 18 → `npm ci` → typecheck/lint/test/build).
  `cd.yml`: build the image on `main`; ECR login/push + `helm upgrade` steps `if:`-gated on AWS secrets.
- **14.5 — Docs/planning sync + merge.** `docs/02 §6`, `docs/03` (build/run image), `docs/00` infra status;
  ROADMAP/STATE/CLAUDE; per-step docs; fill Outcome; merge `phase-14/infra` → `main`.

## 6. Verification
- 14.1: `./gradlew test jacocoTestCoverageVerification` green incl. the new metrics/filter tests.
- 14.2: `docker build -t goaml:dev .` succeeds (network permitting); `docker run` → `/actuator/health` UP +
  SPA served at `/` + `/api/v1/...` reachable. (If the daemon/network can't, document + structural review.)
- 14.3: `helm lint helm/goaml` + `helm template helm/goaml` render cleanly (if helm present).
- 14.4: workflows are valid YAML and the gate commands match what we run locally; CD deploy steps are
  correctly gated (no-op without secrets).
- `git status` scoped to infra files + the 14.5 docs.

## 7. What this phase does NOT do
Provision real AWS (account/EKS/ECR/RDS) or run a live deploy (no account/secrets/remote); a Gradle node
task (we chose the Dockerfile node stage); the plugin/MCP/CLI (**Phase 12, last** — needs a minor infra
touch-up then: MCP HTTP route in ingress + `--cli` run-mode); suite integration (**Phase 1.5, deferred**).

## 8. Notes / risks
- **Network/daemon for `docker build`** — `npm ci` + Gradle deps download in-image; on a flaky network the
  build may need retries. The image build is also exercised by `cd.yml` on a clean runner.
- **logstash-logback-encoder** is the JSON encoder (Spring Boot 3.3 has no built-in structured logging;
  that's 3.4+). Scoped to the `prod` profile so dev logs stay human-readable.
- **PII-sample reminder (carried):** real-PII sample XMLs remain in git history — must be anonymized/purged
  **before** configuring any remote and pushing (CD assumes a remote later).
- **Helm secret strategy** is templated both ways (ExternalSecret or `secretRef`) so it fits whichever the
  target cluster uses; no secret values are committed.

---

## Outcome — ✅ DONE (2026-06-07)

Shipped on branch `phase-14/infra` in 5 gated steps (`e24bce4`…14.5), each with a per-step doc
(`steps/PHASE-14.1..14.5`).

- **14.1 Observability** — `micrometer-registry-prometheus` (`/actuator/prometheus`), `prod`-profile JSON
  logs (logback + logstash encoder), `CorrelationIdFilter` (`X-Correlation-Id` → MDC). Tested
  (unit + Testcontainers IT). Found + fixed: Boot disables metrics export in tests → enabled via the
  specific `management.prometheus.metrics.export.enabled` key (honored in tests **and** prod).
- **14.2 Dockerfile** — 3-stage (node SPA → layered `bootJar` with SPA on the classpath → non-root JRE) +
  `.dockerignore`. **Verified by a real `docker build` + run**: SPA served, `/actuator/prometheus` 200,
  correlation header, liveness/readiness UP, non-root. Found + fixed: `.dockerignore` `build/` also matched
  the `engine/build/` package → anchored to `/build/`.
- **14.3 Helm** — full `helm/goaml/` chart; **`helm lint` clean**, renders across all modes (default,
  ingress+ExternalSecret+IRSA, chart-managed secret).
- **14.4 GitHub Actions** — `ci.yml` (backend + frontend gates) + `cd.yml` (image build always; ECR push +
  `helm upgrade` gated on AWS secrets via OIDC). YAML validated; gate parity with local.
- **14.5** — docs/planning sync + merge to `main`.

**Deviations / honest scope:** real AWS provisioning + a live deploy are out (no account/cluster/remote) —
CD is wired but inert until secrets exist. `helm` wasn't installed locally (fetched a throwaway binary to
lint/template). Per the chosen decision there is **no Gradle node task** (the Dockerfile node stage handles
the SPA).

**Carried-forward:** when Phase 12 ships (after infra), add the MCP HTTP route to the Helm ingress + the
`--cli` run-mode. PII sample XMLs must be purged from git history before any first push to a remote.

**Next:** Phase 12 (plugin/MCP/CLI) — the last phase.
