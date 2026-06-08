# ROADMAP — goAML Platform

> The 14-phase build order with status. Each phase is independently testable and lands as one git commit.
> Update the Status column when a phase completes (and mark it in [STATE.md](STATE.md)).
> Detail + Phase 6 breakdown: [docs/09-build-order-and-roadmap.md](../docs/09-build-order-and-roadmap.md).

| # | Phase | Status | Commit |
|---|-------|--------|--------|
| 1 | **Skeleton** — Gradle, Spring Boot 3.3/Java 21, package layout, Dockerfile, docker-compose, Flyway shared baseline, Actuator health | ✅ done | `8764ed3` |
| 2 | **Multi-tenancy + security foundation** — shared entities, Hibernate SCHEMA multi-tenancy, tenant provisioning + per-tenant Flyway, JWT + RBAC + audit | ✅ done | `1f1933e` |
| 3 | **`domain/`** — JAXB POJOs for the `<report>` tree + round-trip/ordering tests | ✅ done | `8ea6fdf` |
| 4 | **`engine/` builders + marshaller** + golden-file XML per report type | ✅ done | `220b763` |
| 5 | **`engine/` validation + UAE jurisdiction + lookups** | ✅ done | `102484d` |
| 6 | **`integration/aws/` Secrets Manager + Redis B2B token cache → `b2b/` goAML REST client; LocalStack/Redis/WireMock tests + JaCoCo ≥90% gate** (Secrets-only; S3/SES → 8/10) | ✅ done | `e6a03d6`…`81f61b0` |
| 7 | **`persistence/` + `service/` + `web/`** DPMSR reports/submissions REST — wires the engine + b2b to HTTP (Testcontainers + WireMock E2E; JaCoCo gate) | ✅ done | `154a2f5`…`82af99f` |
| 8 | **S3 attachments** — `S3StorageClient` + `attachment` table; multipart upload (proxied through the API) → S3, pulled into the submission ZIP; attach/list/remove REST; LocalStack IT + E2E (AV scanning deferred) | ✅ done | `07afd21`…`77de56e` |
| 9 | **`scheduler/`** — `@Scheduled` `SubmissionStatusPoller` across ACTIVE tenants (reuses `refreshStatus`) + bounded transient `RetryService`; poll-only (no auto-resubmit), plain `@Scheduled`; Testcontainers IT | ✅ done | `015ea61`…`9530bf3` |
| 10 | **`notification/`** — per-tenant in-app store + SES email (`SesClient`), fired off report transitions at the `SubmissionService` seam (poller + on-demand + submit) to author + tenant MLROs; email gated off by default; `GET/POST /api/v1/notifications`; Testcontainers ITs. Plan: [plans/phase-10-notifications.md](plans/phase-10-notifications.md) | ✅ done | `99e3c75`…`6da1a5f` |
| 11 | **`ingestion/`** — file import as a persisted `import_job` with row-level results: goAML **XML** (`GoamlXmlImporter` reuses unmarshal+validators) + flat **DPMSR CSV** (`CsvImporter` → `DpmsrCreateRequest` → `ReportService.create`); `POST/GET /api/v1/imports`; sync + per-row isolation; MockMvc E2E. Plan: [plans/phase-11-ingestion.md](plans/phase-11-ingestion.md) | ✅ done | `dd9b54a`…(11.4) |
| 13 | **React frontend** (`frontend/`) — Vite+React+TS+AntD SPA: auth → dashboard → DPMSR builder → detail/submit/track → import → notifications + lookups browser → admin; + REST enablers (lookup API, admin API, CORS/SPA-serving). 58 Vitest specs; typecheck+lint+build gated. Plan: [plans/phase-13-react-frontend.md](plans/phase-13-react-frontend.md) | ✅ done | `8e76a40`…(13.11) |
| 14 | **Infra** — finalized multi-stage Dockerfile (SPA-bundled, layered, non-root), full Helm chart (`helm/goaml/`), observability baseline (Prometheus + JSON logs + correlation IDs), GitHub Actions CI + gated CD. Plan: [plans/phase-14-infra.md](plans/phase-14-infra.md) | ✅ done | `e24bce4`…(14.5) |
| 12 | **goAML Claude Plugin & MCP harness + `cli/`** — Spring AI MCP server (SSE at `/api/v1/mcp/**`) + a Claude plugin (skill/commands/hook/marketplace) + a `--cli` run-mode of the same jar; all three delegate to the same engine/services (REST/MCP/CLI parity), tenant-scoped + role-gated, with an MLRO-gated dry-run-first submission harness. Plan: [plans/phase-12-plugin-and-mcp-harness.md](plans/phase-12-plugin-and-mcp-harness.md) | ✅ done | `94a0dce`…(12.7) |

