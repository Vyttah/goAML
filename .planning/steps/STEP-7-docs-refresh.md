# Step 7 — Refresh developer docs to the current codebase

> **Status: ✅ DONE (2026-06-04).**
> Part of [../plans/xsd-first-foundation.md](../plans/xsd-first-foundation.md) (step X.7). Final step of the
> XSD-first foundation. **Docs-only — no code changes, no test impact.**

---

## 1. What this step is, and why

`docs/` is the project's durable developer documentation (CLAUDE.md mandates committing it). Steps 1–6 + the
conventions refactor changed the codebase substantially, so several docs now describe code that **no longer
exists** and would mislead a new engineer or a fresh-clone resume:
- the **hand-modeled `domain/*`** (deleted in Step 4c) — docs still present it as the data model;
- the **old package layout** (`persistence/shared`, `web/auth`, top-level `tenant/`) — moved by the R1 refactor;
- **no XSD gate / no Lombok / no MapStruct** — all now present;
- the engine described against the hand model rather than the **xjc-generated** model.

Step 7 brings the docs back in line with the code (which is the source of truth), so the documentation is
trustworthy again. `docs/CONVENTIONS.md` (written this session) is already current and becomes the canonical
structure reference the others point to.

## 2. Doc-by-doc plan

| Doc | State | Action |
|---|---|---|
| `04-domain-model.md` | **stale (biggest)** — describes hand-modeled `domain/*` POJOs | **Rewrite:** the domain is **xjc-generated JAXB** from `goAMLSchema.xsd` 5.0.2 into `domain.generated.*` (emitted to `build/`, not committed); `.xjb` bindings (`sql_date → OffsetDateTime`; the `reportActivity` choice-slot rename from Step 4a); `domain/adapter/GoamlDateTimeAdapter`. Note `item_type`/`conduction_type` are Strings (enum size cap). |
| `05-engine.md` | partly stale | **Update:** engine builds/validates/marshals against the generated model; add the **XSD validation gate** (`XsdSchemaValidator`), the **lookup ⊆ XSD-enum invariant** (`LookupXsdConsistencyTest`), and the **DPMSR builder** (`DpmsrReportBuilder` + `DpmsrReportInput` record/fluent + `GoamlParties`/`GoamlWrappers` + `ValidatedReport`); goldens are now XSD-validated; 4 activity + 3 transaction golden types. |
| `02-system-architecture.md` | stale package map | **Update:** layer-first layout (`controller`/`service`/`repository`/`model/{entity,dto,mapper}`/`config/tenant`/`exception`) + product-core `domain`/`engine`; point to `CONVENTIONS.md`. |
| `03-tech-stack-and-local-dev.md` | stale stack | **Update:** add **Lombok**, **MapStruct** (`lombok-mapstruct-binding`), **bjornvester xjc** + JAXB 4; keep Flyway/Testcontainers. |
| `06-multitenancy-and-security.md` | stale package refs | **Update:** tenant infra now `config/tenant/*`; auth logic in `AuthService`/`DefaultAuthService` (thin controller); entities renamed (`AppUser`, `Tenant`, …, no `Entity` suffix). |
| `07-persistence-and-migrations.md` | stale package refs | **Update:** entities → `model/entity/<feature>/`, repos → `repository/<feature>/` (separated, no suffix); Flyway `db/migration/{shared,tenant}` unchanged. |
| `08-testing.md` | stale | **Update:** add the XSD-gate test, goldens-are-XSD-validated, `LookupXsdConsistencyTest`, `GeneratedModelRoundTripTest`, `DpmsrReportBuilderTest`; test tree mirrors main. |
| `09-build-order-and-roadmap.md` | status drift | **Update:** mark XSD-first Steps 1–6 + the conventions refactor done; cross-link `.planning/steps/`. |
| `11-glossary.md` | minor gaps | **Add:** xjc/generated model, XSD gate, `conduction_type`, `reportActivity` choice slot, DPMSR builder, lookup⊆XSD invariant. |
| `README.md` | index | **Add:** `CONVENTIONS.md` to the index; one-line "current state" note. |
| `00-implementation-plan.md` | historical | **Banner only:** "historical original plan — current structure/state live in `.planning/` + `docs/CONVENTIONS.md`." (Preserve as history; don't rewrite.) |
| `01-business-context.md` | mostly fine | **Light touch:** DPMSR is generic across DPMS dealers (any precious-metal/stone invoice), not gold-specific. |
| `CONVENTIONS.md` | **current** | none (written this session). |

## 3. Guardrails (so the refresh doesn't introduce new drift)

- **Code is the source of truth.** Every package path / class name / dependency I write gets grep-verified
  against `src/` and `build.gradle` before commit. No describing intent that isn't in the code.
- **Don't over-claim.** Where a thing is pending (the unbuilt 10 report types, UAT field acquisition, Phase 6+
  features), the docs say "planned", not "done".
- **No code touched** — `./gradlew` not required to pass anything new; I'll still confirm the build is green at
  the end since docs live in the same repo.

## 4. Files touched

`docs/{04,05,02,03,06,07,08,09,11,README,00,01}.md` (content per §2). No `src/`, no `build.gradle`, no tests.

