# goAML Platform — project orientation

> Auto-loaded every session. Keep this short; it's a map, not the content.

**What:** multi-tenant RegTech platform that builds, validates, submits & tracks **goAML** AML reports to
the **UAE FIU** (goAML Web B2B REST), for many client Reporting Entities. Java 21 · Spring Boot 3.3 ·
Gradle · PostgreSQL (schema-per-tenant) · JWT/RBAC/audit. Sold **standalone** and as part of Vyttah's
suite (accounting/ERP + AML screening).

## Start here (in order)
1. **`.planning/STATE.md`** — where we are / what's next. **Read first.**
2. **`.planning/ROADMAP.md`** — the 14-phase build order + status (and Phase 1.5).
3. **`.planning/PROJECT.md`** — what it is, requirements, **Key Decisions**, constraints.
4. **`.planning/discussion-log.md`** — the running Q&A/decision history (how we got here).
5. **`docs/`** — full developer documentation (business → architecture → domain → engine → security →
   testing → glossary). `docs/00-implementation-plan.md` is the original plan;
   `docs/16-operations-dr-retention.md` is the backup/DR/records-retention + poller-topology ops policy.

## Active plans (`.planning/plans/`)
- **`xsd-first-foundation.md`** — current priority: build the domain over the real `goAMLSchema.xsd`
  (5.0.2) via xjc-generated JAXB + an XSD validation gate. The authoritative XSD + 2 real DPMSR samples
  are vendored at `src/main/resources/xsd/goaml/5.0.2/` and `src/test/resources/samples/`.
- **`integration-and-auth-architecture.md`** — suite positioning, **Phase 1.5** accounting/screening
  integration, and the **unified-auth** design (goAML keeps its own JWT + a federated token-exchange
  on-ramp). **The code shipped** (1.5a/b/c merged to `main`; accounting is REST, not RabbitMQ). The contract
  reference is `docs/14-suite-integration.md`; the live-wiring steps are `plans/go-live-integration-runbook.md`.
- **`goaml-as-aml-microservice.md`** — the **frontend-direct** track: the AML cockpit calls goAML directly
  (browser assembles the full DPMSR → `POST /api/v1/reports`). Built across both repos (goAML pieces on
  `feature/goaml-frontend-direct`; AML pieces on `feature/goaml-integration`, unmerged).
- **`phase-12-plugin-and-mcp-harness.md`** — the Claude plugin + MCP harness (done).

