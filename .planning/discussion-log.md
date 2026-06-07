# Discussion Log — goAML Platform

> A durable record of the questions, answers, and decisions made while planning/building this project,
> so future contributors (and future-us) understand **how and why** we got here. Newest session on top.
> Formal decisions also live in [PROJECT.md](PROJECT.md) → Key Decisions; live status in [STATE.md](STATE.md).

---

## Session 2026-06-08 — Pre–Phase-1.5 full verification + hardening pass

Participants: developer (rajat.gajera) + Claude. Branch: `hardening/post-verification` → merged to `main`.
Ask: before starting Phase 1.5, thoroughly test everything (nothing missed / nothing hardcoded / no default
data), exercise every feature + endpoint with dummy data and generate XML (live FIU submit not possible —
no creds), then the developer reviews the SPA.

### What was verified (all green)

- **Both test suites:** backend `test jacocoTestCoverageVerification` (full + coverage gate) and frontend
  typecheck/lint/Vitest/build.
- **Live end-to-end walk** (dev-seed app on real Postgres + LocalStack S3/SecretsManager + Redis): auth +
  `/me` + RBAC (403s correct), lookups, **report create→validate→XSD→persist → generated well-formed goAML
  5.0.2 XML** (header auto-applied), validation negatives (threshold / bad enums / dup), submit (graceful
  502 on FIU transport — no real send), admin (provision/user/config), attachments (upload→S3/list/delete),
  notifications (fan-out + read), imports (XML→VALID, CSV per-row, bad-header reject), **cross-tenant
  isolation**, actuator health/prometheus, MCP SSE auth.
- **Hardcoded/default-data scan:** 0 TODO/FIXME in `src/main`; the only "defaults" are documented + gated
  (dev-seed password, LocalStack dummy creds, `rentity_id=0`→INVALID fallback, **placeholder lookup seeds
  pending real UAE exports**).

### Gaps found → fixed (developer chose "fix all, one by one"; 5 atomic commits, gate held, merged)

1. **Report XML view/download** — XML was built+persisted but had no REST/UI surface. Added
   `GET /reports/{id}/xml` + a "View XML" modal/download on the detail page.
2. **Attachment download** — listable but not retrievable. Added `GET …/attachments/{id}/content`
   (reuses `S3StorageClient.fetch`) + a per-row download action (viewers, not just editors).
3. **Opaque 500 on FIU integration failure** — `SecretsAccessException`/`S3AccessException` were unmapped.
   Mapped → **502** in `GlobalExceptionHandler`.
4. **Late config validation** — admin `goaml-config` accepted any `authMode` (failed only at submit with a
   cryptic enum error). Now validates `authMode` (→`B2bAuthMode`, normalized) + `jurisdictionCode`
   (→registry) at write time → 400.
5. **CSV template under-constrained** — `REQUIRED_HEADERS` omitted `indicators` + `party_reason` (both
   schema-mandatory), so "required-only" CSVs always produced INVALID rows. Added both → fail-fast 400.

