# STATE — goAML Platform

> **The single source of truth for "where are we / what's next."** Update this file whenever a phase
> starts or completes. It is plain markdown (readable by anyone, no tooling required) and is also what
> the `resume` workflow loads. On a fresh machine: clone the repo, read this file, you're oriented.

---

## Project Reference

**What:** Multi-tenant RegTech platform that builds, validates, submits, and tracks goAML AML reports to
the UAE FIU (goAML Web B2B REST), filing on behalf of many client Reporting Entities.
**Why / full context:** [docs/01-business-context.md](../docs/01-business-context.md)
**Implementation plan (intent + 14-phase build order):** [docs/00-implementation-plan.md](../docs/00-implementation-plan.md)
**Roadmap (phase status):** [ROADMAP.md](ROADMAP.md) · **Project facts/decisions:** [PROJECT.md](PROJECT.md)
**Full developer docs:** [docs/README.md](../docs/README.md)

---

## Current Position

- **Phases 1–14 ALL complete** (the standalone product is fully built — engine, REST API, SPA, infra, and the
  Phase-12 plugin/MCP/CLI), plus the **XSD-first foundation** (domain xjc-generated from goAML 5.0.2 + XSD gate
  + DPMSR builder) and the **Vyttah layer-first refactor**. **Phase 1.5** (suite integration + federated auth)
  remains **deferred — decide later** (a separate track; see Recent Decisions).
- **Last completed:** **Phase 12 (plugin / MCP / CLI) — the final phase.** A Spring AI MCP server (SSE at
  `/api/v1/mcp/**`) inside the app, a distributable Claude plugin (skill + commands + hook + marketplace), and
  a `--cli` run-mode of the same jar — all three delegating to the same engine/services (REST/MCP/CLI parity),
  tenant-scoped + role-gated, with an **MLRO-gated, dry-run-first, confirm-required** submission harness.
  Commits `94a0dce`…(12.7); merged to `main`. Per-step docs: `steps/PHASE-12.1..12.7`.
- **Previously:** **Phase 14 (infra)** — deployable packaging: a finalized 3-stage **Dockerfile**
  (node SPA build → layered `bootJar` with the SPA on the classpath → non-root JRE; verified by a real
  `docker build` + run — SPA served, `/actuator/prometheus` 200, liveness/readiness UP, non-root), a full
  **Helm chart** (`helm/goaml/`: Deployment w/ health-group probes + hardened security context, Service,
  Ingress+TLS, HPA, ConfigMap, ServiceAccount+IRSA, 3 secret strategies; `helm lint` clean), an
  **observability baseline** (`micrometer-registry-prometheus` → `/actuator/prometheus`, `prod`-profile JSON
  logs, a `CorrelationIdFilter`), and **GitHub Actions** (`ci.yml` backend+frontend gates; `cd.yml` image
  build + secret-gated ECR/EKS deploy via OIDC). Commits `e24bce4`…(14.5). Per-step docs:
  `steps/PHASE-14.1..14.5`.
- **Previously:** **Phase 13 (`frontend/`)** — the React + TypeScript + Vite + Ant Design SPA over the
  REST API: auth (JWT-claims identity, 401→login) → dashboard → **full DPMSR builder** (Zod mirror, lookup
  dropdowns, inline server validation) → report detail (MLRO submit + FIU status + attachments) → file
  import (XML/CSV + per-row results) → notifications (bell + center) → reference browser → admin (tenant /
  user / goAML-config). Plus the **REST enablers** it needed: `controller/lookup/`, `controller/admin/` +
  `service/admin/`, env-gated CORS + SPA-serving. A gated **dev seeder** (`config/dev/DevDataSeeder`,
  `goaml.dev.seed.enabled`) seeds a demo tenant + logins for local review. **58 Vitest specs**;
  `tsc`+ESLint+`vite build` gated per step; backend JaCoCo gate held on the new controllers/services.
  Commits `8e76a40`…(13.11). Per-step docs: `steps/PHASE-13.1..13.11`.
