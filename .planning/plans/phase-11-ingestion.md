# Phase 11 — `ingestion/` (inbound file import: goAML XML + DPMSR CSV)

> **Status: 🔲 PROPOSED (2026-06-06) — awaiting your approval before implementation.**
> Roadmap **Phase 11**. Adds bulk/file **intake** alongside the existing single-report JSON API: import a
> **goAML XML** file (re-import / round-trip / resubmit) and a **flat DPMSR CSV** (one row → one report),
> each producing a persisted **`import_job`** with **row-level results**. Reuses the whole engine
> (`unmarshal` + validators + builder) and the existing `ReportService`. This is the **standalone** import
> surface; the RabbitMQ-accounting + screening-REST intake remains **Phase 1.5**.

---

## 1. What this phase is, and why

Today a report is created one at a time via `POST /api/v1/reports` (JSON → engine → persist). Two real
intake needs aren't covered: (a) a compliance officer holding a **goAML XML** file (produced elsewhere, or
exported earlier) wants to **re-import** it — view it, re-validate it, resubmit it; and (b) a dealer with
**many cash sales** wants to upload a **spreadsheet** rather than hand-key each report. Phase 11 adds an
`ingestion/` package with two importers behind a uniform **`import_job`** record that carries **per-row
results** (status, entity_reference, report id, errors), so a partial batch reports exactly which rows
failed and why — without aborting the good ones.

**Scope (your decisions this session):**
- **Synchronous + persisted `import_job`** — the upload is processed in-request; an `import_job` row + a
  per-row results array are persisted (durable record + row-level error reporting) and returned
  immediately. A `GET /api/v1/imports/{id}` re-reads results. Bounded by a max-row cap. **No async infra**
  (queue/poll deferred to Phase 14 if very large files ever need it).
- **goAML XML + a constrained DPMSR CSV** — XML import reuses `ReportMarshaller.unmarshal` + the XSD/rules
  validators; the CSV is a **flat one-row-per-report** template (single counterparty + one primary good —
  the common cash-sale case), **specified below and flagged for stakeholder sign-off**. Richer/nested
  reports continue to use the JSON API or XML.

## 2. Decisions