All five re-verified live after rebuild. **No engine/validation/security logic changed** — these are surface
+ robustness fixes. Phase 1.5 remains the next track (deferred until the developer's go-ahead).

## Session 2026-06-03 — Resume, docs, XSD-first pivot, and report-type scope

Participants: developer (rajat.gajera) + Claude. Branch: `main`. All work uncommitted at session end
(pending the developer's go-ahead to commit).

### Topics & outcomes (in order)

**1. "Can we resume?"**
- Found the repo had **no `.planning/` GSD state**; progress was tracked only by git commits (Phases 1–5
  done) and an implementation plan that lived **outside** the repo (`~/.claude/plans/`).
- Confirmed the `aml-ai-skills` Claude skill describes a *different* project (a Python AML Co-Pilot
  chatbot), **not** this Java repo — do not use its build order here.

**2. "Write down everything a core developer needs to know, in this repo."**
- Read the whole codebase (6 parallel readers across build/persistence/security/domain/engine/tests) and
  wrote **`docs/` (12 files)** verified against the actual code, with explicit ⚠️ gap callouts where the
  code differs from the plan. Confirmed the full test suite green (`./gradlew test` → BUILD SUCCESSFUL).

**3. "Maintain everything in this repo so anyone on any machine can resume."**
- Copied the implementation plan in-repo → `docs/00-implementation-plan.md`.
- Created in-repo state: **`.planning/STATE.md` / `PROJECT.md` / `ROADMAP.md`** (plain markdown, also
  what the `resume` flow reads). Updated the root `README.md` to point at these. Removed external links.

**4. "Add a plan for a full-fledged Claude plugin + harness to use all goAML features."**
- Wrote **`.planning/plans/phase-12-plugin-and-mcp-harness.md`** — a Claude plugin (skills + slash
  commands + `.mcp.json` + pre-submit hook) over an in-app MCP server that wraps existing
  engine/b2b/service methods (parity with REST/CLI). Strong safety harness: validate-before-submit,
  dry-run default, **human-confirmed + MLRO-gated submission** (an AI agent must never silently file a
  regulatory report), idempotency, structured I/O, audit. Has 4 open decisions to confirm before build.

**5. "Use the latest XSD; fetch from internet; build the system over that only."**
- Researched: **latest goAML schema = 5.0.x** (XSD 1.1 lineage); repo targeted the older v4.0,
  hand-modeled. The authoritative XSD is **not on the open web** — it's behind login-gated FIU/UNODC
  portals. Decision recorded: **XSD-first** — generate JAXB from the authoritative XSD + add an XSD
  validation gate, replacing the hand-modeled domain. Plan: **`.planning/plans/xsd-first-foundation.md`**.

**6. "If they provide UAT access, find the steps; I'll get the required files."**
- Wrote **`.planning/field-acquisition-checklist.md`** — the UAE FIU onboarding flow (SACM → UAT/Prod →
  goAML Web) and a per-artifact table (XSD, lookups, B2B URLs/creds, rentity_id, report codes, BRRs) with
  where each lands in the repo. Secrets rule: credentials go to AWS Secrets Manager, **never git**.

**7. SACM registration blocker — "I'm a single developer, I don't have this data."**
- Finding: **SACM registration is gated to a real regulated entity** (needs a supervisory body +
  registration number + authorization PDF). A solo dev cannot self-register; do **not** submit fabricated
  data to the regulator. Live UAE access needs a real client-RE relationship. **Workaround:** build
  against a reference XSD now, swap the authoritative file in later. Captured in the checklist
  ("Reality check") + STATE.

**8. Developer added real files to `assets/` (XSDs + 2 sample reports). "Purely focus on these."**
- The blocker dissolved — authoritative files in hand. Analysis of `goAMLSchema.xsd`:
  - **goAML 5.0.2**, **no targetNamespace** (report XML is no-namespace), **39 complexTypes + 45
    simpleTypes**.
  - **No XSD 1.1 asserts / openContent / alternatives** → plain **xjc codegen + standard JDK JAXP
    validation work; no Saxon/Xerces-EE needed** (the earlier feared complexity evaporated).
  - **17 report types** in `report_type`: AIF, AIFT, CIR, CNMRA, DPMSR, ECDD, ECDDT, HRC, HRCA, IRR, ITR,
    PNMRA, PSTR, REAR, SAR, SIR, STR.
  - Real structural drift vs the hand-modeled domain: `goods_services` wraps repeating **`<item>`**;
    `report_party` can be an **`<entity>`** with a nested **`<director_id>`** (person + `<role>`); dates
    use `sql_date` (= our existing `yyyy-MM-dd'T'HH:mm:ss` adapter format).
  - The two real samples (`TR.2079.200000309/310.xml`) are **DPMSR** (gold purchase, cash > AED 55,000).
  - Vendored into the repo: `src/main/resources/xsd/goaml/5.0.2/` and `src/test/resources/samples/`.
  - `goAMLReportDataSchema.xsd` has **no header** → it's an internal/export schema, **not** for
    submission. We build over `goAMLSchema.xsd` only.

**9. "What reports/transactions will this app convert to XML and upload?"**
- Clarified: the app uploads **reports** (XML), not raw transactions. Two shapes — **transaction-shape**
  (contains `<transaction>` elements) vs **activity-shape** (parties + goods, no transactions; DPMSR is
  this). Walked through all 17 schema codes + meanings (STR/SAR/DPMSR/REAR/HRC/HRCA/PNMRA/CNMRA/AIF/
  AIFT/ECDD/ECDDT confident; **CIR/IRR/ITR/PSTR/SIR uncertain** — need the UAE "Different Types of Reports
  v1.2" doc to define). PNMRA/CNMRA are the TFS/sanctions name-match reports (potential vs confirmed
  match; CNMR superseded the old FFR/Funds-Freeze concept).

**10. ⭐ DECISION — report-type scope (phased):**
- **Phase 1 (first target): precious-metals dealers → `DPMSR`**, plus **`STR`/`SAR`** (baseline) and
  **`PNMRA`/`CNMRA`** (sanctions/TFS) as likely near-term additions.
- **Next phase: all 17 report types.**
- Rationale: the real samples + first customers are gold dealers; DPMSR is the concrete, highest-value
  starting point. The engine is generated from the schema so it *can* do all 17, but we deliver DPMSR
  first and expand.

**11. "Targeting DPMSR first — which type of transaction does it cover?"**
- **Qualifying transactions (the reporting triggers):** (1) **cash** transaction with an individual
  (resident/non-resident) ≥ **AED 55,000**; (2) **cash** with a corporate entity ≥ 55k; (3)
  **international wire transfer** with a corporate entity ≥ 55k; (4) **cash instalment/advance**
  (reported when funds received) ≥ 55k; (5) **unfixed gold** in cash ≥ 55k. **Filing window: 2 weeks.**
- **Exempt (not reportable):** credit-card/cheque/bank transfer with individuals (any amount); old-gold
  exchange for new (no cash over threshold); local UAE-bank wire/cheque.
- **⚠️ Technical nuance:** DPMSR is **activity-shaped** — it does **not** use the `<transaction>` block.
  The dealing is represented by **`goods_services/item`** (item_type GOLD, estimated/disposed value,
  size+uom, currency, registration_date) + **`report_parties`** (counterparty entity/person + director)
  + **`reporting_person`** + **`reason`/`action`** narrative. The "transaction" lives in the goods values
  + narrative, not in a structured transaction element. (Structured `<transaction>` modelling only
  arrives with **STR** later.) Both real samples confirm: 309 = cash purchase of gold; 310 = receipt of
  excess cash vs a gold purchase; each has one GOLD item + one counterparty entity.
- **To confirm when building:** exact `item_type` + `size_uom` lookup values (from goAML lookup tables);
  authoritative trigger source = UAE FIU **DPMS Report Submission Guide**. `dpmsThreshold: 55000` already
  in `ae.yml`.

**12. "I have Vyttah accounting (microservices, RabbitMQ) + an AML screening (sanctions+KYC) app — where
should goAML integrate / how to keep it?"**
- Recommendation accepted: **goAML = its own dedicated microservice** ("Regulatory Reporting Service"),
  alongside (not merged into) accounting + screening. Bounded contexts: Accounting owns transactions;
  Screening owns KYC + sanctions; goAML owns reports/validation/FIU-submission/audit. **Reportability
  detection lives in goAML.** DPMSR data flow mapped across the three systems.

**13. "I also want to sell goAML standalone (manual entry + XML + submit), plus auto-submit from accounting
and a form/API from screening — but keep that as Phase 1.5."**
- **Standalone** = the existing core (manual entry → build → validate → submit), native auth. **Phase 1.5**
  = accounting→goAML via RabbitMQ (auto-create draft) + screening→goAML via REST/form. Recorded as a new
  roadmap **Phase 1.5** (sequenced after the standalone core despite the label).

**14. ⭐ DECISION — unified authentication (3 forks answered):**
- **Identity approach:** keep goAML's **own JWT** as the identity authority. Accounting/screening
  authenticate their own user, then call a goAML **token-exchange** endpoint to mint a goAML token
  (service-to-service trust); goAML **stores accounting/screening users** (external-identity links) to
  resolve them. **No external IdP.**
- **Standalone login:** **configurable per deployment** (`goaml.auth.mode = native | federated | both`).
- **Auto-submit:** **auto-create validated draft → MLRO 1-click approve**; full-auto is a per-tenant opt-in.
- **FIU B2B creds are separate** from user login (per-tenant, Secrets Manager).
- Plan refined via **Ultraplan** (2026-06-04): it verified all code references and caught that `.planning/`
  + `docs/` were **uncommitted** (invisible in a fresh clone) → recorded the design in
  [plans/integration-and-auth-architecture.md](plans/integration-and-auth-architecture.md), added a durable
  root `CLAUDE.md`, and the action item to **commit** the knowledge base.

### Open decisions still pending
- The 3 mechanical codegen choices (xjc Gradle plugin, generated package `…domain.generated`, `sql_date`
  → `OffsetDateTime` binding) — defaults proposed; awaiting go-ahead to wire codegen (steps X.2–X.3).
- The 4 plugin/harness decisions in [plans/phase-12-plugin-and-mcp-harness.md](plans/phase-12-plugin-and-mcp-harness.md) §10.
- Definitions of the 5 uncertain report codes (CIR/IRR/ITR/PSTR/SIR) — fetch the UAE "Different Types of
  Reports v1.2" doc when needed.
- Whether/when to commit this session's work (docs/ + .planning/ + vendored XSDs+samples).

### Key references produced this session
- `docs/` (12 onboarding docs) + `docs/00-implementation-plan.md`
- `.planning/STATE.md`, `PROJECT.md`, `ROADMAP.md`
- `.planning/plans/phase-12-plugin-and-mcp-harness.md`
- `.planning/plans/xsd-first-foundation.md`
- `.planning/field-acquisition-checklist.md`
- Vendored: `src/main/resources/xsd/goaml/5.0.2/`, `src/test/resources/samples/`

---

## Session 2026-06-06 — Phases 10 & 11 built; remaining build order reordered

### Built + merged to `main`
- **Phase 10 (`notification/`)** — in-app store + SES email, fired at the `SubmissionService` seam to
  author + tenant MLROs; merge `335c0d6`.
- **Phase 11 (`ingestion/`)** — goAML XML + flat DPMSR CSV import as a persisted `import_job` with row-level
  results; reuses `ReportService.create`; merge `c533e70`. Also fixed a latent audit `TenantContext` clobber.

### Decision — defer Phase 12 to last; remaining order 13 → 14 → 12; Phase 1.5 deferred
- **Ask:** "hold the Claude plugin & MCP harness + CLI for now; finish the product and its every phase
  first, then take Phase 12 last — but answer only after proper research."
- **Research findings (not assumptions):**
  - **Nothing depends on Phase 12.** The **React frontend (13) talks to the REST API** (docs/00:
    "React talks to the REST API"), not MCP/CLI; every REST surface it needs already exists (Phases
    2/5/7/8/9/10/11). **Infra (14)** packages the jar/SPA regardless. **Phase 1.5** is independent.
  - **Phase 12 depends only on Phases 6–11** (its own plan §0/§9) — all complete — so it can run any time
    after 11, including last. It's now *more* buildable than when planned (all tool tiers unblocked).
  - **Both remaining product phases are buildable now:** 13 (frontend, REST-only) and 14 (infra; best after
    13 so the built SPA wires into `static/`).
  - **Caveat:** docs/00 defines the deployable as "REST API + MCP server + static SPA + CLI mode of one
    jar." With 14 before 12, infra needs a minor revisit when 12 ships (MCP HTTP route in Helm/ingress +
    `--cli` run-mode). Known touch-up, not a blocker.
- **Decisions (confirmed via AskUserQuestion):**
  - Remaining build order = **13 (frontend) → 14 (infra) → 12 (plugin/MCP/CLI, LAST)**.
  - **Phase 1.5** (Vyttah-suite integration + federated auth) = **deferred — decide later** (separate track).
- **Next action:** Phase 13 (React frontend) — write the plan, get approval, build per the gated workflow.

---

## 2026-06-06/07 — Phase 13: React frontend (built end-to-end)

- **Built the full `frontend/` SPA** (Vite + React + TS + Ant Design) over the REST API, on branch
  `phase-13/frontend`, in 11 gated steps (per-step docs `steps/PHASE-13.1..13.11`), 58 Vitest specs green.
  Backend enablers first (lookup API, admin API, CORS + SPA-serving), then the SPA area-by-area: auth/shell
  → dashboard → DPMSR builder → report detail/lifecycle → import → notifications + reference browser → admin.
- **Decisions (confirmed via AskUserQuestion):**
  - **DPMSR builder scope = the full nested form** (plan D1): header + reportingPerson (phone/address/
    identifications) + parties[] (person XOR entity, entities with directors[]) + goods[], with Zod mirror,
    lookup-driven country/currency dropdowns, and inline server `ValidationResult`.
  - **Local-review credentials = a gated dev seeder** (`config/dev/DevDataSeeder`, `goaml.dev.seed.enabled`,
    off by default) — there are **no seeded users** and provisioning needs a SUPER_ADMIN token
    (chicken-and-egg); the seeder creates a demo tenant + SUPER_ADMIN/TENANT_ADMIN/MLRO/ANALYST logins
    (password `Passw0rd!`). Never enable in a deployed environment.
- **Honest gaps surfaced (not faked)** — no backend endpoint exists for report **XML preview**, for
  **re-fetching an existing report's validation result** (messages return only at create time), or for
  **attachment download**. Flagged in the UI as small future backend adds. Lookups are placeholder
  code-only seeds (only country/currency are dropdowns). The **Gradle node task** (dist → static) is
  deferred to **Phase 14** packaging.
- **Next action:** merge `phase-13/frontend` → `main`, then **Phase 14 (infra)**.

---

## 2026-06-07 — Phase 14: infra (Docker · Helm · observability · CI/CD)

- **Built Phase 14 end-to-end** on branch `phase-14/infra` (steps 14.1–14.5):
  - **14.1 observability** — `micrometer-registry-prometheus` (`/actuator/prometheus`), `prod`-profile JSON
    logs (logback + logstash encoder), `CorrelationIdFilter` (`X-Correlation-Id` → MDC). Found Boot disables
    metrics export in tests → enabled via the specific `management.prometheus.metrics.export.enabled` key.
  - **14.2 Dockerfile** — finalized 3-stage (node SPA → layered bootJar w/ SPA on classpath → non-root JRE)
    + `.dockerignore`; verified by a real `docker build` + run. Fixed a `.dockerignore` `build/` pattern that
    also matched the `engine/build/` Java package.
  - **14.3 Helm** — full `helm/goaml/` chart; `helm lint` clean, renders across modes.
  - **14.4 GitHub Actions** — `ci.yml` (backend + frontend gates) + `cd.yml` (image build + secret-gated
    ECR/EKS deploy via OIDC).
- **Decisions (confirmed via AskUserQuestion):** SPA → jar via a **Dockerfile node stage** (Gradle stays
  Node-free; no Gradle node task); **CI gates + image build, deploy gated on secrets**; **full Helm chart**.
- **Out of scope / honest:** no real AWS account/EKS/ECR/remote, so CD is wired but inert until secrets
  exist; `helm` wasn't installed (used a throwaway binary to lint/template).
- **Carried-forward:** when Phase 12 ships, add the MCP HTTP route to the Helm ingress + the `--cli`
  run-mode. PII sample XMLs still must be purged from history before any first push to a remote.
- **Next action:** merge `phase-14/infra` → `main`, then **Phase 12 (plugin/MCP/CLI) — the last phase**
  (confirm its 4 open decisions first).

---

## 2026-06-07 — Phase 12 (plugin / MCP / CLI), the final phase

- **Decisions (confirmed via AskUserQuestion):** per-tenant **MCP service token** auth model (implemented as
  the same JWT resolution for now — service-token issuance is a focused later add); **human-confirmed,
  MLRO-gated, dry-run-first** submission; **full Claude plugin + MCP server**; **streamable-HTTP/SSE on EKS +
  stdio local**. The user also chose to **build Phase 12 only** (not pull the deferred Phase 1.5 forward).
- **Shipped in 7 gated steps (`94a0dce`…12.7), each with a per-step doc (`steps/PHASE-12.1..12.7`):**
  - **12.1** MCP server (Spring AI 1.0.2 webmvc SSE) + token auth + tenant/RBAC. Two non-obvious findings,
    both fixed: `base-url` is NOT honored for the WebMvc SSE route (used explicit `sse-endpoint`
    `/api/v1/mcp/sse`); the sync server runs tools on a Reactor thread, so the SecurityContext + TenantContext
    are carried across via Reactor automatic context propagation + ThreadLocalAccessors. Proven by a real
    SSE-client parity IT.
  - **12.2** read/build/validate/preview tools; added `ReportService.validate`/`previewXml` (closed the
    Phase-13 gap) + `engine/metadata/ReportTypeMetadata` (single source of truth, shared with the validator).
  - **12.3** the Claude plugin (skill + `.mcp.json` + `/goaml-build` `/goaml-validate`).
  - **12.4** submit/status/messages + the **safety harness** (MLRO, dry-run-first, confirm-required,
    validate-first) + a pre-submit hook; `SubmissionService.postMessage` added.
  - **12.5** import + admin tools (lookups-refresh **deferred** — no backend FIU-lookup sync exists).
  - **12.6** the **CLI** (`--cli` run-mode, picocli) with the same harness; made `SecurityConfig`/
    `DefaultAuthService`/`AuthController` `@ConditionalOnWebApplication` so the non-web context loads, and moved
    `JwtProperties` registration to `GoamlApplication`.
  - **12.7** packaging (repo-root `.claude-plugin/marketplace.json`), `docs/13-plugin-mcp-cli.md`, the
    carried-forward Helm-ingress/Dockerfile infra touch-up, planning sync, and the merge to `main`.
- **Honest scope:** RBAC is enforced at each tool/command via explicit `McpIdentity.requireAnyRole` /
  `CliAuthenticator.requireRoles` (reliable across the MCP/CLI edges) rather than `@PreAuthorize` through
  picocli/MethodToolCallback reflection. The `cli/**` package is not under the JaCoCo gate (its bootstrap glue
  isn't unit-testable); commands have unit + IT coverage. A live confirmed-submit-over-MCP/CLI E2E is left to
  the existing WireMock submission-service coverage.
- **Outcome:** the standalone product is complete — **14/14 phases**. Open: Phase 1.5 (deferred) + go-live
  prerequisites (AWS, a remote + the **PII-history purge**, FIU creds/lookups).
