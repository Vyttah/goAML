# Phase 11.1 — `import_job` store + config + CSV dependency

> **Status: ✅ DONE (2026-06-06).**
> Part of [../plans/phase-11-ingestion.md](../plans/phase-11-ingestion.md). First step of Phase 11.

---

## 1. Goal & why
Lay the substrate the importers need before any parsing logic: the per-tenant **`import_job`** record (the
durable import + row-level error report), the row cap config, explicit multipart limits, and the CSV
library. No importer yet (11.2–11.4).

## 2. What was built

| File | Role |
|---|---|
| `db/migration/tenant/V5__import_jobs.sql` | Per-tenant `import_job` table: `source_type`, `filename`, `status`, `total_rows`, `succeeded`, `failed`, `results` JSONB, `created_by`, `created_at`; index on `created_at`. |
| `model/entity/ingestion/ImportJob` | JPA entity (no `@Table` schema — tenant-resolved like `Report`); `results` JSONB mapped as `String` (the `Report` convention); setters for status/tallies/results. |
| `repository/ingestion/ImportJobRepository` | `findAllByOrderByCreatedAtDesc` (+ inherited `findById`). |
| `config/ingestion/IngestionProperties` + `IngestionConfig` | `goaml.ingestion.max-rows` (CSV row cap); `@EnableConfigurationProperties`. |
| `application.yml` | `goaml.ingestion.max-rows:500` + **`spring.servlet.multipart.{max-file-size:10MB, max-request-size:15MB}`** (D9 — raise the 1 MB default). |
| `build.gradle` | `+ org.apache.commons:commons-csv:1.11.0`; `+ com/vyttah/goaml/service/ingestion/**` to the JaCoCo gate. |

## 3. Key understanding / decisions
- **`results` JSONB stored as `String`, not a mapped `List`.** Follows the documented `Report` convention
  ("JSONB columns map as `String` via `JdbcTypeCode`; the service (de)serializes with Jackson") — so the
  ingestion service will Jackson-(de)serialize a `List<ImportRowResult>` in 11.4. Keeps persistence
  single-patterned.
- **One job row per upload, written once.** Synchronous processing (D1) means no intermediate states — the
  row lands with its final `status` (`COMPLETED`/`PARTIAL`/`FAILED`) + tallies + the full results array.
- **No second `import_job_row` table** — row results live in the JSONB array; simpler and sufficient for
  sync processing (a separate table would only matter for streaming/async).
- **`recipient`/`created_by` by id, no cross-schema FK** — same reasoning as `notification`/`audit_log`.
- **Multipart limits are explicit** so valid XML/CSV uploads aren't capped at Spring's 1 MB default; the
  real bounds are `PackagingLimits` (submit) + `max-rows` (CSV).
- **Commons CSV over opencsv** — lighter, RFC-4180, header-addressed access (`CSVFormat.builder().setHeader`).

## 4. Tests
- **`ImportJobPersistenceTest`** (Testcontainers): a `DPMSR_CSV` job with tallies + a two-entry `results`
  JSONB array round-trips; the JSONB survives (`REF-1` / `bad value` present); newest-first listing returns
  it.
- **`IngestionPropertiesTest`** (Binder): `max-rows` binds.

## 5. Verification
`./gradlew test --tests IngestionPropertiesTest --tests ImportJobPersistenceTest` → **BUILD SUCCESSFUL**;
`commons-csv:1.11.0` resolves. `git status` scoped to Phase 11.1 files.

---

## Outcome
✅ The import-job store + config + CSV dependency are in place (no importer yet). Next: **11.2** — the
`GoamlXmlImporter` (unmarshal → validate → persist), with a build→export→re-import round-trip IT.
