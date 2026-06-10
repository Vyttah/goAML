# Discussion Log — goAML Platform

> A durable record of the questions, answers, and decisions made while planning/building this project,
> so future contributors (and future-us) understand **how and why** we got here. Newest session on top.
> Formal decisions also live in [PROJECT.md](PROJECT.md) → Key Decisions; live status in [STATE.md](STATE.md).

---

## Session 2026-06-09 (later) — Competitive teardown (LexAML) + suite-cockpit plan

Participants: developer (rajat.gajera) + Claude. No code yet — strategy + planning.
Ask: the developer walked Claude (via screenshots) through **LexAML** (liveexshield.com), a deployed UAE AML
suite with paying clients, and asked for a thorough comparison + "can we be better as planned?" + a plan for
**both** Vyttah products.

### What LexAML is
One monolith: Customer Onboarding → Sanction Screening → RBA → Transaction (pick customer + enter deal) →
Transaction Approval (maker-checker) → GoAML Xml (generate) → **manual upload to the goAML portal**. It even
generates the older "goAML XML **Schema v2**" variant (+ a "Gold" XML). Stores a reusable customer master with
shareholder/director/UBO/bank sub-records; a per-tenant "GoAML Person" (reporting person) setting gates XML.

### The comparison (grounded in our code)
The breadth LexAML bundles — customer master, screening, RBA, case/ISTR — **already exists live in Vyttah's AML
software**. goAML supplies what LexAML's last mile only fakes: **B2B REST auto-submission + status poll**
(LexAML = manual upload), multi-tenant SaaS, REST/MCP/CLI, and **5.0.2 schema fidelity** (LexAML's output is the
older v2). Phase 1.5 already wired AML↔goAML at the data layer (federated SSO + screening party feed + accounting
push). Gaps vs LexAML are all small/ours: lookups, tenant-default MLRO, maker-checker, finishing the seam.

**Verdict:** the **suite** (AML software + goAML) beats LexAML — on breadth (tie→win via depth) and decisively on
automation — **iff live B2B submission is proven**. That an incumbent with clients chose manual upload is the
loudest signal B2B needs validating; it's externally gated (SACM/real RE) so it runs in parallel.

### Decisions (drive `plans/suite-cockpit-integration.md`)
1. **Cockpit = the AML software** (owns all masters + transactions). **goAML = report generator +
   system-of-record**: stores the whole payload (parties + goods + transaction details + master snapshot) + XML +
   FIU status so a goAML login shows the full transaction *and* its report — but **no separate editable masters**.
2. **Maker-checker on both planes** — AML business sign-off upstream; goAML adds its own MLRO/compliance review
   stage before FIU submit (two-tier, not redundant).
3. **Priority = demo-ready suite first** (seam + lookups + tenant MLRO + maker-checker); chase a client RE → live
   B2B proof in parallel.
4. **MLRO fed by goAML, not AML** — goAML stores the reporting person as a tenant default and auto-injects it;
   the AML software sends none.

Key code fact that simplified the plan: `report.input` (JSONB) + `report.report_xml` already persist the full
report — so "store the whole transaction" is mostly a read/view concern (Phase D.2), not new storage.

Plan: [plans/suite-cockpit-integration.md](plans/suite-cockpit-integration.md) (goAML Phases A–E + AML-software
integration requirements + the seam contract). Order: A → B → D.2 → C → D.1/D.3, E in parallel.

