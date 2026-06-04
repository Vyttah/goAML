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

- **Phases 1–7 complete**, plus the **XSD-first foundation** (domain xjc-generated from goAML 5.0.2 + XSD
  gate + DPMSR builder) and the **Vyttah layer-first refactor**.
- **Active focus: Phase 8 next** — **S3 attachments** (presigned upload, pull into the ZIP), tested vs
  LocalStack; adds the `attachment` tenant table + the `S3StorageClient` deferred from Phase 6.
- **Last completed:** **Phase 7 (persistence + service + web REST)** — the **DPMSR report lifecycle API**:
  `report`/`submission` tenant tables + `TenantGoamlConfig`; `ReportService` (create→build+validate→persist)
  + `SubmissionService` (package→B2B submit→reportkey, on-demand status); `ReportController`
  (`/api/v1/reports` create/list/get/submit/status, **MLRO-gated submit**). Testcontainers + WireMock E2E;
  JaCoCo gate (~98.5% on the gated packages). Commits `154a2f5`…`82af99f`. Per-step docs: `steps/PHASE-7.1..7.4`.
  **The report flow is now manually testable via the API** (see [docs/03 — Trying the report API locally](../docs/03-tech-stack-and-local-dev.md)).
- **Branch:** `xsd-first/step-1-validation-gate` (work has continued here through the migration + Phases 6–7).
- **Build/tests:** ✅ green — `docker compose up -d postgres localstack redis` then
  `./gradlew test jacocoTestCoverageVerification` → `BUILD SUCCESSFUL`.

## Next Action — Phase 8 (S3 attachments)

Add supporting-document attachments to a report and into the submission ZIP. Expected scope:
1. **`integration/aws/S3StorageClient`** (the client deferred from Phase 6) — per-tenant prefixes; tested
   vs LocalStack S3.
2. **`attachment` tenant table** + entity/repo; presigned upload URL, then pull the bytes into
   `ReportZipPackager` at submit (within `PackagingLimits.UAE_DEFAULT`: 5 MB/file, 20 MB/ZIP).
3. **Web:** attach/list/remove endpoints on a report; submit includes the attachments.

> Follow the gated workflow: write the Phase 8 plan + per-step understanding docs, get approval, then build
> step-by-step (one green commit per step, JaCoCo gate extended).

**Recently completed (history in `steps/` + `discussion-log.md`):** XSD-first foundation (STEP-1..7 +
STEP-R); **Phase 6** (PHASE-6.1..6.5) → Secrets Manager, Redis token cache, goAML B2B client; **Phase 7**
(PHASE-7.1..7.4) → report/submission persistence, report + submission services, REST API + RBAC + E2E.

## Progress

`[█████░░░░░] 7/14 (≈50%)` + XSD-first foundation + layer-first refactor

| Done | Phase |
|------|-------|
| ✅ | 1 Skeleton · 2 Multi-tenancy+security · 3 domain/ · 4 engine builders+marshaller · 5 engine validation+jurisdiction+lookups · 6 integration/aws/ + b2b/ client · **7 persistence + service + web REST** |
| ⏭️ | **8 S3 attachments** (LocalStack) |
| ⬜ | 9 scheduler · 10 notifications · 11 ingestion · 12 mcp+cli · 13 frontend · 14 infra |

(Full table + Phase 6 recap in [ROADMAP.md](ROADMAP.md) and
[docs/09-build-order-and-roadmap.md](../docs/09-build-order-and-roadmap.md).)

---

## Recent Decisions

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
- **To resume on any machine:** clone → read this file → `docker compose up -d postgres` →
  `./gradlew test` (confirm green) → continue (XSD-first foundation, then Phase 6).
