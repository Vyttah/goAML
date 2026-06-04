# goAML — Consolidated Plan Summary (one-page review)

> A point-in-time review snapshot (2026-06-04) of the whole plan, for sign-off before implementation.
> Living source of truth remains [STATE.md](STATE.md); full detail in the linked docs.

## TL;DR
- **What:** multi-tenant platform that builds/validates/submits/tracks goAML reports to the UAE FIU.
  Sold **standalone** + run inside Vyttah's suite (accounting/ERP + AML screening).
- **Status:** Phases 1–5 done & committed; test suite green. **Immediate next = XSD-first foundation**, then
  the standalone core (Phases 6–14), then suite integration (Phase 1.5).
- **First report type = `DPMSR`** (precious-metals dealers, cash ≥ AED 55,000); all 17 schema codes later.

## Locked decisions
1. goAML is its **own microservice** — standalone + suite; never merged into accounting/screening.
2. **XSD-first:** build the domain over the **real goAML 5.0.2 XSD** (obtained + vendored) via **xjc-generated
   JAXB** + an XSD validation gate on **standard JDK JAXP** (schema has **no** 1.1 asserts → no Saxon).
3. **Scope phased:** DPMSR first (+ STR/SAR baseline, PNMRA/CNMRA sanctions near-term); all 17 next phase.
4. **Auth:** goAML keeps its **own JWT** as identity authority. 3 on-ramps → one standard JWT: native login,
   **federated token-exchange** (siblings authenticate their user → exchange for a goAML token), service
   principal. No external IdP. `goaml.auth.mode = native|federated|both`.
5. **Auto-submit:** accounting event → validated draft → **MLRO 1-click**; full-auto = per-tenant opt-in.
6. **FIU B2B creds ≠ user login** — per-tenant, AWS Secrets Manager; separate from all auth.
7. Bounded contexts: Accounting=transactions, Screening=KYC/sanctions, **goAML=reports + reportability +
   submission + audit**.

## Sequencing
```
XSD-first foundation  →  Standalone core (Phases 6–14)  →  Phase 1.5 (suite integration + federated auth)
   [immediate]            AWS+B2B→submit→track→web/UI         [labelled 1.5 but sequenced LAST]
```

## The immediate plan — XSD-first foundation  ([plans/xsd-first-foundation.md](plans/xsd-first-foundation.md))
| Step | Work |
|------|------|
| X.1 | ✅ done — `goAMLSchema.xsd` 5.0.2 + 2 real DPMSR samples vendored |
| X.2 | Wire **xjc** codegen (Gradle plugin + `.xjb`: package, `sql_date`→`OffsetDateTime`) |
| X.3 | **XSD validation gate** (standard JAXP) — first validate the 2 real samples |
| X.4 | Re-point engine (builders/marshaller/validator/samples) to generated types; retire hand-modeled `domain/*` |
| X.5 | Regenerate goldens vs 5.0.2; review diffs |
| X.6 | Report-type coverage: **DPMSR first**, then STR/SAR + PNMRA/CNMRA; rest next phase |
| X.7 | Refresh docs to 5.0.2 reality |

**↳ 3 mechanical decisions to confirm before X.2:** (a) xjc Gradle plugin (default `com.github.bjornvester.xjc`,
pinned); (b) generated package (default `com.vyttah.goaml.domain.generated`); (c) `sql_date`→`OffsetDateTime`
binding via `.xjb` reusing `GoamlDateTimeAdapter`.

## The later plans (design-complete; build when sequenced)
- **Phase 1.5 — integration + federated auth** ([plans/integration-and-auth-architecture.md](plans/integration-and-auth-architecture.md)):
  accounting (RabbitMQ) → reportability → auto-draft → MLRO approve; screening (REST + form); token-exchange
  endpoint + `external_identity` + `V3__federated_identity.sql`. Sequenced after the standalone core.
- **Phase 12 — Claude plugin + MCP harness** ([plans/phase-12-plugin-and-mcp-harness.md](plans/phase-12-plugin-and-mcp-harness.md)):
  read/build/validate tools buildable on the engine; submit/track need Phases 6/7/9. Has 4 open decisions.

## Open external inputs (gate go-live, not the build) — via [field-acquisition-checklist.md](field-acquisition-checklist.md)
Per-tenant B2B URLs + credentials · full UAE lookup exports · UAE Business Rejection Rules (BRRs) ·
`rentity_id` · CSV import template · AWS account specifics. *Needs UAT access (SACM registration is gated to
a real regulated entity).*

## Known gaps in current code (pre-migration)
No Emirates-ID/passport validation (awaits BRRs) · no XSD gate yet (built in X.3) · ANALYST/MLRO not yet
enforced (no submit endpoint until Phase 7) · `countries`/`funds` lookups loaded but unused · AWS SDK +
WireMock + AMQP deps not yet in `build.gradle` (added per phase) · engine not yet wired to any HTTP endpoint.

## To start implementation
Confirm the 3 XSD-first mechanical decisions above → I begin X.2 (codegen) → X.3 validates your 2 real
samples as the first proof. ⚠️ Anonymize the real-PII sample XMLs before any `git push`.