| # | Decision | Choice |
|---|----------|--------|
| D1 | Processing model | **Synchronous**, in-request. Persist an `import_job` (one per upload) with a JSONB **results array** (one entry per row/report). Return the job; `GET /api/v1/imports/{id}` reads it. A configurable **max-row cap** (`goaml.ingestion.max-rows`, default 500) rejects oversized CSVs up front. |
| D2 | XML import path | `GoamlXmlImporter`: `ReportMarshaller.unmarshal(bytes)` → re-`marshal` to canonical XML → `XsdSchemaValidator.validate(xml)` + `ReportValidator.validate(tree, jurisdiction)` → persist a `report` row (status `VALID`/`INVALID`) exactly like `DefaultReportService` does. `entity_reference`/`report_code`/`rentity_id` are read from the unmarshalled tree. One XML file = one report (job with `total_rows=1`). |
| D3 | CSV import path | `CsvImporter` (Apache **Commons CSV**): parse → map each row to a **`DpmsrCreateRequest`** → call the existing **`ReportService.create(request, tenantId, actor)`** (reuses build + validate + persist + idempotency). One row = one report. |
| D4 | Per-row isolation | Each row is independent: a parse/mapping/validation/duplicate failure on row *N* records a row **result** (`status=FAILED`, errors) and **continues**. The job ends `COMPLETED` (all ok), `PARTIAL` (some failed), or `FAILED` (none succeeded / file unreadable). Mirrors the poller's per-item isolation. |
| D5 | Idempotency | Reuse `entity_reference` uniqueness. A duplicate within the file or against existing data → that row's result is `FAILED` ("duplicate entity_reference"), others proceed. (Matches `DuplicateEntityReferenceException`.) |
| D6 | Reports are first-class | Imported reports are normal `report` rows — listable, viewable, validatable, and **submittable** through the existing lifecycle. XML-imported rows store the canonical XML in `report_xml`; their `input` JSONB holds a small source marker (`{"source":"GOAML_XML_IMPORT","filename":…}`) since there's no `DpmsrCreateRequest` behind them. |
| D7 | Data model | New per-tenant **`import_job`** table (resolved via `search_path`, like `report`): `id`, `source_type` (`GOAML_XML`/`DPMSR_CSV`), `filename`, `status`, `total_rows`, `succeeded`, `failed`, `results` JSONB, `created_by`, `created_at`. Row results live in the JSONB array (no second table — sync processing fills it once). |
| D8 | REST + RBAC | `POST /api/v1/imports/xml` (multipart goAML XML), `POST /api/v1/imports/csv` (multipart CSV), `GET /api/v1/imports` (list), `GET /api/v1/imports/{id}` (job + results). **ANALYST or MLRO** (same as report create); tenant + actor from `UserPrincipal`. |
| D9 | Limits | Add explicit Spring multipart limits (`spring.servlet.multipart.max-file-size`/`max-request-size`) so XML/CSV uploads aren't capped at the 1 MB default; CSV row count bounded by D1's cap. No virus scanning (deferred with Phase 8's AV item). |
| D10 | Audit | Each import is audited (`REPORT.IMPORT`, source + counts) via the existing `AuditService`; per-report creates already audit `REPORT.CREATE`. |

## 3. The DPMSR CSV template (v1 — flagged for sign-off)

**One row = one report.** A single counterparty (PERSON **or** ENTITY) + one primary good — the common
cash-sale shape. Columns (header row required; `;`-separated lists where noted). Nested/multi-party or
multi-good reports use the JSON API or XML instead.

```
entity_reference, submission_date, fiu_ref_number, reason, action, indicators(;),
reporting_person_first_name, reporting_person_last_name,
party_type(PERSON|ENTITY), party_reason,
  person_first_name, person_last_name, person_birthdate, person_country_of_birth, person_nationality,
  person_id_number,
  entity_name, entity_incorporation_number, entity_incorporation_country,
good_item_type, good_description, good_estimated_value, good_currency_code, good_status_code
```

Mapping rules + which columns are required-per-`party_type` are specified in step 11.3's doc and the
`docs/` CSV reference. **This template is provisional pending FIU/stakeholder confirmation** (docs/09 open
item #5) — the importer is column-name driven so the layout can change without code churn.

## 4. Step breakdown (one commit per step, each green; per-step doc in `steps/`)

### Step 11.1 — `import_job` store + config + CSV dep
- **`db/migration/tenant/V5__import_jobs.sql`** — the `import_job` table (D7) + index on `(created_at)`.
- **`model/entity/ingestion/ImportJob`** (per-tenant, no `@Table` schema) + a `results` JSONB mapped via
  `@JdbcTypeCode(SqlTypes.JSON)` to a `List<ImportRowResult>` record.
- **`repository/ingestion/ImportJobRepository`** — `findByIdAnd… ` / `findAllByOrderByCreatedAtDesc`.
- **`config/ingestion/IngestionProperties`** (`goaml.ingestion.max-rows`) + `application.yml`
  (`goaml.ingestion.*` + `spring.servlet.multipart.*` limits, D9).
- **`build.gradle`** — `implementation 'org.apache.commons:commons-csv:<ver>'`; add
  `com/vyttah/goaml/service/ingestion/**` to the JaCoCo `coveredPackages`.
- **Tests:** Testcontainers persistence (job + JSONB results round-trip); properties bind.

### Step 11.2 — GoamlXmlImporter (unmarshal → validate → persist)
- **`service/ingestion/GoamlXmlImporter`** — D2: unmarshal, re-marshal canonical, XSD + rules validate,
  persist a `report` row (duplicate-checked), return an `ImportRowResult`. Malformed XML → a typed
  `ImportException`/row failure (never a 500).
- **Tests:** unit (mocked marshaller/validators/repo): valid XML → `VALID` report + success result; XSD
  errors → `INVALID` report + messages; un-parseable XML → row failure; duplicate `entity_reference` → row
  failure. **Round-trip IT** (Testcontainers): build a DPMSR report via the engine, export its XML, import
  it back → a matching persisted report.

### Step 11.3 — CsvImporter (row → DpmsrCreateRequest → ReportService.create)
- **`service/ingestion/CsvImporter`** — D3/D4: parse with Commons CSV (header-addressed columns), map each
  row to `DpmsrCreateRequest` (`party_type` switch; `;`-split indicators), call `ReportService.create`,
  collect a per-row `ImportRowResult`; isolate per-row failures (bad number, missing required column,
  validation `INVALID`, duplicate). Enforce the max-row cap (D1).
- **Tests:** unit (mocked `ReportService`): two good rows → two reports; a malformed row (bad
  `good_estimated_value`) → that row fails, the other still created; missing required column → row failure;
  duplicate → row failure; over-cap file → rejected. Mapping tests for PERSON vs ENTITY rows.

### Step 11.4 — Ingestion service + REST + integration + docs
- **`service/ingestion/IngestionService` + `DefaultIngestionService`** — orchestrates: build an
  `ImportJob`, run the right importer, tally `succeeded`/`failed`/`status`, persist, audit (D10), return.
- **`controller/ingestion/ImportController`** (D8) + **`model/dto/ingestion/ImportJobView`** (+ row views);
  **`service/ingestion/IngestionExceptions`** (e.g. `ImportJobNotFoundException` 404, `ImportRejectedException`
  400 for unreadable/oversized files); `GlobalExceptionHandler` entries.
- **Integration/E2E** (Testcontainers, `@SpringBootTest`): upload a small CSV (one good row + one bad row)
  → job `PARTIAL`, one report created + listable, the bad row's error present; upload a goAML XML → one
  report created; `GET /api/v1/imports/{id}` returns the results.
- **Docs/planning:** `docs/02` (`ingestion/` ✅), `docs/03`/`docs/04` (import flow + CSV template
  reference), `docs/09`/`ROADMAP`/`STATE`/`CLAUDE.md` (Phase 11 ✅, Phase 12 next, 11/14); fill this plan's
  outcome + per-step docs `steps/PHASE-11.1..11.4`; close docs/09 open item #5 (CSV template proposed).

## 5. JaCoCo / coverage
`com/vyttah/goaml/service/ingestion/**` to `coveredPackages` at the same **≥90% instruction / ≥80% branch**
bar. Deterministic unit tests (mocked engine/`ReportService`) carry coverage; the Testcontainers
round-trip + E2E tests prove the wiring.

## 6. What this phase does NOT do
The Phase 1.5 intake (RabbitMQ accounting consumer + reportability detection + screening REST push);
non-DPMSR report types (XML import is type-generic via the schema, but the **CSV** is DPMSR-only this
phase); true async/queued processing + progress polling (Phase 14 if needed); a multi-party/multi-good CSV
(use JSON/XML); virus scanning of uploads; an `update`/upsert mode (duplicates are row errors, not
overwrites); the React import UI (Phase 13 consumes these endpoints).

## 7. Verification
`docker compose up -d postgres localstack redis` → `./gradlew test jacocoTestCoverageVerification` green;
the round-trip IT proves XML export→import fidelity; the E2E proves a `PARTIAL` CSV (good + bad row)
creates exactly the good report and reports the bad row; per-row failures never abort the batch; imported
reports are submittable through the existing lifecycle; `git status` scoped to Phase 11 dirs + the 11.4 docs.

## 8. Notes / to confirm in-step
- **XML `entity_reference` extraction:** confirm the exact field on the unmarshalled tree
  (`rentity_reference` vs report-level reference) in 11.2 and reuse the duplicate check against it.
- **CSV template is provisional** — column-name-driven mapping so a sign-off change is a doc + mapping
  tweak, not a rewrite. Surface the assumed columns in the 11.3 doc for review.
- **Per-row isolation is load-bearing** — one bad row must never fail the job; mirror the poller's
  try/catch-per-item discipline and always record a result.
- **Multipart limits** — set them explicitly (D9) or large valid uploads 500 with an opaque error.
- **Reuse, don't fork** — the CSV path goes through `ReportService.create` (not a parallel persist) so
  build/validate/idempotency/audit stay single-sourced; only the XML path persists directly (the tree is
  already built) and must mirror `DefaultReportService`'s persistence exactly.

---

## Outcome — ✅ COMPLETE (2026-06-06)

Delivered across `dd9b54a` (11.1) · `b5bff6b` (11.2) · `28dc157` (11.3) · this commit (11.4):

- **11.1 store** — per-tenant `import_job` table (V5) + `ImportJob` entity (results JSONB as `String`) +
  repo; `IngestionProperties` (`max-rows`) + explicit `spring.servlet.multipart.*` limits; `commons-csv`.
- **11.2 XML** — `GoamlXmlImporter`: unmarshal → re-marshal canonical → XSD + rules validate → persist a
  report exactly like `DefaultReportService`; never throws for bad input. Round-trip IT on the golden DPMSR.
- **11.3 CSV** — `CsvImporter`: flat one-row-per-report template → `DpmsrCreateRequest` →
  `ReportService.create` (reuse, no fork); per-row isolation; fail-fast whole-file rejection.
- **11.4 service+REST** — `IngestionService` (tally → persist `import_job` → audit) + `ImportController`
  (`POST/GET /api/v1/imports`) + `ImportJobView`; MockMvc E2E.

**Decisions realised:** **synchronous + persisted `import_job`** (D1 — row-level results in a JSONB array,
no async infra); **goAML XML + a flat DPMSR CSV** (D2/D3, template §3 flagged for sign-off); **per-row
isolation** (D4) + **reuse `ReportService.create`** (no parallel persist).

**Two findings worth keeping:**
- **MockMvc over `TestRestTemplate` for the E2E.** The socket-based template flaked non-deterministically
  (JJWT "type tag" parse errors = token corrupted under embedded-server load — the Phase 9.2 class of
  flake). MockMvc (in-process, full filter chain) made it deterministic and exposed a real bug ↓.
- **Latent audit `TenantContext` clobber, fixed.** `DefaultAuditService.record` cleared the thread's
  tenant in `finally`; harmless when `create` ended a request, but the CSV importer calls `create`
  (→ audit → clear) per row and then persists the job → the `import_job` write hit `public`. Fix: audit
  now **restores** the caller's prior tenant. Protects every create-then-more-tenant-work flow.

**Coverage:** `service/ingestion/**` held to the ≥90%/≥80% bar; full `./gradlew test
jacocoTestCoverageVerification` green.

**Not done (later phases / by design):** the Phase 1.5 RabbitMQ-accounting + screening-REST intake;
non-DPMSR CSV; true async/queued processing; multi-party/multi-good CSV; upload virus scanning; an
update/upsert mode (duplicates are row errors); the React import UI (Phase 13). **CSV template is
provisional pending FIU/stakeholder sign-off** (docs/09 open item #5).