**Then read the real AML stack** at `dev/AML` (SpringBoot4 `aml-orm`/`customer-service`/`admin-service`/`user-service`
+ Next.js Frontend_Customer cockpit + Python sanctions scraper) → three findings reshaped the plan: **(1) the AML
software has NO transaction/deal entity or screen** (onboarding/screening only) — the DPMSR "deal" must be built;
**(2) auth is HS256 shared-secret, no keypair** — but goAML's federated/integration auth expects an RS256 signed
assertion, so an auth bridge is new AML-side work; **(3) nothing in AML calls goAML yet** (goAML's screening
*receiver* is built; the AML *sender* is greenfield). Tenancy maps cleanly: AML `company_id` → goAML
`tenant.external_org_ref`. Two decisions locked: **the deal module is built in the AML software** (entity +
customer-service endpoints + Frontend_Customer screen; "File to goAML" pushes the bundle), and **we prove the pipe
end-to-end first** (Slice 1: RS256 bridge + 1 company→tenant + 1 customer's parties → goAML draft, no UI). Plan
rewritten to grounded **v2** with the AML↔goAML field mapping + slice/phase sequencing
(Slice 1 + Phase A → B → C deal module → C.4 → D, E parallel). AML stack saved to auto-memory (`aml-software-stack`).

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
+ robustness fixes.

### During the developer's SPA review — one more fix

6. **SUPER_ADMIN landed on the tenant Reports dashboard → "Access Denied"** (reports are tenant-scoped; a
   platform SUPER_ADMIN has no tenant), and the notification bell **500'd** (the `notification` table lives
   in tenant schemas, absent in `public`). Fix: role-aware landing (`landingPathFor` → SUPER_ADMIN `/admin`),
   hide tenant-scoped nav + bell from a tenantless admin, a Dashboard deep-link guard, and harden the
   notifications API to a clean **403** for a tenantless caller (mirrors the reports API). Branch
   `fix/superadmin-landing` → merged to `main`. Gate held (frontend 62, backend coverage).