## Key facts to not re-derive
- **Status: ALL 14 phases committed** (+ XSD-first foundation + layer-first refactor) — the standalone product
  is complete. Built order was 13 → 14 → 12; **Phase 12 (plugin/MCP/CLI) was the last** and is done. **Phase 1.5
  (suite integration + federated auth) is DONE/merged** — 1.5a federated token-exchange (`/api/v1/auth/federated/token`
  + `external_identity`/`trusted_service`), 1.5b accounting integration (**REST, not RabbitMQ** — both models +
  reportability check), 1.5c screening push/seed are all built and merged to `main` (2026-06-08→09). A further
  **suite-cockpit track (Phases A–D)** and a **frontend-direct pivot** then layered the AML cockpit on top (the
  cockpit's Create-Transaction page builds a full DPMSR in the browser and POSTs it straight to goAML); only
  **Phase E (live FIU B2B, external)** remains. **Phase 12** added a Spring AI **MCP
  server** (SSE at `/api/v1/mcp/**`, tenant-scoped + role-gated, MLRO-gated dry-run-first submission), a Claude
  **plugin** (`plugin/goaml/` + repo-root `.claude-plugin/marketplace.json`), and a **`--cli`** run-mode of the
  same jar — REST/MCP/CLI all delegate to the same engine/services (parity). See `docs/13-plugin-mcp-cli.md`.
  **Phase 14** added the deployable infra: a
  finalized 3-stage **Dockerfile** (node SPA build → layered `bootJar` with the SPA on the classpath →
  non-root JRE), a full **Helm chart** (`helm/goaml/`: Deployment+probes, Service, Ingress+TLS, HPA,
  ConfigMap, ServiceAccount+IRSA, 3 secret strategies), an **observability baseline**
  (`micrometer-registry-prometheus` → `/actuator/prometheus`, `prod` JSON logs, `CorrelationIdFilter`), and
  **GitHub Actions** (`ci.yml` gates; `cd.yml` image build + secret-gated ECR/EKS deploy). When Phase 12
  ships, a minor infra touch-up follows (MCP HTTP route in the Helm ingress + `--cli` run-mode).
  **Phase 13** added the **`frontend/` SPA** (Vite+React+TS+Ant
  Design over the REST API: auth → dashboard → full DPMSR builder → detail/submit/track/attachments →
  import → notifications + reference browser → admin; 58 Vitest specs) plus its REST enablers
  (`controller/lookup/`, `controller/admin/` + `service/admin/`, CORS + SPA-serving) and a gated
  `config/dev/DevDataSeeder` (`goaml.dev.seed.enabled`) for local review. Surfaced (not faked) backend gaps:
  report XML preview, validation re-fetch, attachment download. Phase 7 wired the engine + b2b to HTTP (the
  **DPMSR report lifecycle REST API**: `/api/v1/reports` create/validate/submit/status, MLRO-gated submit
  over `report`/`submission` tenant tables); Phase 8 added **S3 attachments** — multipart upload (proxied
  through the API) → S3, pulled into the submission ZIP at submit; `attachment` tenant table +
  `S3StorageClient`; attach/list/remove REST (AV scanning deferred). Phase 9 added **`scheduler/`** — a
  `@Scheduled` `SubmissionStatusPoller` that refreshes FIU status across ACTIVE tenants (poll-only, no
  auto-resubmit; plain `@Scheduled`, no distributed lock) + a bounded transient `RetryService`. Phase 10
  added **`notification/`** — a per-tenant in-app `notification` store + an `integration/aws/SesClient`
  (SES email, **gated off by default**), fired from the **`SubmissionService` seam** (`submit()` +
  `refreshStatus()` — covers the poller, on-demand status, and submit) to the report **author + tenant
  MLROs**; best-effort + isolated (`safeNotify` never throws out); `GET/POST /api/v1/notifications`. Phase 11
  added **`ingestion/`** — file import as a persisted `import_job` with row-level results: **goAML XML**
  (`GoamlXmlImporter` reuses unmarshal + validators) + a flat **DPMSR CSV** (`CsvImporter` → row →
  `DpmsrCreateRequest` → the existing `ReportService.create`, no parallel persist); synchronous, per-row
  isolation, fail-fast whole-file rejection; `POST/GET /api/v1/imports`. (Also fixed a latent audit bug:
  `DefaultAuditService` now **restores** the caller's `TenantContext` instead of clearing it.) Flow is
  manually testable via the API (live submit needs per-tenant FIU config + the `goaml-attachments` bucket;
  live email needs a verified SES sender). The CSV template is provisional pending FIU sign-off.
- **First report type = `DPMSR`** (precious-metals dealers; cash ≥ AED 55,000). All 17 schema codes later.
- **DPMSR is activity-shaped** (goods + parties, no `<transaction>` block).
- **Auth:** self-managed HS256 JWT, RBAC roles SUPER_ADMIN/TENANT_ADMIN/MLRO/ANALYST; tenant routing via
  the `schema` JWT claim. **FIU B2B creds ≠ user login** (per-tenant, Secrets Manager).
- The `aml-ai-skills` Claude skill is a **different** project (a Python chatbot) — **do not** use its
  build order here.

## Conventions
- Money = `BigDecimal`; timestamps = `OffsetDateTime` (UTC). Flyway owns the schema (`ddl-auto: none`).
- Build/test: `docker compose up -d postgres localstack redis` then `./gradlew test` (Postgres uses
  Testcontainers; the Phase 6 LocalStack/Redis integration tests run against compose and skip if absent).
- Update `.planning/STATE.md` + `ROADMAP.md` when a phase starts/finishes; append `discussion-log.md` for
  decisions. **Commit `.planning/` + `docs/`** — they're the durable project memory.
