# Phase 11.3 — CsvImporter (row → DpmsrCreateRequest → ReportService.create)

> **Status: ✅ DONE (2026-06-06).**
> Part of [../plans/phase-11-ingestion.md](../plans/phase-11-ingestion.md). Third step of Phase 11.

---

## 1. Goal & why
The second importer: a **flat DPMSR CSV** (one row = one report) for bulk hand-off, mapping each row to the
existing `DpmsrCreateRequest` and reusing `ReportService.create` so there is **no parallel build/validate/
persist path**. Per-row isolation; whole-file rejections fail fast. No REST/job yet (11.4).

## 2. What was built

| File | Role |
|---|---|
| `service/ingestion/IngestionExceptions` | `ImportRejectedException` (400) — whole-file rejection (unreadable, missing required headers, over the row cap). |
| `service/ingestion/CsvImporter` | `importCsv(bytes, tenantId, actor)`: Commons-CSV parse (header-addressed) → per row map to `DpmsrCreateRequest` → `ReportService.create` → collect `ImportRowResult`; isolate per-row failures. |

## 3. Key understanding / decisions
- **Reuse, don't fork (D-reuse).** Rows go through `ReportService.create`, inheriting build + validate +
  idempotency + audit unchanged. The CSV layer only parses + maps + isolates.
- **Whole-file vs per-row split.** Structural problems (can't parse, missing **required headers**, over
  `goaml.ingestion.max-rows`) throw `ImportRejectedException` **before any report is created** — atomic
  rejection. Per-row problems (bad number/date, missing required cell, validation `INVALID`, duplicate)
  become a `FAILED`/`INVALID` `ImportRowResult` and the batch continues.
- **Row cap enforced on parsed count.** `parser.getRecords()` is read fully (bounded by the 10 MB multipart
  limit), the size checked against the cap before any `create` — so an over-cap file creates **zero**
  reports.
- **Header-name-driven mapping** (the template is provisional, §3 of the plan): required headers are
  asserted up front; optional cells via `rec.isMapped/isSet` → null. A layout sign-off change is a mapping
  tweak.
- **Lenient dates** — `submission_date`/`person_birthdate` accept a full ISO offset/datetime **or** a plain
  `yyyy-MM-dd` (→ start-of-day UTC), so spreadsheets exporting bare dates work.
- **Flat template limits (by design).** One counterparty (PERSON **or** ENTITY via `party_type`) + one
  primary good per row; richer/nested reports use the JSON API or XML. Mapping leaves unmodelled bits
  (location, multi-party, directors, identifications) null.

## 4. Tests
`CsvImporterTest` (7, mocked `ReportService`): two good PERSON rows → two `VALID` results (create×2);
PERSON-row field mapping (ref/fiu/indicators-split/reportingPerson/party.person/goods/decimal value);
ENTITY-row maps the entity party; a bad `good_estimated_value` row → `FAILED` (and `create` **not** reached
for it) while the next row still creates; duplicate → `FAILED`; missing required header → `ImportRejected`;
over-cap (`maxRows=1`, 2 rows) → `ImportRejected`, zero creates.

## 5. Verification
`./gradlew test --tests CsvImporterTest` → **BUILD SUCCESSFUL**; 7 tests pass. `git status` scoped to Phase
11.3 files.

---

## Outcome
✅ CSV import maps + reuses the create path with per-row isolation + fail-fast file rejection. Next:
**11.4** — `IngestionService` orchestration (tally → persist `import_job` → audit) + `POST/GET
/api/v1/imports` REST + E2E + docs.