> **Build order (decided 2026-06-06):** remaining phases run **13 → 14 → 12**. Phase 12 (plugin/MCP/CLI)
> is deferred to last — it's dependency-safe (nothing depends on it; the frontend uses the REST API, and it
> only needs the now-complete Phases 6–11). Caveat: since infra (14) lands before 12, expect a minor infra
> touch-up when 12 ships (expose the MCP HTTP route in Helm/ingress + confirm the `--cli` run-mode).
> **Phase 1.5** (suite integration + federated auth) is **IN PROGRESS** (started 2026-06-08). Sub-phases:
> **1.5a federated auth ✅**, **1.5b accounting (REST, both models + reportability check) ✅**, 1.5c screening REST + form.
| **1.5a** | **Federated auth** — `goaml.auth.mode` (native\|federated\|both), V3 federated-identity migration (`external_identity`, `trusted_service`, `tenant_external_ref`, `tenant_goaml_config.auto_submit`), `ServiceCredentialValidator` (RS256 signed service assertion), `POST /api/v1/auth/federated/token` token-exchange (+ JIT provisioning). | ✅ done | `a4f1e4a`…`ef04cd9` |
| **1.5b** | **Accounting → goAML (REST)** — both models: client builds the DPMSR via the existing report API (Model 1, embedded-consumer E2E) **and** raw-invoice push → goAML reportability detection → auto-create draft → MLRO 1-click / `auto_submit`; idempotent + status pull; + a standalone `POST /api/v1/reportability/check`. Contract: [docs/14-suite-integration.md](../docs/14-suite-integration.md). (Was RabbitMQ — changed to REST 2026-06-08.) | ✅ done | `d4a50de`…(1.5b.6) |
| **1.5c** | **Screening → goAML** — REST push (party/KYC, same service-assertion auth) → report → XML; + goAML SPA form. | ⬜ todo | — |

> **Note on Phase 1.5:** the "1.5" label reflects its product priority, but it is **sequenced after the
> standalone core** (it depends on the engine, the b2b client, and submission existing). **2026-06-08
> decisions:** accounting integration is **REST, not RabbitMQ** (immediate verdict + status pull, one REST
> style shared with screening, no broker); both apps consume goAML as **embedded API clients** behind their
> own UIs (goAML = single system-of-record); accounting supports **both** "client builds the report" and
> "push raw invoice → goAML builds it". B/C wire-DTOs are gated on the Vyttah accounting/screening schemas.

## Phase 6 (DONE) — what shipped

- **6a `integration/aws/`** — `GoamlSecretsClient` reads per-tenant goAML creds from Secrets Manager
  (no separate KMS client). **Secrets-only**; `S3StorageClient`/`SesClient` deferred to Phases 8/10.
- **6b `b2b/`** — `GoamlB2bClient`/`RestGoamlB2bClient` (HTTP/1.1) + `TokenManager` (Redis token cache,
  401 re-auth); ops `postReport`/`getReportStatus`/`deleteReport`/`postMessage`/`getLookups`; typed errors.
- Tests: LocalStack + Redis integration (tagged/conditional) **and** Mockito/WireMock unit tests; JaCoCo
  ≥90% gate on the new packages (achieved ~98.7% instr). Per-step docs: `steps/PHASE-6.1..6.5`.

See [docs/10-b2b-submission-protocol.md](../docs/10-b2b-submission-protocol.md) for the wire protocol.

## Phase 13 (DONE) — what shipped

- **Backend enablers:** `controller/lookup/` (jurisdictions + lookup sets, authenticated) · `controller/admin/`
  + `service/admin/` (tenant provision/list = SUPER_ADMIN; user + goAML-config = TENANT_ADMIN) · env-gated
  CORS bean + SPA-serving `WebMvcConfigurer` + `SecurityConfig` permits. Held to the existing JaCoCo gate.
- **`frontend/` SPA** (Vite 5 · React 18 · TS · Ant Design 5 · TanStack Query · Zod · React Router · MSW ·
  Vitest+RTL): auth (JWT-claims identity, 401→login) → dashboard → **full DPMSR builder** (Zod mirror,
  lookup dropdowns, inline server validation) → report detail (MLRO submit + FIU status + attachments) →
  import (XML/CSV + per-row results) → notifications (bell + center) → reference browser → admin.
- **Dev seeder** (`config/dev/DevDataSeeder`, gated `goaml.dev.seed.enabled`) seeds a demo tenant + logins
  for local review. **58 Vitest specs**; `tsc`+ESLint+`vite build` gated per step. Per-step docs:
  `steps/PHASE-13.1..13.11`.
