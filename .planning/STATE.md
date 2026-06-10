# STATE — goAML Platform

> **The single source of truth for "where are we / what's next."** Update this file whenever a phase
> starts or completes. It is plain markdown (readable by anyone, no tooling required) and is also what
> the `resume` workflow loads. On a fresh machine: clone the repo, read this file, you're oriented.

---

> **Live dev stack (2026-06-10 frontend-direct test):** goAML `:8090` runs branch `feature/goaml-frontend-direct`
> against **`goaml_e2e`** (5544) with `GOAML_AUTH_MODE=both` + `GOAML_ALLOWED_ORIGINS=http://localhost:3001`
> + dev seed; its SCREENING `trusted_service` is `jit=true, default_role=MLRO` and `tenant_goaml_config.rentity_id=3177`
> (demo tenant `0853bc6d-…`). customer-service `:8081` runs **module-only** `mvn -pl customer-service spring-boot:run`
> (NOT `-am` → that targets the parent, no main class) with `GOAML_INTEGRATION_BASE_URL=http://localhost:8090`
> + `GOAML_INTEGRATION_SOURCE=SCREENING` + the dev key. Frontend `:3001` needs
> `NEXT_PUBLIC_API_GOAML_SERVICE_URL=http://localhost:8090/api/v1` in `.env`. Smoke: `dev-local/goaml-direct-verify.sh`.

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
- **Full-schema fidelity (done, branch `feature/full-schema-fidelity`):** a third real DPMSR sample exposed
  ~13 fields the curated `DpmsrCreateRequest` dropped. The REST report API now binds the full-fidelity
  **`DpmsrReportPayload`** (the xjc-generated leaf types directly, via a scoped enum Jackson module) so a
  caller can supply — and the marshalled XML carries — **every** goAML element; only the server-applied header
  is injected. Curated DTO kept as the internal builder (extended: goods + accounting invoice→registration_no);
  SPA builder posts the payload via a flat→wrapper adapter. Plan: `.planning/plans/full-schema-fidelity.md`.
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
- **2026-06-08 session (later) — Phase 1.5 STARTED.** Plan approved (plan-mode); recorded four design changes
  vs the locked architecture (all in [discussion-log.md](discussion-log.md) + the updated
  [plans/integration-and-auth-architecture.md](plans/integration-and-auth-architecture.md)): **accounting
  integration is REST, not RabbitMQ**; both apps are **embedded API clients** (goAML = single
  system-of-record); accounting supports **both** "client builds the report" and "push raw invoice → goAML
  builds it"; goAML exposes a **reportability-check** endpoint. **Built + merged sub-phase 1.5a (federated
  auth)** to `main` — `goaml.auth.mode`, V3 federated-identity migration, RS256 `ServiceCredentialValidator`,
  `POST /api/v1/auth/federated/token` (+ JIT) — 5 gated steps `a4f1e4a`…`ef04cd9`, full gate green, new auth
  packages added to the JaCoCo gate. ⚠️ Push still gated on the PII-sample history purge.
- **2026-06-09 session — Phase 1.5b (accounting REST) COMPLETE, merged to `main`.** Both integration models +
  the reportability check, on branch `feature/phase-1.5b-accounting` (`d4a50de`…1.5b.6, `--no-ff` to `main`):
  **(1.5b.1-2)** goAML-owned `ReportabilityDetector` (cash ≥ AED 55,000 + precious) + `POST
  /api/v1/reportability/check`; **(1.5b.4)** Model 2 raw-invoice push `POST
  /api/v1/integration/accounting/transactions` (service-assertion authed) → reportability verdict →
  validated DPMSR draft, idempotent on `ACC-<companyId>-<documentNumber>`, status pull — incl. discovering
  goAML's XSD **enumerates `item_type`** so `CommodityMapping` maps accounting `commodityType` → real codes
  (`METAL→GOLD`, diamonds→`DIMND`, jewellery→`JEWEL`, stones/pearl→`GEM`, `WATCH→WATCH`; a watch is DPMS only
  with metal/stone value); **(1.5b.5)** submit gating — `tenant_goaml_config.auto_submit` ON → auto-submit
  (best-effort; an FIU failure falls back to the MLRO gate), OFF (default) → notify tenant MLROs of a
  one-click draft (new `notifyDraftAwaitingReview` seam method); **(1.5b.3)** Model 1 embedded-consumer E2E
  (a federated JWT drives the existing `/api/v1/reports*` API end-to-end; MLRO submit-gating preserved for
  federated users) + the consumer contract doc [docs/14-suite-integration.md](../docs/14-suite-integration.md).
  Full gate green each step; new packages (`ingestion/reportability`, `service/integration`,
  `controller/integration`) added to the JaCoCo gate. ⚠️ Push still gated on the PII-sample history purge.