Phase 1.5 remains the next track (deferred until the developer's go-ahead).

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

## Session 2026-06-08 (later) — Phase 1.5 kickoff + key design changes

Started building **Phase 1.5** (suite integration + federated auth) after the pre-1.5 verification pass.
Confirmed scope = all three deliverables A→B→C; service trust = **signed service assertion** (RS256, per-source
registered public key). Four design decisions were taken/recorded this session that **change the locked
architecture** (`integration-and-auth-architecture.md` updated accordingly):

- **Accounting integration is REST, not RabbitMQ.** Accounting POSTs the invoice/transaction and gets an
  *immediate verdict* (reportable → draft created, or acknowledged); accounting then *pulls status*. Reasons:
  the immediate-confirmation UX the developer wants, one REST ingestion style shared with screening (no broker /
  AMQP / Testcontainers-RabbitMQ), and goAML's existing status API fits a pull. The one tradeoff —
  delivery-loss if goAML is down — is covered by an **accounting-side outbox + retry** made safe by **goAML
  idempotency on the external ref**. (Overturns the doc's RabbitMQ decision.)
- **Embedded / headless consumer model.** Both accounting **and** the AML software integrate goAML as
  first-class REST API clients behind *their own* UIs (federated user JWT → call `/api/v1/reports`…). goAML is
  the **single system-of-record** + FIU submission authority; a report created by any client is visible to all
  (same tenant). MLRO submit-gating still applies to the federated user (embedding can't bypass the control).
- **Accounting = BOTH models:** (1) the client builds the DPMSR itself and uses the existing report API, **and**
  (2) the client pushes a raw invoice and goAML auto-detects + builds the draft.
- **Reportability-check endpoint:** goAML exposes its authoritative "is this reportable?" verdict
  (`POST /api/v1/reportability/check`) so clients can check before building/submitting. Detection rules stay
  owned by goAML (no rule drift across apps).

- **Sub-phase 1.5a — federated auth: BUILT + merged to `main`** (branch `feature/phase-1.5a-federated-auth`,
  5 gated steps, full backend gate green at each):
  - **1.5a.1** `goaml.auth.mode` = native|federated|both (`AuthProperties`/`AuthMode`, default native →
    standalone unchanged); `/login` gated off when federated → `AuthModeDisabledException` (404). `a4f1e4a`.
  - **1.5a.2** `shared/V3__federated_identity.sql` + JPA: `external_identity`, `trusted_service`,
    `tenant_external_ref` (mapping table — accounting/screening may use different org ids), and
    `tenant_goaml_config.auto_submit` (default false). Testcontainers IT. `59b9e94`.
  - **1.5a.3** `ServiceCredentialValidator` — RS256 verify against the registered per-source public key,
    `aud=goaml`, reject expired, cap lifetime ≤5 min (replay window), cross-check iss. 10 unit tests. `12e0036`.
  - **1.5a.4** `POST /api/v1/auth/federated/token` — verify assertion → resolve `external_identity` →
    app_user + tenant → issue the **standard** goAML JWT (downstream unchanged); JIT-provisions unknown users
    at least privilege (ANALYST), refuses to silently link by email. 7 unit + MockMvc E2E (token usable +
    tenant-scoped on `/api/v1/me`; bad signature → 401). `ef04cd9`.
  - **1.5a.5** JaCoCo `coveredPackages` += `service/auth/**`, `controller/auth/**`, and the new `security`
    classes; planning docs synced; `--no-ff` merge to `main`.
- **Next:** 1.5b (accounting REST — reportability detector + check endpoint buildable now; raw-invoice push
  DTO gated on the Vyttah accounting invoice schema), then 1.5c (screening). **Push still gated on the
  PII-history purge.**

### Session 2026-06-09 — Sub-phase 1.5b (accounting REST): BUILT + merged to `main`

Branch `feature/phase-1.5b-accounting`, gated steps `d4a50de`…(1.5b.6), `--no-ff` to `main`. Full backend
gate (`./gradlew test jacocoTestCoverageVerification`) green at each step.

- **1.5b.1-2 — `ReportabilityDetector` + check endpoint.** goAML-owned rules (no drift across apps):
  reportable iff the cash component is **≥ AED 55,000** AND the goods are precious-metals/stones. `POST
  /api/v1/reportability/check` (user-JWT authed, for embedded UIs) → `{reportable, reasons[]}`; AED-only in
  v1 (non-AED rejected). `d4a50de`.
- **1.5b.4 — Model 2 raw-invoice push.** `POST /api/v1/integration/accounting/transactions` (server-to-server,
  `X-Service-Assertion` authed) → 202 `{goamlRef, reportable, reportId, status, reasons}`. Reportable → goAML
  builds a **validated DPMSR draft** (tenant from `companyId` via `tenant_external_ref`); **idempotent** on
  `ACC-<companyId>-<documentNumber>` (safe accounting-outbox retry). DTO `AccountingTxnPayload` shaped to the
  Vyttah accounting/masters vocabulary the user provided (source doc, cash settlement, party
  corporate→Entity / individual→Person, `goods[]`). **Key finding:** goAML's XSD **enumerates `goods.item_type`**
  (caught at runtime — `PRECIOUS_METAL` failed `cvc-enumeration-valid`), so `CommodityMapping` maps the
  accounting `commodityType` enum → real codes: `METAL→GOLD`, `LOOSE_DIAMOND_*→DIMND`,
  `DIAMOND_JEWELLERY_*→JEWEL`, `COLOR_STONE`/`PEARL→GEM`, `WATCH→WATCH`. **Watch rule (confirmed):** a watch is
  DPMS goods only when the line carries metal/stone value. (`METAL→GOLD` is coarse — refining to SLVER/PLTNM
  needs a masters metal-type field, a later enhancement.) `2c0d1c1`.
- **1.5b.5 — submit gating.** After a VALID draft: `tenant_goaml_config.auto_submit` **ON** → `SubmissionService.submit`
  now (audited); a transport/credential failure is **swallowed and falls back to the MLRO gate** (an FIU
  outage never fails the accounting push). **OFF** (default, safe) → keep the draft + notify the tenant's
  MLROs via a new `NotificationService.notifyDraftAwaitingReview` (in-app `REPORT_PENDING_REVIEW`, email if
  enabled; factored out of `notifyReportTransition` via a shared `fanOut`). `0dc8cd9`.
- **1.5b.3 — Model 1 embedded consumer.** Proved (E2E) an external app drives the **existing** `/api/v1/reports*`
  API with a **federated** JWT end-to-end (create→validate→list→get→xml→submit→status) — goAML as single
  system-of-record — and that **MLRO submit-gating holds for federated users** (a JIT ANALYST: create OK,
  submit 403). Wrote the consumer contract `docs/14-suite-integration.md` (both models, assertion, auth modes,
  exchange, report API, check endpoint, push + `auto_submit` + idempotency/no-loss). `58e6cf8`.
- **1.5b.6 — wrap-up.** JaCoCo `coveredPackages` += `ingestion/reportability/**`, `service/integration/**`,
  `controller/integration/**` (DTOs/entities excluded, as elsewhere); planning docs synced; `--no-ff` merge.
- **Next:** 1.5c (screening REST push + a goAML SPA form) — gated on the Vyttah screening payload schema; the
  AML software can already use Model 1 today. **Push still gated on the PII-history purge.**

### Session 2026-06-09 (later) — Sub-phase 1.5c (screening): BUILT + merged → Phase 1.5 COMPLETE

Branch `feature/phase-1.5c-screening` (`f0d4ccc`…1.5c.5, `--no-ff` to `main`). Full gate green per step.

- **Schema source decision.** The screening payload schema came from the **live `customer-service` OpenAPI**
  (`https://dev-aml-api.vyttah.com/customer-service-0.0.1-SNAPSHOT/v3/api-docs`) — the Vyttah AML software,
  the same platform documented in the `aml-ai-skills` cheat-sheet (its *API contract* is fine to reuse here;
  only its *build order* is off-limits per the root CLAUDE.md). **Output decision (user):** a screening
  customer profile → **reusable party + DPMSR draft seed** (not a new STR report type, not enrich-existing).
- **Resolved-codes contract.** Screening masters are FK ids (`nationalityId`, `idTypeId`, …); per the locked
  "source assembles, goAML never calls back" rule, the wire DTO `ScreeningPartyPayload` carries **resolved**
  ISO/goAML codes (the screening side resolves before pushing, like accounting).
- **1.5c.1** `ScreeningPartyMapper`: customer→Entity(LEGAL, +directors)/Person(NATURAL); shareholders/UBOs→
  parties (`Shareholder`/`Beneficial Owner`); PEP + sanctions hits→customer party comments.
- **1.5c.2** `screened_subject` tenant table (`tenant/V6`) storing the resolved payload as JSONB; idempotent
  upsert on `SCR-<companyId>-<customerUid>`; `POST/GET /api/v1/integration/screening/subjects` (assertion auth).
- **1.5c.3** user-facing `/api/v1/screening/subjects` browse + `POST /{ref}/seed-report` → DPMSR draft from the
  subject's parties + caller goods/MLRO. **Latent engine gap found + fixed:** a DPMSR person party
  (`t_person_my_client`) requires `gender`, `birthdate`, `country_of_birth`, `id_number`, `nationality1`,
  `residence`, a `<phones>` wrapper, a full identification (coded type + issue/expiry) **and** `tax_reg_number`.
  Added `country_of_birth` to the DTO/mapper and made the engine always emit a (possibly empty) `<phones>`;
  the identifications block is omitted (partial screening data would fail it) in favour of the required
  top-level `id_number`. Net: **entity**-party customers seed fully VALID reports; **person**-party customers
  seed drafts an analyst completes (the missing ID-doc/tax fields). (Prior to this, *no* natural-person DPMSR
  party could validate — the accounting path had only ever used entity parties.)
- **1.5c.4** SPA **Screening** page (Ant Design table + seed modal → navigate to the report); frontend gate
  green (tsc/eslint/64 vitest/vite build).
- **1.5c.5** JaCoCo += `service/screening`/`controller/screening` (added mapper edge-case tests to hold the
  ≥80% branch bar); docs (`docs/14`) + planning synced; `--no-ff` merge. **Phase 1.5 closed.**

## Full-schema fidelity (DPMSR contract 1:1 with the official XSD)

- **Trigger:** a third real DPMSR (`assets/USG0000000297 299.xml`, another vendor's software) exposed ~13
  fields the curated `DpmsrCreateRequest` silently dropped. **Root cause:** loss was only in the hand-curated
  DTO layer — the xjc-generated domain + engine input were already 1:1 with the XSD.
- **Decision:** make the contract **full schema 1:1** by binding the JSON contract to the generated types
  (`DpmsrReportPayload`) instead of hand-curating — self-maintaining, can't drift again. A scoped Jackson
  module binds the xjc `@XmlEnum` types by their schema `value()`. Server still injects/forces the header
  (`rentity_id`, `submission_code=E`, `report_code=DPMSR`, `currency=AED`).
- **Mid-flight call:** kept the curated `DpmsrCreateRequest` as the internal ergonomic builder
  (MCP/accounting/screening/CSV) and **extended** it (goods `disposed_value`/`status_comments`/
  `registration_number`/`identification_number`; accounting maps the invoice no → `registration_number`)
  rather than deleting it — lower churn, captures the real fidelity win. Frontend done now (not deferred).
- **Findings:** real files use the inline `<identification>` (not the `<identifications>` wrapper); and they
  nest `employer_address_id`/`employer_phone_id` inside `<director_id>`, which is **XSD-invalid** in
  `t_entity_person` — our XSD gate correctly rejects it.
- **PII:** `assets/USG…` + `assets/TR.2079.*` are real-PII and must not be committed (repo was history-purged
  for push). Left on disk untracked; an **anonymized** copy lives at `src/test/resources/samples/USG-dpmsr-activity.xml`.

## goAML as a frontend-direct microservice for the AML cockpit (pivot — 2026-06-10)

**Decision (APPROVED — building; auth model = Option 1 Federated SSO, chosen 2026-06-10):** reposition goAML
as a microservice the **AML frontend calls directly**, retiring the AML-backend proxy shape built in Phase C/D.
Full plan:
[plans/goaml-as-aml-microservice.md](plans/goaml-as-aml-microservice.md).

- **User's pivot (verbatim intent):** AML frontend gets a **Create Transaction** + **Approve Transaction**
  menu (+ **Download XML** after approve); the frontend calls goAML **directly** to create / approve /
  generate-validate XML; it **fetches party data from the AML services** and **assembles the whole DPMSR
  payload on the frontend**; **all transaction + DPMSR + goAML data stays in goAML** (no AML persistence);
  screening + other AML features are unaffected.
- **Supersedes, doesn't disturb:** Phase C/D built the same loop via an **AML-backend proxy + an AML
  `goaml_transaction` deal table** (`feature/goaml-integration`, **unmerged**). This pivot moves the calls to
  the **frontend** and drops AML-side persistence. Because that branch is unmerged, AML mainline is untouched —
  the old deal module stays **dormant**, not ripped out.
- **Verification fan-out (6 readers, both repos)** confirmed the full contract surface. The **goAML side needs
  near-zero change** — create / review-approve-reject / submit / detail / xml / status / lookups /
  reportability all already exist (table in the plan §5).
- **⭐ The auth crux (verified):** `POST /api/v1/auth/federated/token` requires an **RS256 assertion signed by
  a PRIVATE key** → **cannot be minted in a browser**. But `/api/v1/auth/login` IS browser-callable and CORS is
  configurable (`goaml.web.allowed-origins` → the AML origin), so a browser **can hold a goAML JWT and call
  `/api/v1/reports*` directly** — the only question is **how it gets the token**. Options:
  1. **Federated SSO via a thin AML-backend token-mint (RECOMMENDED)** — one AML-backend call mints the
     assertion + exchanges it → goAML JWT; the browser then calls everything else on goAML directly. Transparent
     SSO, per-user attribution, reuses the built assertion signer + federated endpoint. Approve/submit are
     **MLRO-only**, so the federated approver must map to a goAML **MLRO**.
  2. **Native goAML login** — browser logs into goAML (`/auth/login`) directly; zero AML-backend involvement;
     but a second credential to manage (shared service account loses attribution).
  3. per-call assertion from the browser — ❌ key-in-browser, rejected.
- **Payload contract:** the public `POST /api/v1/reports` takes the heavy full-schema `DpmsrReportPayload`;
  `ReportService.create(DpmsrCreateRequest,…)` (curated) already exists → **recommend** adding a small additive
  `POST /api/v1/reports/dpmsr` so the frontend assembles the **curated** shape, not the xjc tree.
- **Field gaps the Create form must collect** (goAML needs, AML KYC lacks/omits): occupation, residence
  country, incorporation state, commercial name, full ID-doc block + multiple IDs, TRN; a DPMSR **person** party
  without a full ID doc + `tax_reg_number` seeds an analyst-completed **draft** (engine rule from 1.5c).
- **Build shape:** G1 (goAML config + optional curated endpoint) · A1 (frontend goAML auth bridge +
  `axiosInstanceGoaml`) · A2 (Create Transaction page — KYC prefill + payload assembly) · A3 (Approve
  Transaction page — goAML review workflow + Download XML) · A4 (lookups direct + docs).
- **✅ Auth model chosen (2026-06-10): Option 1 — Federated SSO via a thin AML-backend token-mint.** The
  browser bootstraps its goAML JWT through one AML-backend call (`POST /api/v1/goaml/token` → mint RS256
  assertion → goAML `/auth/federated/token`), then calls every transaction op on goAML directly. Build order:
  **G1** (goAML: `GOAML_AUTH_MODE=both` + CORS for the AML origin + role-map the approver → goAML MLRO +
  optional curated `POST /api/v1/reports/dpmsr`) → **A1** (AML `GoamlTokenController` + frontend
  `axiosInstanceGoaml`/goAML-JWT interceptor) → **A2** (Create Transaction page) → **A3** (Approve Transaction
  page) → **A4** (lookups direct + docs).
- **✅ PIVOT COMPLETE (2026-06-10).** Built + gated across both repos: **G1.1** curated `POST /reports/dpmsr`
  (`a69a184`) · **G1.3** per-`trusted_service` `default_role` → SCREENING JIT→MLRO (`f76998d`, shared V6) ·
  **A1a** `GoamlTokenController` mint→exchange (`7a399e0`) · **A1b** `axiosInstanceGoaml` + getters (`0f57680`) ·
  **A2** Create Transaction page (`ca45180`) · **A3** Approve Transaction page + Download XML (`820dc08`) ·
  **A4** docs/closeout (lookups already direct; dormant Phase C/D module left intact; run/test guide = plan §10).
  **goAML half verified LIVE** (§8a — federated SSO → MLRO → direct reports/lookups/curated-create + CORS);
  A2/A3 gate-green (tsc/lint/next build). Auth = **Federated SSO** (one backend mint, then browser → goAML for
  every op). All goAML-side knobs are runtime/standalone-safe (`auth.mode`, CORS, `default_role` null→ANALYST).
  **Remaining:** the user's live cockpit pass (plan §10) + Phase E (real per-tenant FIU creds, external).
  **Code-set fidelity caveat:** AML masters' nationality/occupation/country codes vs goAML's sets — confirm in
  the live pass.

---

## 2026-06-10 — Rich Transaction Builder (LiveExShield parity) track opened

- **Ask:** make the cockpit Create Transaction match (and beat) LiveExShield — collapsible
  **Customer / Shareholder / Director-Representative / Bank / UBO** sections, each a selectable relation table
  that expands to the relation's full KYC (incl. an ID-documents "Detail" sub-table), plus a deep
  **Transaction Details** block. Also UI restyle to 100% match Customer Onboarding (done: commits `77147b2`
  page restyle, `af10573` app select-icon).
- **Decisions (locked):** (1) **capture everything, never break the XML** — show all LiveExShield fields, file
  only XSD-valid elements (FIU never rejects), keep the rest as display/metadata so nothing is lost; (2)
  **frontend-first then goAML** (T1 UI → T2 carry-through); (3) **read-only prefilled** relation detail panels.
- **Contract reality (verified):** DPMSR is activity-shaped (parties+goods, no txn block). LiveExShield fields
  split: ✅ map today (report/goods) · 🟡 XSD-supported but not in curated DTO (rich director/UBO, bank account
  party, role codes, PEP, multi-ID, nationality2) · ❌ not in XSD at all (payment mode, channel, rate, carrier,
  late deposit, amount LC, indemnified, shareholding %).
- **Key enabler:** the shipped **full-schema-fidelity** path — `POST /api/v1/reports` binds `DpmsrReportPayload`
  (xjc leaf types, 1:1 XSD). T2 switches the cockpit to the full payload to carry **every** field; no curated-DTO
  extension needed. See `.planning/plans/full-schema-fidelity.md`.
- **Plan:** `.planning/plans/rich-transaction-builder.md` (T1 frontend → T2 goAML carry-through → T3 detail view).

- **✅ T1 done (2026-06-10, frontend):** Create Transaction rebuilt into collapsible LiveExShield-style
  sections — Customer (subject) + Shareholder / Director-Representative / Bank / UBO (each a selectable
  relation table expanding to read-only KYC + ID-docs "Detail" sub-table) + Goods + Transaction Details
  (indicators/description/action/branch filed; Additional Details captured but not in the XML). Selected
  relations map to `entity.directors[]` with role codes (DIR/SHRHL/UBO/ATR); banks deferred to T2. New files:
  `CollapsibleSection.tsx`, `RelationGroupSection.tsx`, `relationKyc.ts`. Gate green (tsc+lint). Commits:
  `77147b2` (UI restyle), `af10573` (app select icon), `7f1add0` (T1). **Next: T2** — switch the cockpit to
  POST the full `DpmsrReportPayload` so every KYC/user field round-trips to the XML (banks as account party,
  rich director/UBO detail, PEP, multi-ID, nationality2).

- **✅ T2 done (2026-06-11):** cockpit now posts the **full-fidelity `DpmsrReportPayload`** to `POST /api/v1/reports`
  (ported adapter `dpmsrPayload.ts` mirrors goAML's SPA: camelCase, phones/addresses wrappers, `directorId[]`,
  inline `identification[]`, `nationality1`, `personMyClient`). **goAML change:** `reportingPerson` relaxed to
  optional + `create(DpmsrReportPayload)` injects the tenant MLRO from `tenant_goaml_person` (mirrors the
  curated path) — commit `a44892c`, EmbeddedConsumerE2ETest covers it, full gate green. **Frontend:** commit
  `e42eb11`; also fixed two real XSD bugs found via the persisted `validation_errors`: alpha-3→alpha-2 country
  codes (`b5e60d3`) and the phone `tph_contact_type`/digit-prefix; plus the **identification `type` enum** fix
  (AML id-type name → `identifier_type` EID/PASSP/…; an identification is emitted only when type+number+both
  dates+issue_country all resolve, else omitted — XML can't be invalidated by a partial ID). **Remaining T2
  item:** banks are captured/displayed but not yet filed as a structured `account_my_client` party (required
  enum fields — status_code/currency/account_name — need live validation); flagged for the combined test.

- **✅ T2 bank party wired (2026-06-11):** banks now file as a goAML **`t_account`** party (the light
  counterparty account — only `swift|institution_code` + `account` + `account_name` required), NOT the heavy
  `account_my_client` (which would require fabricating signatory/opened-date/status/branch). Maps real KYC:
  bankName→institutionName, swift, accountNumber→account, customer name→accountName, iban; filed only when
  swift + account number are present (else the bank stays displayed-only — no fabrication, XML never broken).
  Frontend commit `5bc0285` (+ adapter t_account branch). **Verified live**: full payload with a bank party →
  201 VALID and institution_name/swift/account/account_name/iban round-trip into the marshalled XML. **T2 is
  now complete** — every selected relation type (subject, directors/shareholders/UBOs/reps with roles+IDs,
  banks) plus all goods fields files into a FIU-valid DPMSR; non-XSD extras remain captured metadata.

- **✅ T2.1 — full LiveExShield field parity + correct mandatory markers (2026-06-11):** user flagged the cockpit
  still had far fewer fields than LiveExShield, no mandatory markers, and a confusing "Indicators" field. Ran a
  4-way XSD-grounded gap analysis (goAML 5.0.2 XSD ⇄ xjc leaf types ⇄ current form ⇄ LiveExShield/AML-KYC).
  Findings + decisions:
  - **`indicators` IS mandatory.** XSD `<report_indicators>` (≥1 `<indicator>` from the 423-value
    `report_indicator_type` enum) has no minOccurs ⇒ required; an empty one is XSD-INVALID and the FIU rejects.
    It IS LiveExShield's "Is STR/ISTR" reason-for-reporting. Relabelled "Reason for reporting (FIU indicator)" +
    helper; kept required.
  - **Subject party type switched to the LENIENT `t_person`/`t_entity`** (was natural→`t_person_my_client`,
    legal→`t_entity` — inconsistent). DECISIVE evidence: all three real FIU DPMSR samples use `<entity>`
    (non-my-client). `t_person_my_client` forces gender/birthdate/idNumber/nationality/residence/phones + a
    **required 1-char `tax_reg_number`** (line 1251) — a real TRN would XSD-break the XML and blank fields built
    INVALID. On `t_person`/`t_entity` only the name is required, every KYC field is optional ⇒ carry everything,
    FIU can't reject for a missing field. Also fixed `country_of_birth` being silently dropped by the adapter.
  - **Field parity:** added the missing LiveExShield fields, all KYC-prefilled — natural: alias, dual nationality
    (`nationality2`), source of wealth, email, full address; legal: business activity (→`business`, free-text,
    maxLen 255), date of incorporation, TRN (→`tax_number`), email, full address. `legal_form_type` is a restricted
    enum, so license type is NOT filed there (metadata). EID issue-country defaults AE, passport→nationality
    (definitional, editable, not fabricated). ID + address filed all-or-nothing (omit if incomplete → never breaks).
  - **Mandatory markers + validation** on every field the FIU/LiveExShield treats as required. PEP (no 5.0.2
    person element) + source of funds captured as report metadata.
  - **Live-verified** (POST /api/v1/reports, goAML :8090): natural `<person>` + legal `<entity>` full payloads
    both build **VALID, zero messages**, and every new element round-trips into the marshalled XML. Frontend
    commit `0348255`; **no goAML code change** (the `DpmsrReportPayload` full path already carries all of this).