- **Previously:** **Phase 11 (`ingestion/`)** — file import as a persisted `import_job` with row-level
  results: **goAML XML** (`GoamlXmlImporter` reuses unmarshal + XSD/rules validators → persisted,
  re-submittable) + a flat **DPMSR CSV** (`CsvImporter` maps each row → `DpmsrCreateRequest` → the existing
  `ReportService.create`, no parallel persist). Synchronous; per-row isolation (a bad row → `FAILED`
  result, the batch continues); whole-file rejection (unreadable / missing required headers / over
  `goaml.ingestion.max-rows`) → 400 with no job. `POST/GET /api/v1/imports`. Also **fixed a latent audit
  bug**: `DefaultAuditService` cleared `TenantContext` in `finally`, clobbering a request that audits
  mid-stream then keeps doing tenant work (the CSV path) — now it **restores** the prior tenant. The E2E
  uses **MockMvc** (in-process) which made a `TestRestTemplate` socket flake deterministic and exposed that
  bug. Commits `dd9b54a`…(11.4). Per-step docs: `steps/PHASE-11.1..11.4`.
- **Previously:** **Phase 10 (`notification/`)** — report transitions now reach users. A per-tenant
  `notification` in-app store + an `integration/aws/SesClient` (SES email, gated off by default) are fired
  from the **`SubmissionService` seam** (`submit()` + `refreshStatus()`, so the poller, the on-demand
  `GET …/status`, and submit-time rejections all flow through one place), notifying the report **author +
  the tenant's active MLROs**. Best-effort + isolated (`safeNotify` try/catch, after the saves — a
  notification/email failure never rolls back a status change or aborts a poll). `GET/POST
  /api/v1/notifications` for the in-app list/read. Testcontainers ITs prove the in-app fan-out + the
  email-enabled gate. Commits `99e3c75`…(10.4). Per-step docs: `steps/PHASE-10.1..10.4`.
- **Previously:** **Phase 9 (`scheduler/`)** — proactive submission-status tracking:
  `SubmissionStatusPoller` (`@Scheduled`) enumerates ACTIVE tenants, finds each tenant's `SUBMITTED`
  reports, and refreshes FIU status via the existing `SubmissionService.refreshStatus` (→ ACCEPTED/REJECTED)
  — wrapped in a bounded transient `RetryService` (`B2bTransport`/`B2bAuth` only). **Poll-only** (no
  auto-resubmit — re-submit stays manual MLRO), **plain `@Scheduled`** (no distributed lock; idempotent
  GETs). Per-tenant/per-report failures logged + skipped; the scheduled method never throws (would suppress
  future runs); `TenantContext` cleared per tenant. Testcontainers IT (two tenants transition + isolation).
  Commits `015ea61`…`9530bf3` (+ 9.4). Per-step docs: `steps/PHASE-9.1..9.4`.
- **Branch:** `phase-14/infra` (off `main`; Phases 6–11 + 13 + XSD-first are on `main`). Merge to `main` on
  Phase 14 close.
- **Build/tests:** ✅ green — backend `./gradlew test jacocoTestCoverageVerification` → `BUILD SUCCESSFUL`;
  frontend `cd frontend && npm test` (58) / `npm run typecheck` / `npm run lint` / `npm run build`;
  `docker build -t goaml:dev .` builds + boots; `helm lint helm/goaml` clean.