- **2026-06-09 session (later) — Phase 1.5c (screening) COMPLETE → Phase 1.5 DONE, merged to `main`.** Branch
  `feature/phase-1.5c-screening` (`f0d4ccc`…1.5c.5, `--no-ff`). The screening payload schema came from the
  live `customer-service` OpenAPI (`https://dev-aml-api.vyttah.com/customer-service-0.0.1-SNAPSHOT/v3/api-docs`).
  **(1.5c.1)** `ScreeningPartyPayload` (resolved-codes) + `ScreeningPartyMapper` (customer→Entity/Person,
  directors→entity directors, shareholders/UBOs→parties, PEP/sanctions→party comments). **(1.5c.2)**
  `screened_subject` tenant table (`tenant/V6`) + `DefaultScreeningIngestionService` (resolve SCREENING
  company→tenant, bind context, idempotent upsert on `SCR-<companyId>-<uid>`) + `POST/GET
  /api/v1/integration/screening/subjects` (service-assertion authed). **(1.5c.3)** user-facing
  `/api/v1/screening/subjects` browse + **seed a DPMSR draft** from a subject (`/{ref}/seed-report`) →
  parties flow into a real report's XML. **Fixed a latent engine gap**: DPMSR person parties
  (`t_person_my_client`) require `country_of_birth` (added to `DpmsrCreateRequest.Person` + mapper) and a
  `<phones>` wrapper (now always emitted); a person party further needs a full ID doc + `tax_reg_number`, so
  person-seeded reports are analyst-completed drafts while **entity**-party customers seed fully VALID reports.
  **(1.5c.4)** SPA **Screening** page (browse subjects + seed modal → navigate to the report); frontend gate
  green (tsc/eslint/64 vitest/build). **(1.5c.5)** JaCoCo += `service/screening`/`controller/screening`;
  docs + planning synced. **Phase 1.5 is complete; all standalone + suite-integration work is done.**
- **2026-06-09 session (later still) — real-PII git-history purge COMPLETE (the push blocker is cleared).**
  The real filer's PII (company Example Jewellery LLC [ex–Kanji Bullion], people, Emirates ID, passport,
  email, phone, address, amounts) was spread across **10 files** (the 2 test samples, 2 `assets/` duplicates,
  3 test fixtures that assert on the values, 3 planning `steps/` docs, + an XSD comment) **and one commit
  message** — not just the 2 samples. Anonymized globally with a consistent token map (fixtures + assertions
  replaced in lockstep, so tests stay green) via `git filter-branch` over **all branches** (`--tree-filter`
  for file content + `--msg-filter` for the commit message), then pruned (`refs/original` dropped, reflogs
  expired, `gc --prune=now`). **Verified: 0 PII-bearing objects** remain across every blob/commit/tag; full
  backend gate green on the rewritten tree. ⚠️ **All commit SHAs changed.** A pre-purge backup bundle (with
  PII) is at `dev/goaml-prePII-backup.bundle` — **delete it once satisfied; never push it.** The repo is now
  safe to add a remote + push.
