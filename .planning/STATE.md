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

- **Active focus: XSD-first foundation migration — UNBLOCKED.** Authoritative **goAML 5.0.2** schema +
  2 real DPMSR samples obtained from the live UAE portal and vendored into the repo
  (`src/main/resources/xsd/goaml/5.0.2/`, `src/test/resources/samples/`). This pivots the data layer
  ahead of continuing Phase 6. Plan: [plans/xsd-first-foundation.md](plans/xsd-first-foundation.md).
- **Phases 1–5 complete** (Phase 6 = next functional phase after the migration).
- **Branch:** `main` (uncommitted: docs/, .planning/, assets/, vendored XSDs+samples).
- **Build/tests:** ✅ green at last full run — `./gradlew test` → `BUILD SUCCESSFUL`.
- **Last completed:** Phase 5 — engine validation + UAE jurisdiction + lookups (commit `102484d`).

## Next Action — XSD-first codegen

> **Progress:** Step 1 ✅ (XSD validation gate + real samples validate), Step 2 ✅ (xjc codegen → 46
> generated classes, dates→OffsetDateTime, ReportType enum), and Step 3 ✅ (both real DPMSR samples
> round-trip through the generated model — unmarshal → values verified → re-marshal → re-validate clean
> against the XSD) are done & green. **Next = Step 4** (re-point engine onto the generated model + retire
> hand-modeled `domain/*`). ⚠️ Step-4 note from Step 3: the generated `Report` body is a catch-all
> `List<JAXBElement<?>>` (`getContent()`) — builders set header fields via `ObjectFactory.createReportXxx(…)`,
> not typed setters (schema reuses the name "Activity"). Step docs in [steps/](steps/).

1. Wire **xjc** codegen into `build.gradle` (JAXB Gradle plugin) → generate JAXB types from
   `goAMLSchema.xsd` into `com.vyttah.goaml.domain.generated`; `.xjb` binding maps `sql_date` →
   `OffsetDateTime` (reuse `GoamlDateTimeAdapter`).
2. Build the **XSD validation gate** (`engine/validation/XsdSchemaValidator`) using **standard JDK JAXP**
   (no asserts in the schema → no Saxon needed). Validate the 2 real sample XMLs as the first test.
3. Re-point `engine/` (builders, marshaller, validator, samples) to generated types; retire hand-modeled
   `domain/*`.
4. Regenerate goldens against 5.0.2; expand report-type coverage toward the 17 real codes.

⚠️ Substantive/destructive step (replaces Phase 3 hand-modeled domain) — confirm approach before the
big engine re-point. Codegen + validation-gate + sample validation can proceed first (low risk).

## Progress

`[███▌░░░░░░] 5/14 (≈36%)`

| Done | Phase |
|------|-------|
| ✅ | 1 Skeleton · 2 Multi-tenancy+security · 3 domain/ · 4 engine builders+marshaller · 5 engine validation+jurisdiction+lookups |
| ⏭️ | **6 integration/aws/ + b2b/ client** |
| ⬜ | 7 persistence+web REST · 8 S3 attachments · 9 scheduler · 10 notifications · 11 ingestion · 12 mcp+cli · 13 frontend · 14 infra |

(Full table + Phase 6 detail in [ROADMAP.md](ROADMAP.md) and
[docs/09-build-order-and-roadmap.md](../docs/09-build-order-and-roadmap.md).)

---

## After the XSD-first foundation — Phase 6 (standalone core resumes)

> The XSD-first codegen above comes first. Once the domain is regenerated, Phase 6 continues the
> standalone core. Two independently-testable pieces (build 6a first — the B2B client depends on it for
> credentials):

- **6a. `integration/aws/`** — `SecretsManagerClient` (+KMS), `S3StorageClient`, `SesClient`, tested
  against **LocalStack** (`docker compose up -d localstack`). Resolve a tenant's goAML creds from
  `tenant_goaml_config.secrets_path`.
- **6b. `b2b/`** — `GoamlB2bClient` + `RestGoamlB2bClient` (Spring `RestClient`) + `TokenManager`
  (per-tenant creds, cache token, re-auth on 401). Ops: `postReport→reportkey`, `getReportStatus`,
  `deleteReport`, `postMessage`, `getLookups`. Typed errors. Tested against **WireMock**.

Protocol spec: [docs/10-b2b-submission-protocol.md](../docs/10-b2b-submission-protocol.md).
**Before coding:** add the **AWS SDK v2** dependency and a **WireMock** test dependency to
`build.gradle` (neither is present yet), and run `superpowers:brainstorming` to lock the design.

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