- **Known UI-surfaced backend gaps** (small future adds): report XML preview, re-fetching an existing
  report's validation result, and attachment download — no endpoints yet, flagged in-UI rather than faked.

## Phase 14 (DONE) — what shipped

- **Image:** finalized 3-stage `Dockerfile` (node SPA build → layered `bootJar` with the SPA on the
  classpath → non-root JRE runtime, container-aware JVM) + `.dockerignore` (keeps `src/test/` incl. PII
  samples out of the image). Verified by a real `docker build` + run (SPA served, prometheus 200,
  liveness/readiness UP, non-root).
- **Helm:** full `helm/goaml/` chart — Deployment (probes on the health groups, hardened security context),
  Service, Ingress+TLS, HPA, ConfigMap, ServiceAccount+IRSA, and 3 secret strategies (ExternalSecret /
  chart-managed / existing). `helm lint` clean; renders across modes.
- **Observability:** `micrometer-registry-prometheus` (`/actuator/prometheus`), `prod`-profile JSON logs
  (logback + logstash encoder), and a `CorrelationIdFilter` (`X-Correlation-Id` → MDC). Probes already existed.
- **CI/CD:** `.github/workflows/ci.yml` (backend + frontend gates) + `cd.yml` (image build always; ECR push
  + `helm upgrade` to EKS gated on AWS secrets via OIDC). Per-step docs: `steps/PHASE-14.1..14.5`.
- **Carried-forward touch-up:** when Phase 12 ships, expose the MCP HTTP route in the Helm ingress + add the
  `--cli` run-mode.

## Phase 12 (DONE) — what shipped

- **`mcp/`** — Spring AI 1.0.2 MCP server (SSE `/api/v1/mcp/sse`) inside the app; `JwtAuthFilter` authenticates
  every call → `TenantContext` + RBAC; `McpContextPropagationConfig` carries that across the sync server's
  Reactor thread hop. ~24 `@Tool`s spanning reference data, DPMSR build/validate/preview/create, **guarded**
  submit/status/messages, import, and admin — each delegating to the existing services (REST/MCP parity), with
  RBAC via `McpIdentity.requireAnyRole`. Submission is **MLRO-gated, dry-run-first, confirm-required,
  validate-first**.
- **`plugin/goaml/`** — a Claude plugin: `goaml` skill + `/goaml-build|validate|submit|status|import` commands +
  `.mcp.json` (remote SSE) + a pre-submit hook; published via the repo-root `.claude-plugin/marketplace.json`.
- **`cli/`** — the `--cli` run-mode of the same jar (picocli): `validate|preview|submit|status|import|lookups`,
  same services + same submit harness; `CliAuthenticator` resolves a `--token`. `SecurityConfig`/`AuthController`/
  `DefaultAuthService` made `@ConditionalOnWebApplication` so the non-web CLI context loads.
- Backend reuse-enablers: `ReportService.validate`/`previewXml` (closed the Phase-13 preview/validation gap),
  `SubmissionService.postMessage`, `engine/metadata/ReportTypeMetadata`. New deps: Spring AI BOM + MCP webmvc
  starter, reactor-core + context-propagation, picocli. Per-step docs: `steps/PHASE-12.1..12.7`.
- **Carried-forward infra touch-up DONE here:** MCP route + SSE guidance on the Helm ingress; `--cli` note on
  the Dockerfile. Deferred (documented): a lookups-refresh tool (no backend FIU-lookup sync exists yet).

## Detailed phase plans

- **Phase 12** — [plans/phase-12-plugin-and-mcp-harness.md](plans/phase-12-plugin-and-mcp-harness.md):
  full Claude plugin + MCP harness (read/build/validate tools buildable now; submit/track/import need
  Phases 6/7/9/11). Has 4 open decisions to confirm before building.
- **Phase 1.5** — [plans/integration-and-auth-architecture.md](plans/integration-and-auth-architecture.md):
  suite positioning, accounting (RabbitMQ) + screening integration, and the unified-auth token-exchange
  design. Docs-only so far; code sequenced after the standalone core.
- **XSD-first foundation** — [plans/xsd-first-foundation.md](plans/xsd-first-foundation.md): the current
  priority — build the domain over the real goAML 5.0.2 XSD.

## Open external inputs (gate live correctness, not the build)

UAE goAML XSD · per-tenant B2B URLs + credentials · UAE Business Rejection Rules · real UAE lookup
exports · CSV import template · AWS account specifics. Detail: [docs/09 §4](../docs/09-build-order-and-roadmap.md).