- **Local review:** `GOAML_DEV_SEED=true ./gradlew bootRun` (seeds a demo tenant + logins, all password
  `Passw0rd!`: `mlro@demo.local`, `analyst@demo.local`, `admin@demo.local`, `superadmin@goaml.local`) +
  `cd frontend && npm run dev` → http://localhost:5173 (proxies `/api` → :8080). Container: `docker build -t
  goaml:dev .` then run with `SPRING_DATASOURCE_*` + `GOAML_JWT_SECRET` env (serves API + SPA on :8080).

## Next Action — the standalone build is COMPLETE

All 14 roadmap phases are done. There is no next *build* phase for the standalone product. Open options
(decide later):
1. **Phase 1.5** — Vyttah-suite integration + federated auth (RabbitMQ accounting consumer → reportability →
   auto-create DPMSR draft → MLRO 1-click; screening REST push; `/auth/federated/token` + `external_identity`).
   Deferred; design in [plans/integration-and-auth-architecture.md](plans/integration-and-auth-architecture.md).
2. **Go-live prerequisites (external, gate live correctness not the build):** an AWS account/EKS/ECR/RDS; a
   GitHub remote + CD secrets; **the PII-sample history purge BEFORE any first push to a remote** (real-PII
   sample XMLs are in git history); per-tenant FIU B2B URLs + credentials; real UAE lookup exports + BRRs.
3. **Smaller follow-ups surfaced during Phase 12:** a lookups-refresh tool (needs a backend FIU-lookup sync —
   new work); STR/other report-type build tools (engine builds DPMSR today).

> Phase-14 carried-forward infra touch-up is **DONE** (12.7): MCP route + SSE guidance on the Helm ingress and
> the `--cli` note on the Dockerfile.

**Recently completed (history in `steps/` + `discussion-log.md`):** XSD-first foundation (STEP-1..7 +
STEP-R); **Phase 6** (PHASE-6.1..6.5) → Secrets Manager, Redis token cache, goAML B2B client; **Phase 7**
(PHASE-7.1..7.4) → report/submission persistence, services, REST API + RBAC + E2E; **Phase 8**
(PHASE-8.1..8.5) → S3 client, `attachment` table, attachment service + submit wiring, multipart REST;
**Phase 9** (PHASE-9.1..9.4) → scheduler queries + config, RetryService, SubmissionStatusPoller, IT;
**Phase 10** (PHASE-10.1..10.4) → notification store + config, SesClient, NotificationService, seam+REST+IT;
**Phase 11** (PHASE-11.1..11.4) → import_job store, GoamlXmlImporter, CsvImporter, ingestion service+REST+E2E;
**Phase 13** (PHASE-13.1..13.11) → lookup+admin REST enablers, then the React SPA (auth→dashboard→builder→
detail→import→notifications→reference→admin) + dev seeder; **Phase 14** (PHASE-14.1..14.5) → observability
(Prometheus/JSON logs/correlation id), finalized Dockerfile, Helm chart, GitHub Actions CI + gated CD.

## Progress

`[██████████████] 14/14 (100%)` + XSD-first foundation + layer-first refactor

| Done | Phase |
|------|-------|
| ✅ | 1 Skeleton · 2 Multi-tenancy+security · 3 domain/ · 4 engine builders+marshaller · 5 engine validation+jurisdiction+lookups · 6 integration/aws/ + b2b/ client · 7 persistence + service + web REST · 8 S3 attachments · 9 scheduler · 10 notifications · 11 ingestion · **12 plugin/MCP/CLI** · 13 frontend · 14 infra |
| ⬜ | 1.5 suite-integration (deferred — decide later) |

(Full table + Phase 6 recap in [ROADMAP.md](ROADMAP.md) and
[docs/09-build-order-and-roadmap.md](../docs/09-build-order-and-roadmap.md).)

---

## Recent Decisions

- **Remaining build order — 13 → 14 → 12; 12 deferred to last; 1.5 deferred (2026-06-06):** after research
  (nothing depends on Phase 12 — the frontend consumes the **REST API**, infra packages the jar regardless,
  and Phase 12 only needs the now-complete Phases 6–11), the **Claude plugin & MCP harness + `cli/`** is
  moved to **last**. Order: **Phase 13 (frontend) → Phase 14 (infra) → Phase 12 (plugin/MCP/CLI)**.
  **Caveat:** infra (14) lands before 12, so expect a minor infra touch-up when 12 ships (MCP HTTP route in
  Helm/ingress + `--cli` run-mode). **Phase 1.5** (Vyttah-suite integration + federated auth) is a separate
  track, **deferred — decide later**. Full Q&A in [discussion-log.md](discussion-log.md).
- **Suite positioning + unified auth + Phase 1.5 (2026-06-04):** goAML is sold **standalone AND** runs in
  Vyttah's suite (accounting/ERP + AML screening) as its own microservice. **Unified auth:** goAML keeps
  its own JWT as identity authority; accounting/screening exchange their authenticated user for a goAML
  token via `POST /api/v1/auth/federated/token` (service trust + stored `external_identity`);
  `goaml.auth.mode = native|federated|both`. **Integration (Phase 1.5):** accounting→goAML via RabbitMQ
  (txn → reportability detection in goAML → auto-create DPMSR draft → **MLRO 1-click**, full-auto opt-in);
  screening→goAML via REST/form. **FIU B2B creds stay separate from login.** Design:
  [plans/integration-and-auth-architecture.md](plans/integration-and-auth-architecture.md). Refined via
  Ultraplan; it confirmed all code refs and flagged that `.planning/`+`docs/` were **uncommitted** (hence
  invisible in a fresh clone) → **commit them.**
- **Report-type scope — phased (2026-06-03):** Phase 1 target = **precious-metals dealers → `DPMSR`**
  (+ `STR`/`SAR` baseline + `PNMRA`/`CNMRA` sanctions near-term); **next phase = all 17** schema codes.
  Full reasoning + the whole session's Q&A is in **[discussion-log.md](discussion-log.md)**.
- **XSD-first foundation (2026-06-03):** build the domain + structural validation over the
  **authoritative latest goAML XSD** (target **5.0.x, XSD 1.1**) using **xjc-generated JAXB** + an
  XSD-1.1 validation gate — replacing the hand-modeled v4.0 POJOs. This **reworks Phases 3–5** and
  expands report-type coverage (UAE adds REAR/PNMR/FFR). **Blocking dep:** export the XSD from the UAE
  FIU portal into `src/main/resources/xsd/` (it's login-gated — not fetchable from the open web). Full
  migration plan: [plans/xsd-first-foundation.md](plans/xsd-first-foundation.md).
- Project state is now maintained **in-repo** (`.planning/` + `docs/`) so anyone on any machine can
  resume. The implementation plan was copied from `~/.claude/plans/` into
  [docs/00-implementation-plan.md](../docs/00-implementation-plan.md).
- The `aml-ai-skills` Claude skill describes a **different** project (a Python AML Co-Pilot chatbot),
  **not** this Java repo. Do not use its build order here.
- Key locked architecture decisions live in [PROJECT.md](PROJECT.md) → Key Decisions.

## Pending Todos

- **Phase 12 plan added** — [plans/phase-12-plugin-and-mcp-harness.md](plans/phase-12-plugin-and-mcp-harness.md):
  full goAML Claude plugin + MCP harness so users connect Claude and drive all features safely. Has **4
  open decisions to confirm** (auth model, submission autonomy, plugin target, transport). Steps 12.1–12.3
  (read/build/validate/preview tools + plugin skill/commands) are buildable now on the existing `engine/`;
  submit/track/import tools need Phases 6/7/9/11.

## Blockers / Concerns

- **✅ XSD acquisition — RESOLVED:** the authoritative goAML **5.0.2** XSD + 2 real DPMSR samples are
  vendored (`src/main/resources/xsd/goaml/5.0.2/`, `src/test/resources/samples/`). XSD-first codegen is
  **unblocked**. **Still pending via [field-acquisition-checklist.md](field-acquisition-checklist.md)**
  (needs UAT access; does NOT block codegen): per-tenant B2B URLs + creds, full UAE lookup exports, the
  BRRs doc, `rentity_id`. Confirm the UAE *production* goAML version (4.x vs 5.x) before go-live.
- **SACM registration is gated to a real regulated entity** — a solo developer cannot self-register
  (needs a supervisory body + registration number + authorization PDF). This gates **live UAT/B2B access**
  (creds, submission), **not** the XSD-first codegen (the XSD is already in hand). Live access needs a real
  client-RE relationship. (See the "Reality check" in the checklist.)
- **External inputs gate live correctness** (not the build): UAE XSD, per-tenant B2B URLs + credentials,
  UAE Business Rejection Rules, real lookup exports, CSV template, AWS account specifics. See
  [docs/09 §4 Open Items](../docs/09-build-order-and-roadmap.md).
- **Known gaps** between plan and current code (no Emirates-ID validation yet, no XSD gate, ANALYST/MLRO
  not yet enforced, etc.) — see [docs/09 §5](../docs/09-build-order-and-roadmap.md).

---

## Session Continuity

- **Last session:** 2026-06-03 — Resumed project; wrote full in-repo developer docs (`docs/`, 12 files)
  verified against the codebase; made the repo self-contained for resumption (copied the plan in,
  created this `.planning/` state); confirmed test suite green; added the **Phase 12 plugin + MCP harness
  plan** (`.planning/plans/`).
- **Stopped at:** Phases 1–5 complete; docs + state + Phase 12 plan landed. Ready to begin Phase 6 (or
  start the buildable-now slice of Phase 12 — steps 12.1–12.3 — if the plugin is the priority).
- **2026-06-04 session:** decided suite positioning + unified auth + Phase 1.5 (above); refined the plan
  via Ultraplan; recorded the design in [plans/integration-and-auth-architecture.md](plans/integration-and-auth-architecture.md);
  added a durable root `CLAUDE.md`. The full KB + docs + vendored XSDs were committed in `d8637c1` ("p1",
  preserved snapshot); a follow-up commit merged Ultraplan's refinements into the auth plan and removed the
  redundant `goamlplanningkb/` duplicate. ⚠️ Real-PII sample XMLs remain committed (local-only, unpushed) —
  **anonymize before any push/share.**
- **2026-06-06/07 session:** built **Phase 13 (React frontend)** (steps 13.1–13.11, 58 Vitest specs, gated
  dev seeder; merged to `main` `9a4c691`) **and Phase 14 (infra)** on branch `phase-14/infra` (steps
  14.1–14.5): observability (Prometheus/JSON logs/correlation id), finalized SPA-bundled non-root Dockerfile
  (verified via real `docker build` + run), full Helm chart (`helm lint` clean), GitHub Actions CI + gated
  CD. Decisions: DPMSR builder = full nested form (13); SPA via Dockerfile node stage + gated CD + full Helm
  (14). **Next: merge `phase-14/infra` → `main`, then Phase 12 (plugin/MCP/CLI) — the last phase.**
- **2026-06-07 session (Phase 12 — final phase):** built the **plugin/MCP/CLI** in 7 gated steps on branch
  `phase-12/plugin-mcp` (steps 12.1–12.7): MCP server scaffold + auth/tenant/RBAC (incl. solving base-url + the
  Reactor-thread context-propagation); read/build/validate/preview tools; the Claude plugin (skill + commands);
  the submit/status/messages **safety harness** + pre-submit hook; import + admin tools (lookups-refresh
  deferred); the **CLI** parity layer (+ dual-mode `@ConditionalOnWebApplication` fix); packaging/marketplace +
  docs + the Helm/Dockerfile infra touch-up. All gated green; merged to `main`. **The standalone product is
  complete (14/14 phases).**
- **2026-06-08 session (pre–Phase-1.5 verification + hardening):** full test + live end-to-end verification
  of the standalone product (every REST endpoint + MCP, real Postgres/LocalStack/Redis, generated valid goAML
  5.0.2 XML; live FIU submit not possible w/o creds — transport failure handled as 502). Scan: no hardcoded
  prod data / 0 TODOs. Found + fixed 5 surface/robustness gaps on branch `hardening/post-verification`
  (5 atomic commits, full gate held, merged to `main`): (1) report XML view+download endpoint+UI; (2)
  attachment download endpoint+UI; (3) AWS integration failures → 502 (not opaque 500); (4) validate
  `authMode`/`jurisdictionCode` at config-write; (5) require `indicators`+`party_reason` in the DPMSR CSV.
  No engine/validation/security logic changed. **+ during the SPA review:** fixed SUPER_ADMIN landing
  (was hitting the tenant Reports dashboard → "Access Denied" + a notification-bell 500) — role-aware landing
  to `/admin`, tenant-scoped nav/bell hidden, notifications API → clean 403 for a tenantless caller
  (`fix/superadmin-landing` → `main`). See [discussion-log.md](discussion-log.md) (Session 2026-06-08).
  **Next: Phase 1.5 (deferred until go-ahead).**
- **To resume on any machine:** clone → read this file → `docker compose up -d postgres` →
  `./gradlew test` (confirm green) → for the UI, `GOAML_DEV_SEED=true ./gradlew bootRun` +
  `cd frontend && npm install && npm run dev` → continue with **Phase 12** (confirm its 4 open decisions).