- **2026-06-09 session (later still) — Suite-cockpit track STARTED; Phase A DONE.** After a LexAML competitive
  teardown + reading the real AML stack at `dev/AML` (SpringBoot4 customer/admin/user services + Next.js
  Frontend_Customer cockpit + Python scraper), wrote the grounded **suite-cockpit integration plan**
  ([plans/suite-cockpit-integration.md](plans/suite-cockpit-integration.md)) — AML software is the cockpit
  (owns masters + the new deal module), goAML = report generator + system-of-record. Built **Phase A** on
  `feature/tenant-goaml-person` (`68db70b`/`9e8ec9b`/`0e76ead`, merged `--no-ff`): goAML now stores the
  **reporting person (MLRO) as a tenant default** (`tenant_goaml_person`, one active/tenant) and auto-injects it
  when a report is created without one — so the AML cockpit / CSV / accounting / screening feeds need not send
  it. Admin REST (`/api/v1/admin/goaml-persons`) + SPA panel. **Next: Slice 1** (prove the pipe — RS256 auth
  bridge on the AML side + `trusted_service`/tenant mapping + push one customer's parties → goAML draft), then
  the AML **deal module** (Phase C). Key AML-stack facts: it has **no transaction/deal model** and **does not
  call goAML yet**; auth is **HS256** (goAML needs an **RS256** assertion). Memory: `aml-software-stack`.
- **2026-06-09 session (continued) — Slice 1 (prove the pipe) CODE-COMPLETE across both repos.** goAML
  (`main`): dev-seed suite fixtures (trusted_service + company→tenant + active MLRO, `40f0fdd`) and screening
  `companyId` Integer→String to carry the AML UUID (`f03b199`). **AML software** (`dev/AML/Backend_Java`, its
  own git repo, branch `feature/goaml-integration` — not merged): S1.1 RS256 service-assertion signer
  (`18af3a3`), S1.4a goAML push client + payload contract (`364472e`), S1.4b customer-load +
  `MasterCodeResolver` + `GoamlCustomerPushService` + `POST /api/v1/customers/{id}/push-to-goaml` (`74a0949`).
  **Build resolved with no install/Docker:** the AML repo is Maven/Java-21 — build via the existing
  **Temurin 21** (`~/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/...`) + **IntelliJ's bundled Maven**
  (`/Applications/IntelliJ IDEA CE.app/Contents/plugins/maven/lib/maven3/bin/mvn`), e.g.
  `JAVA_HOME=<temurin21> <mvn> -pl customer-service -am test -Dtest=... -Dsurefire.failIfNoSpecifiedTests=false`.
  Local AML→goAML test helper: `dev-local/push-screening-smoke.sh` (gitignored; dev RSA keypair — public in
  `DevDataSeeder`, private at `dev-local/screening-dev-key.pem`). Stale local demo-tenant schema (pre-`V6`)
  500s the push → recreate the demo tenant (fresh DB) to test. **Next:** Phase C — the AML **deal module**
  (the DPMSR goods/transaction the screening push can't carry), then C.4 maps it → goods on the goAML side.
- **2026-06-09 session (continued) — Phase B (expanded lookups) DONE, merged to `main`.** Resumed the
  step-by-step plan→review→implement→review rhythm. First a plan edit: folded "download the report **inside**
  the AML software" into the suite plan's C.2 (a goAML XML-download proxy) + D.4 (`ebb16e3`) — end-state parity
  goal is the DPMSR is viewable+downloadable in **both** apps. Then **Phase B** on `feature/phase-b-lookups`
  (`--no-ff`): **B.1+B.2** (`908ec3e`) — three `ae` lookup sets derived directly from the 5.0.2 XSD enums
  (codes **and** labels from the per-enum schema comments): `item_types` (63), `item_status` (20),
  `report_indicators` (423); `LookupXsdConsistencyTest` guards them (lookup ⊆ XSD enum). **B.3** (`2bd7ab9`) —
  the lookup API now serves `entries` `[{code,label}]` alongside `codes` (additive; `LookupService.entries()`),
  SPA `CodeSelect` shows "CODE — label" + supports multi-select + forwards the Form.Item `id` (was dropped),
  and the DPMSR builder's item type / status / indicators are now lookup-backed (item type → a real code like
  `GOLD`, not free text). Both gates green each step. **Feeds Phase C.3** (the AML deal form hits the same
  lookup API). **Next: Phase C — the AML deal module** (deal entity + "File to goAML" + Frontend screen), then
  **C.4** (goAML maps deal → goods → a fully VALID DPMSR).
- **2026-06-10 session — Phase C STARTED; C.4a (goAML one-shot filing endpoint) DONE, merged to `main`.**
  Wrote+reviewed the Phase C plan (3 decisions locked: one-shot filing endpoint, AML-side lookup proxy to
  goAML, maker-checker deferred to D) — parallel Explore agents confirmed goAML's report path already accepts
  goods+header, so C.4 is wiring a service-authed entry point, not new report logic. Built **C.4a** on
  `feature/phase-c-filing` (`--no-ff`): `POST /api/v1/integration/screening/filings` (service-assertion authed)
  takes a party bundle (`ScreeningPartyPayload`) + deal goods (`DpmsrCreateRequest.Goods`) + header → builds a
  VALID DPMSR via `reportService.create` in one call; idempotent on `FIL-<companyId>-<filingRef>`; MLRO
  auto-injected (Phase A); `GET /filings/{ref}` status. Commit `500c7ca` + a test-infra fix `40851a4` (pinned
  the test JVM heap to 2g — the full gate was intermittently OOMing on context-load, cascading to unrelated
  tests; not a code defect). Full gate green (1m55s). **Two real DPMSR rules surfaced for the AML deal form:**
  a filing needs **≥1 report indicator** and the tenant needs a positive `rentity_id`. **Next: C.1** — the
  `GoamlTransaction` deal entity in `aml-orm` (Flyway V102) + repo + service, then C.2 (AML endpoints).
- **2026-06-10 session (continued) — Phase C.1 (AML deal entity) DONE.** On the AML repo
  (`dev/AML/Backend_Java`, branch `feature/goaml-integration`, commit `077bef5` — NOT merged): the
  `GoamlTransaction` (DPMSR "deal") entity in `aml-orm` (extends `AuditableEntity`) + `GoamlFilingStatus` enum,
  Flyway **V102** (`goaml_transaction` + `goaml_transaction_indicator` child), `GoamlTransactionRepository`
  (tenant-scoped, soft-delete), request/response DTOs, a hand-written mapper, and `GoamlTransactionService`
  (CRUD; companyId via `CurrentUserService`, uid via `UidGeneratorService`; a filed deal is immutable). Goods +
  indicator values are stored as **goAML codes directly** (no AML master FK) so the C.2 push needs no
  resolution; MLRO + submission date are owned by goAML. 4 unit tests green (mocked repos + real mapper) via
  the Temurin 21 + IntelliJ Maven toolchain. Committed only my files (the user's modified poms/yml left
  untouched). **Next: C.2** — customer-service endpoints: deal CRUD + **"File to goAML"** (assemble parties via
  the Slice-1 `GoamlCustomerPushService` assembly + the deal goods + header → call goAML's C.4a filing
  endpoint) + the **goAML-lookup proxy** + the **report-download proxy**.
- **2026-06-10 session (continued) — Phase C.2 (deal CRUD + "File to goAML") DONE.** AML repo
  (`feature/goaml-integration`, `fa808ce`, NOT merged): `GoamlTransactionController` (deal CRUD at
  `/api/v1/goaml-deals`) + **"File to goAML"** (`GoamlFilingController` `@ConditionalOnProperty` +
  `GoamlFilingService`) — reuses the extracted `GoamlCustomerPushService.assembleSubject` for parties, maps the
  deal → goods + header into `GoamlFilingPayload` (mirrors goAML `ScreeningFilingPayload`), POSTs to goAML's
  C.4a `/filings` (`GoamlScreeningClient.file` → `GoamlFilingResult`), records report id + status on the deal
  (VALID→FILED, else FAILED); `filingRef` = deal uid. 18 goaml tests green (Slice-1 push intact). Committed only
  my files. **Re-sequenced two sub-pieces into coherent feature-pairs:** the goAML-lookup proxy → **C.3**
  (proxy + form + the goAML service-authed lookup endpoint together), the report-download proxy → **C.4b**
  (with its goAML XML-by-ref backend). **Next: C.4b** (goAML) — `GET /filings/{ref}` status + XML-by-ref read +
  the AML download proxy.
- **2026-06-10 session (continued) — Phase C.4b (report XML-by-ref download) DONE, both halves.** goAML
  (`main`, merged `a0ede5f`): `GET /api/v1/integration/screening/filings/{ref}/report.xml` (service-assertion
  authed) → the marshalled goAML XML resolved by `entity_reference` FIL-<companyId>-<ref>, `application/xml` +
  download filename, 404 when no report/XML; E2E (filing test now 6 cases). AML (`feature/goaml-integration`,
  `eae48ae`): `GET /api/v1/goaml-deals/{id}/report.xml` proxy (`GoamlScreeningClient.downloadReportXml` +
  `GoamlFilingService.downloadReportXml`, requires the deal is filed) streams the XML inside the cockpit; 9
  goaml tests green. goAML full gate green (1m58s, 2g heap, no OOM). **Next: C.3** — the goAML service-authed
  **lookup endpoint** + AML **lookup proxy** + the **Frontend_Customer "goAML Filing" screen** (the last C
  step: pick customer → enter deal → File to goAML → download).
- **2026-06-10 session (continued) — Phase C COMPLETE (the LexAML-style deal→file→download pipe).** **C.3a**
  lookup proxy: goAML `IntegrationLookupController` `GET /api/v1/integration/lookups/{jur}/{set}` (service-authed,
  merged `04f42bc`) + AML `GoamlLookupController` `GET /api/v1/goaml/lookups/{set}` (`30c6b9a`) — the cockpit's
  dropdowns come from goAML's authoritative codes (Phase B labels flow through), no drift. **C.3b** the
  **Frontend_Customer "goAML Filing" screen** (its own repo, branch `feature/goaml-integration`, `dfc04f0`):
  route `(main)/goaml-filing` + Sidebar nav; pick legal customer → enter deal (item type/status/indicators from
  goAML lookups, ≥1 indicator required) → **File to goAML** (create→file) → status + validation messages +
  **Download report (XML)**. react-hook-form/yup + the custom Tailwind form components. Gate: tsc + next lint
  clean, **full `next build` green** (route emitted) — needs **Node ≥18.18** (used nvm Node 22; env default
  18.16 too old; build via `export PATH="$HOME/.nvm/versions/node/v22.22.0/bin:$PATH" && npm run build`).
  **Phase C done across all three repos.** Unmerged integration branches: goAML pieces are merged to `main`;
  AML `Backend_Java` + `Frontend_Customer` both on `feature/goaml-integration`. **Next: Phase D** (maker-checker
  on both planes + the "see it all in goAML" read view) and **Phase E** (live FIU B2B submission — external).
- **2026-06-10 session (continued) — Phase D STARTED; D.2a (goAML report review gate) DONE, merged to `main`.**
  Wrote+reviewed the Phase D plan (3 decisions locked: **MLRO approves + submits** — maker≠checker holds at
  create-vs-approve, no new role; **review opt-in everywhere** — `review_required` defaults false for all
  tenants, a TENANT_ADMIN turns it on; **build D.2 first**). Split D.2 into **D.2a** (goAML backend) + **D.2b**
  (SPA review queue). Built D.2a on `feature/phase-d2-report-review` (`--no-ff` merge `1d16989`): per-tenant
  `tenant_goaml_config.review_required` (shared **V5**) + report `reviewed_by/reviewed_at/review_remark`
  (tenant **V7**); `ReportReviewService` (`submitForReview`/`approve`/`reject`/`reviewQueue`, audited) drives
  `VALID → PENDING_REVIEW → APPROVED` (reject → VALID, remark required); the submit gate now requires
  **APPROVED when review is on, VALID when off** (standalone unchanged); REST
  `POST /reports/{id}/submit-for-review|approve|reject` + `GET /reports/review-queue` (submit-for-review =
  ANALYST/MLRO, approve/reject = MLRO-only); invalid transition → 409, no-remark reject → 400.
  `ReportReviewE2ETest` 4 cases (full flow, submit-before-approve 409, reject+remark/RBAC, review-disabled
  guard). Full gate green (2m42s). **Next: D.2b** — the goAML SPA review queue page (list PENDING_REVIEW +
  approve/reject actions), then D.3 (goAML read view), D.1 (AML deal approval), D.4 (AML status+download).
- **2026-06-10 session (continued) — Phase D.2b (goAML SPA review queue) DONE, merged `5e2b5c4`. Phase D.2
  COMPLETE.** goAML `frontend/`: `ReviewQueuePage` (route `reports/review`, new "Review queue" nav item, gated
  MLRO/TENANT_ADMIN) lists PENDING_REVIEW reports — an MLRO approves (→ APPROVED) or rejects (→ VALID, remark
  required via modal); a TENANT_ADMIN sees the queue read-only. `ReportDetailPage` gained **"Submit for review"**
  on a VALID report (ANALYST/MLRO) and now enables submit for VALID **or** APPROVED (backend enforces which);
  `api/reports` + `useReviewQueue` hooks + `ReviewView` type + PENDING_REVIEW/APPROVED status chips.
  `ReviewQueuePage.test` (4: list, approve-clears-queue, reject-requires-remark, TENANT_ADMIN-read-only).
  **Gate caveat (verified):** the full frontend gate is **green on Node 18** (the CI version — lint/tsc clean,
  **72/72 vitest**, `vite build` ok). Under **Node 22** the pre-existing `ImportPage` `userEvent.upload` specs
  fail (jsdom file-upload env artifact) — confirmed by stashing all my changes on pristine `main` (still fails
  on 22, passes on 18); **not** introduced here. **Run the goAML SPA tests on Node 18.** **Next: D.3** — the
  goAML "Transaction & Report" read view (read endpoint + SPA page over stored input + status + XML), then
  D.1 (AML deal approval) + D.4 (AML status+download).
- **2026-06-10 session (continued) — Phase D.3 (goAML "Transaction & Report" read view) DONE — both halves.**
  **D.3a** (goAML `main`, merged `6626375`): `GET /api/v1/reports/{id}/detail` → `ReportDetailView` =
  summary + the stored filing `input` parsed back to a JSON tree + the persisted validation messages + the D.2
  review trail (reviewedBy/reviewedAt/reviewRemark) + `hasXml`. `ReportService.detail`/`ReportDetail`; closes
  the long-noted gap (no endpoint returned a stored report's input or validation). E2E added to
  `ReportApiE2ETest` (6) + `ReportReviewE2ETest` (review trail in /detail after approve). Full backend gate
  green (2m05s). **D.3b** (goAML `frontend/`, merged `0174e91`): `ReportDetailPage` now loads `/detail`
  (`useReportFull`) and adds **Filing details** (new `ReportFilingDetails` — typed DPMSR sections: header /
  reporting person / parties / goods, + collapsible raw-JSON fallback; probes both the full-fidelity
  `DpmsrReportPayload` and the curated shape), **Validation** (messages table — closes the SPA validation-
  re-fetch gap), and **Review** (status/reviewer/remark/when) panels; `getReportDetail` + `ReportDetailView`
  type + detail query-key invalidation on submit/status/review. Tests: `ReportFilingDetails.test` (3) + a
  review/validation case on the detail page (detail-page stubs moved to `/detail`). **Full frontend gate green
  on Node 18: lint/tsc/76 vitest/build** (still run the SPA on Node 18 — Node 22 breaks the unrelated
  ImportPage upload specs). **All goAML-side Phase D is now complete (D.2 + D.3). Next: the AML plane — D.1**
  (deal approval before "File", reusing `CaseManagementDecisionLog`) **+ D.4** (show goAML status + link +
  Download in the AML filing UI).
- **2026-06-10 session (continued) — Phase D.1 (AML goAML-deal approval gate) DONE.** AML `Backend_Java`,
  branch `feature/goaml-integration`, commit `00be99a` (**NOT merged**; only my 11 files committed via explicit
  pathspecs — the user's modified poms/yml/properties left untouched). **Product decision (asked):** the gate
  is a **workflow stage, NOT segregation-of-duties and NOT role-gated** — any authenticated company user can
  create AND approve (the AML backend has no SoD/clear compliance-role model; mirrors goAML's
  MLRO-approves+submits). Built: `GoamlApprovalAction` (SUBMIT/APPROVE/REJECT) + `GoamlFilingApprovalLog`
  entity (aml-orm) + Flyway **V103** `goaml_filing_approval_log` (mirrors `case_management_decision_log`;
  **`goaml_transaction` unchanged** — its `filing_status` enum already had PENDING_APPROVAL/APPROVED);
  `GoamlDealApprovalService` (DRAFT→PENDING_APPROVAL→APPROVED, reject→DRAFT w/ required remark, tenant-scoped,
  append-only audit w/ acting user); 3 endpoints on `/api/v1/goaml-deals/{id}`
  (`submit-for-approval`/`approve`/`reject`); **`GoamlFilingService.file()` now 409s unless APPROVED**;
  `GoamlTransactionService` makes a deal editable/deletable only while DRAFT. Tests green via Temurin21+IntelliJ
  Maven (`mvn -pl customer-service -am test`): `GoamlDealApprovalServiceTest` (8 — incl. tenant isolation +
  blank-remark normalization), `GoamlFilingServiceTest` (9, parametrized gate over non-APPROVED states),
  `GoamlTransactionServiceTest` (4). **Process (ultracode):** 3 background workflows — parallel AML-codebase
  **understand** map → direct implement → **adversarial multi-lens review** (4 lenses × per-finding verify;
  5/9 confirmed applied, 4 correctly dismissed). **Both approval planes now exist. Next: D.4** — the AML
  `Frontend_Customer` filing UI: show goAML/approval status + a link to open the report in goAML + the
  **Download report (XML)** action (C.4b proxy built) — closes Phase D.
- **2026-06-10 session (continued) — Phase D.4 (AML goAML filing console) DONE → PHASE D COMPLETE.** AML
  `Frontend_Customer`, branch `feature/goaml-integration`, commit `5c1f476` (**NOT merged**; only my 2 files —
  `components/GoAMLFilingComponent/GoAMLFilingComponent.tsx` + `utils/react-query/axios/auth.js`). D.1 made the
  backend `file()` require APPROVED, so the screen is no longer create+file in one shot — it now **creates a
  DRAFT, then a deals table drives the gate**: per-status badges (DRAFT/PENDING_APPROVAL/APPROVED/FILED/FAILED)
  + stage actions — **Submit for approval** (DRAFT) → **Approve** / **Reject**+remark (PENDING_APPROVAL) →
  **File to goAML** (APPROVED only) → **Download XML** + optional **Open in goAML** link
  (`NEXT_PUBLIC_GOAML_WEB_URL`, hidden if unset) for FILED/FAILED. 3 new axios helpers (submit/approve/reject).
  **Adversarial-review catch + fix:** success vs error must be keyed off the ApiResponse `type`
  ("SUCCESS"/"ERROR") — both `ApiResponse` and `ApiErrorResponse` carry a numeric `statusCode`, so an earlier
  `statusCode`-based `isSuccess` would have shown a success toast on a 409; fixed to `type==='SUCCESS'`. Gate
  (Node 22): `tsc --noEmit` clean, `next lint` no errors, `next build` ✓ (`/goaml-filing` 6.05 kB emitted).
  Reviewed across api-shape / workflow-gate / react-ux lenses against the confirmed response envelopes (the
  review-workflow had a script-syntax hiccup, so the lens sweep was done directly — no further defects).
  **PHASE D COMPLETE (D.1+D.2+D.3+D.4). The full suite loop is built end-to-end: AML deal → approve (AML) →
  File → goAML builds the DPMSR → MLRO review/approve (goAML) → (with FIU creds) submit; report viewable +
  downloadable in BOTH apps. Remaining: only Phase E** (live FIU B2B proof — external, real RE + SACM).
- **2026-06-10 session (continued) — PIVOT: goAML as a frontend-direct microservice for the AML cockpit.**
  After Phase D closed (AML-backend-proxy loop), the user re-scoped: the **AML frontend should call goAML
  directly** — two menus (**Create Transaction**, **Approve Transaction**) + **Download XML**; the frontend
  **fetches KYC from the AML services and assembles the whole DPMSR payload itself**; **all transaction/DPMSR
  data stays in goAML** (no AML persistence); screening + other AML features untouched. This **supersedes** the
  AML-side deal module + proxies from Phase C/D — which live on the **unmerged** `feature/goaml-integration`
  branch, so AML mainline is undisturbed (the old module stays dormant). Ran a **6-reader verification fan-out**
  across both repos → confirmed the **goAML side needs near-zero change** (create / review-approve-reject /
  submit / detail / xml / status / lookups / reportability all exist). **Auth crux (verified):**
  `/auth/federated/token` needs a **private-key-signed RS256 assertion → not browser-mintable**; but
  `/auth/login` is browser-callable + CORS is configurable, so a browser CAN hold a goAML JWT and call
  `/api/v1/reports*` directly — the open decision is **how it gets the token** (Option 1 = federated SSO via a
  thin AML-backend mint **[recommended]**; Option 2 = native goAML login). Wrote the full plan
  [plans/goaml-as-aml-microservice.md](plans/goaml-as-aml-microservice.md) + logged the decision in
  [discussion-log.md](discussion-log.md). **✅ Auth model chosen: Option 1 (Federated SSO via a thin
  AML-backend token-mint).** Building **G1→A4**: G1 (goAML `GOAML_AUTH_MODE=both` + CORS for the AML origin +
  approver→MLRO role-map + optional curated `POST /api/v1/reports/dpmsr`) → A1 (AML `GoamlTokenController` +
  frontend `axiosInstanceGoaml`/goAML-JWT interceptor) → A2 (Create Transaction page) → A3 (Approve Transaction
  page) → A4 (lookups direct + docs).
- **2026-06-10 session (continued) — frontend-direct G1 + A1 DONE (auth + contract foundation built across
  both repos).** Verified the whole contract surface (6-reader fan-out), then built + committed:
  **goAML `feature/goaml-frontend-direct`** — **G1.1** (`a69a184`) curated `POST /api/v1/reports/dpmsr`
  (`DpmsrCreateRequest` → existing `ReportService.create`, so the cockpit assembles a clean payload not the xjc
  tree; E2E + full gate green); **G1.3** (`f76998d`) per-trusted-service `default_role` (shared **V6**; nullable
  → ANALYST fallback) honoured by the federated exchange, dev SCREENING service now JIT-provisions cockpit users
  as **MLRO** (so create AND approve/submit work with no per-user seeding). **AML `Backend_Java`
  `feature/goaml-integration`** — **A1a** (`7a399e0`) `GoamlTokenController` `POST /api/v1/goaml/token` (mint
  assertion → goAML `/auth/federated/token` → goAML JWT; reuses the RS256 signer; tested). **AML
  `Frontend_Customer` `feature/goaml-integration`** — **A1b** (`0f57680`) `axiosInstanceGoaml` (bootstraps/caches
  the goAML JWT, attaches only the goAML Bearer, own 401-refresh that doesn't log the user out) + `auth.js` goAML
  getters + `.env.example`. **Auth = Federated SSO** (decided): browser bootstraps its goAML JWT via the one
  backend mint, then calls every transaction op on goAML directly. **goAML gate green** (one unrelated WireMock
  socket flake, confirmed passing on isolated re-run). **Next: A2** (Create Transaction page — pick customer →
  KYC prefill → goods/indicators/header + gap fields → assemble `DpmsrCreateRequest` → create) **+ A3** (Approve
  Transaction page — list → detail → submit-for-review/approve/reject → submit → Download XML); the frontend
  `next build` is verified with A2 (first consumer of A1b). **Live E2E needs:** customer-service rebuilt/
  restarted with A1a + `GOAML_AUTH_MODE=both` + `GOAML_ALLOWED_ORIGINS=http://localhost:3001` on goAML +
  the AML companyId mapped to the demo tenant. Plan: [plans/goaml-as-aml-microservice.md](plans/goaml-as-aml-microservice.md).
- **2026-06-10 (continued) — goAML half VERIFIED LIVE.** Self-contained smoke (`dev-local/goaml-direct-verify.sh`,
  gitignored) on an isolated goAML `:8099` over a throwaway `goaml_smoke` DB (dev key mints the SCREENING
  assertion — no AML login), all green: federated exchange → JWT with **MLRO** (G1.3) → direct `GET /reports`
  200 → lookups → curated `POST /reports/dpmsr` **VALID** (G1.1) → CORS from `:3001` echoed (G1.2). Isolated
  instance + DB torn down; running stack (8090/8081/8080) untouched. **Finding (config prereq, not a defect):**
  the dev seeder creates no `tenant_goaml_config` → fresh tenant `rentity_id=0` → INVALID until a config row
  with a positive `rentity_id` is seeded. customer-service `/goaml/token` leg is unit-tested (live leg needs
  the AML password). **A2 plan detailed in the plan doc §6; presented for approval before building.**
- **2026-06-10 (continued) — A2 (Create Transaction page) DONE.** AML `Frontend_Customer`
  (`feature/goaml-integration`, commit `ca45180`, NOT merged; only my files — new
  `components/CreateTransactionComponent/` + `(main)/create-transaction/page.tsx` + Sidebar nav). One-page
  DPMSR builder calling goAML **directly**: pick customer (legal|natural) → prefill party from AML KYC
  (master-id→goAML-code mapped; legal incl. best-effort directors) → complete the gap fields goAML needs
  (incorporation state / commercial name / residence / TRN / full ID doc) → goods rows (item type/status/
  currency from goAML lookups) → indicators (≥1) + reason/action → optional reportability check → assemble
  the curated `DpmsrCreateRequest` → `goamlCreateReport` (`POST /api/v1/reports/dpmsr` via the A1b
  `axiosInstanceGoaml`) → reportId + VALID/INVALID + messages. No AML persistence; MLRO auto-injected by
  goAML. **Gate green (Node 22): tsc + lint clean, `next build` OK, `/create-transaction` 9.06 kB emitted —
  first real consumer of the A1b auth bridge, so it verifies the bridge compiles + wires.** Field-mapping
  fidelity (nationality/occupation code-sets) to confirm in a live cockpit pass. **Next: A3** — Approve
  Transaction page (list goAML reports → detail → submit-for-review/approve/reject → submit to FIU → Download
  XML), then A4 (lookups-direct cleanup + docs).
- **2026-06-10 (continued) — A3 (Approve Transaction page) DONE.** AML `Frontend_Customer`
  (`feature/goaml-integration`, commit pending; only my files — `components/ApproveTransactionComponent/` +
  `(main)/approve-transaction/page.tsx` + Sidebar nav). Lists goAML reports (`goamlListReports`) with status
  badges; per-status row actions drive the workflow **directly** against goAML: VALID → Submit-for-review /
  Submit-to-FIU; PENDING_REVIEW → Approve / Reject+remark (inline); APPROVED → Submit-to-FIU; SUBMITTED/
  ACCEPTED/REJECTED → Refresh status; any → Download XML (Blob) + inline **Details** (`/detail`: header +
  review trail + parties + goods + validation + raw-filing fallback). approve/reject/submit MLRO-gated
  server-side; goAML error bodies surfaced. Gate green: tsc + lint clean (0 warnings), next build OK (/approve-transaction 3.43 kB). Commit `820dc08`. **Next: A4** —
  lookups-direct cleanup + docs; then the full suite loop is frontend-direct.
- **2026-06-10 (continued) — A4 DONE → goAML-as-microservice PIVOT COMPLETE.** A4 was docs/closeout (no new
  code): lookups were already direct in A2/A3, and the dormant Phase C/D deal module is left intact (both nav
  items coexist — "goAML Filing" = old proxy flow, "Create/Approve Transaction" = new direct flow). Added the
  consolidated **run/test guide (plan §10)**, a pointer from `docs/14-suite-integration.md`, and closed the
  pivot here + in `discussion-log.md`. **End state:** the AML cockpit creates a DPMSR directly in goAML and
  drives review → approve → submit → download, all browser → goAML (auth = one backend mint, A1). goAML half
  verified live (§8a); A2/A3 gate-green (tsc/lint/next build). **Branches (unmerged):** goAML
  `feature/goaml-frontend-direct` (G1.1 `a69a184`, G1.3 `f76998d` + planning); AML `Backend_Java`
  `feature/goaml-integration` (A1a `7a399e0`); AML `Frontend_Customer` `feature/goaml-integration` (A1b
  `0f57680`, A2 `ca45180`, A3 `820dc08`). **Remaining:** the user's live cockpit pass (plan §10) + Phase E
  (real FIU creds, external). Plan: [plans/goaml-as-aml-microservice.md](plans/goaml-as-aml-microservice.md).
- **2026-06-11 — Rich Transaction Builder T1→T2 + T2.1 (LiveExShield parity) COMPLETE & VERIFIED LIVE.** The
  cockpit Create-Transaction page now posts the full-fidelity `DpmsrReportPayload` with **every** LiveExShield
  field, all KYC-prefilled, with correct **mandatory markers**. Key fixes this session: subject filed as the
  **lenient `t_person`/`t_entity`** (matching the real FIU DPMSR samples — not `t_person_my_client`, whose
  required 1-char `tax_reg_number` could XSD-break the XML), `country_of_birth` no longer dropped, "Indicators"
  relabelled "Reason for reporting (FIU indicator)" (it is XSD-mandatory ≥1). Added natural alias/dual-nationality/
  source-of-wealth/email/address + legal business-activity/incorporation-date/TRN/email/address; PEP + source-of-
  funds kept as metadata; `legal_form_type` left unset (enum). Live: natural `<person>` + legal `<entity>` full
  payloads build **VALID, zero messages**, all elements round-trip into the XML. **No goAML code change** (full
  path already supports it). Frontend commit `0348255`. Plan: [plans/rich-transaction-builder.md](plans/rich-transaction-builder.md).
  **Remaining:** user's combined live cockpit pass + Phase E (real FIU creds, external).
- **To resume on any machine:** clone → read this file → `docker compose up -d postgres` →
  `./gradlew test` (confirm green) → for the UI, `GOAML_DEV_SEED=true ./gradlew bootRun` +
  `cd frontend && npm install && npm run dev`. **No open build phase** — standalone (14/14) + Phase 1.5
  (suite integration + federated auth) are all done, and the PII-history purge is done. Remaining are
  go-live externals (per-tenant FIU creds, real lookups/BRRs) — see Blockers. **To integrate accounting +
  AML screening, follow [plans/go-live-integration-runbook.md](plans/go-live-integration-runbook.md)**
  (deploy → register trust/tenant-mappings → wire each sibling app → FIU submit config → contract review).