## 5. How it's verified

- **Stale-reference grep is clean:** `grep -rE 'persistence/shared|web/auth|com\.vyttah\.goaml\.(web|persistence)\.|hand-model|<Name>Entity'` over `docs/` returns nothing (except where a doc is deliberately quoting history with a banner).
- **Spot cross-checks:** package paths and class names in the rewritten 04/05/02 exist under `src/` (grep).
- A reader following `docs/` would find the code where the docs say it is.

## 6. What this step does NOT do

No new features, no code/test changes, no roadmap re-planning. It only makes the existing docs accurate. After
Step 7 the **XSD-first foundation is complete** and the functional roadmap resumes at **Phase 6** (AWS + B2B
client) — or **Phase 1.5** (accounting/screening integration) per priority.

---

## Outcome

All 12 docs refreshed against the live codebase (verified, not guessed):

- **`04-domain-model.md`** — fully rewritten: domain is now **xjc-generated** from `goAMLSchema.xsd` 5.0.2
  into `domain.generated.*` (in `build/`, not committed); documents the `bjornvester` xjc plugin config, the
  `.xjb` `sql_date → OffsetDateTime` binding, the **`reportActivity` choice-slot** rename (and that
  `getReportActivity()` is THE accessor; `getActivity()` is vestigial), enums-vs-Strings (`ReportType`,
  `CurrencyType` enums; `item_type`/`conduction_type` as Strings), wrapper-per-owner.
- **`05-engine.md`** — rewritten onto the generated model: the **two gates** (`ReportValidator` rules +
  **`XsdSchemaValidator`** XSD), the **DPMSR convenience builder** (`DpmsrReportInput` record + fluent,
  `GoamlParties`, `GoamlWrappers`, `ValidatedReport`), `ReportType`-keyed constants, `getReportActivity()`,
  the reconciled lookups + the **lookup ⊆ XSD invariant** (`LookupXsdConsistencyTest`), marshaller via
  `ObjectFactory`.
- **`02`** — layer-first package map (`controller`/`service`/`repository`/`model/{entity,dto,mapper}`/
  `config/tenant`/`exception` + product-core `domain`/`engine`); points to `CONVENTIONS.md`.
- **`03`** — added Lombok, MapStruct (+ `lombok-mapstruct-binding`), the bjornvester xjc plugin + generated
  sources; updated coding conventions (layer-first, interface+`Default*`, no `Entity` suffix).
- **`06`** — `config/tenant/*`; thin `controller/auth/AuthController` → `service/auth/AuthService`;
  `exception/GlobalExceptionHandler`; entities renamed (`AuditLog`, no suffix).
- **`07`** — entities → `model/entity/<feature>/` (no suffix), repos → `repository/<feature>/`, accessed via
  services; MapStruct note.
- **`08`** — test tree mirrors main; goldens are **XSD-validated**; added `GoamlXsdValidationTest`,
  `GeneratedModelRoundTripTest`/`GeneratedModelTest`, `LookupXsdConsistencyTest`, `DpmsrReportBuilderTest`,
  `TenantMapperTest`; corrected the STR (`ELCFT`/`transmode_comment`) and DPMSR (`<item>`-wrapped goods) XML
  snippets to the actual goldens.
- **`09`** — added the XSD-first migration + refactor as done; open-item #1 marked integrated; dropped the
  "no XSD gate yet" gap.
- **`11`** — schema 5.0.2; added xjc, generated model, `reportActivity`, XSD gate, lookup⊆XSD,
  `conduction_type`, DPMSR builder, Lombok, MapStruct.
- **`README`** — status note, `CONVENTIONS.md` in the index, schema-version fix.
- **`00`** — "historical" banner (preserved as intent; not rewritten).
- **`01`** — schema 5.0.2; DPMSR is generic across DPMS dealers; the XSD is now in-repo (caveat updated).
- **`10`** (B2B protocol) — untouched: unaffected by the structural/migration changes.

### Verified
- **Stale-reference grep clean:** the only remaining `hand-model`/`*Entity`/`v4.0` hits are in the
  deliberately-historical `00` (under its banner), text describing the migration itself, real generated
  class names (`TEntity`, `TEntity.Phones`, `EntityPersonRoleType`), or legitimate `CONVENTIONS.md` terms
  (`BaseEntity`, the `@Entity` annotation).
- **Spot cross-checks:** every cited path/class (`GoamlParties`, `GoamlWrappers`, `ValidatedReport`,
  `XsdSchemaValidator`, `AuthService`, `GlobalExceptionHandler`, `model/entity/audit/AuditLog`,
  `model/mapper/tenant/TenantMapper`, `goaml-bindings.xjb`, …) exists under `src/`.
- **Docs-only:** `git status` shows only `docs/*.md` modified (+ this STEP doc). No `src/`, `build.gradle`,
  or tests touched — the build is logically unaffected (suite not re-run for a docs-only change).

**XSD-first foundation complete.** The functional roadmap resumes at **Phase 6** (AWS + B2B client) or
**Phase 1.5** (accounting/screening integration) per priority.
