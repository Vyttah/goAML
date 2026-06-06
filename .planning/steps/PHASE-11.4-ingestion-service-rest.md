# Phase 11.4 — Ingestion service + REST + integration + docs

> **Status: ✅ DONE (2026-06-06).**
> Part of [../plans/phase-11-ingestion.md](../plans/phase-11-ingestion.md). Final step of Phase 11.

---

## 1. Goal & why
Tie the importers together behind a persisted `import_job`, expose them over REST, and prove the whole
chain end-to-end. After this, a user can upload a goAML XML or a DPMSR CSV and get back a job with row-level
results.

## 2. What was built

| File | Role |
|---|---|
| `service/ingestion/IngestionService` + `DefaultIngestionService` | Orchestrates: run the importer → tally `succeeded`/`failed` → derive `status` (COMPLETED/PARTIAL/FAILED) → persist `ImportJob` (results JSON) → audit (`REPORT.IMPORT`). `get`/`list`. CSV `ImportRejectedException` propagates (no job persisted). |
| `service/ingestion/IngestionExceptions` | `+ ImportJobNotFoundException` (404). |
| `controller/ingestion/ImportController` | `POST /api/v1/imports/{xml,csv}` (multipart, ANALYST/MLRO), `GET /api/v1/imports` + `/{id}` (also TENANT_ADMIN); empty/unreadable upload → `ImportRejectedException`. |
| `model/dto/ingestion/ImportJobView` | API view incl. the deserialized `List<ImportRowResult>`. |
| `exception/GlobalExceptionHandler` | `ImportJobNotFound` → 404, `ImportRejected` → 400. |
| `service/audit/DefaultAuditService` | **Bug fix** (see §3): restore the caller's prior `TenantContext` instead of clearing it. |

## 3. Key understanding / decisions
- **Latent audit bug, surfaced by the CSV path + caught by switching the E2E to MockMvc.**
  `DefaultAuditService.record` did `TenantContext.set(...)` then **`clear()` in finally** — clobbering the
  request's bound tenant. That was harmless when `ReportService.create` was the last thing in a request,
  but the CSV importer calls `create` (→ audit → clear) per row and then `persistJob` does more
  tenant-scoped work → the `import_job` insert resolved to `public` ("relation does not exist"). **Fix:**
  audit now captures `TenantContext.get()` and **restores it** in finally (clears only if none was set) —
  a general fix protecting any "create-then-more-tenant-work" flow. The XML path never tripped it (no
  `create`/audit), which is why it passed while CSV failed.
- **E2E via MockMvc, not `TestRestTemplate`.** With `RANDOM_PORT` + `TestRestTemplate` the failure was
  *non-deterministic* (different method each run, server-side JJWT "type tag" parse errors = token
  corrupted over an unstable embedded-socket under load — the Phase 9.2 flake). Switching to
  `@AutoConfigureMockMvc` (in-process, no socket) made it **deterministic**, exposing the real audit bug.
  MockMvc still runs the full filter chain (JwtAuthFilter + tenant routing), so coverage is equivalent.
- **Job status derivation:** `succeeded = rows that created a report` (VALID **or** INVALID), `failed =
  FAILED rows`; `COMPLETED` (no failures) / `PARTIAL` (some) / `FAILED` (none created). INVALID reports
  count as created (consistent with the JSON create path) — their messages ride in the row result.
- **File-level rejection skips the job row** — `ImportRejectedException` (unreadable/missing-headers/
  over-cap, or empty upload) propagates to a 400 with **no** `import_job` persisted (atomic).

## 4. Tests
- **`DefaultIngestionServiceTest`** (6, mocked importers/repo/audit): XML created → COMPLETED; XML failed →
  FAILED; CSV mixed → PARTIAL with serialized results; CSV rejection propagates (no save, no audit);
  get-missing → `ImportJobNotFound`; list delegates.
- **`ImportApiE2ETest`** (MockMvc + Testcontainers): CSV (good + bad row) → 201 `PARTIAL`, one report
  (`CSV-REF-1`, not `CSV-REF-2`), job re-fetchable; golden XML → 201 `COMPLETED`, report `DPMSR-2026-001`;
  empty upload → 400. Deterministically green across repeated runs.

## 5. Verification
`docker compose up -d postgres localstack redis` → `./gradlew test jacocoTestCoverageVerification` →
**BUILD SUCCESSFUL** (full suite + gate). `git status` scoped to Phase 11.4 files + the 11.4 docs.

---

## Outcome
✅ Phase 11 complete — file import (goAML XML + flat DPMSR CSV) lands as a persisted `import_job` with
row-level results, over REST, reusing the engine + `ReportService`. Also fixed a latent audit
`TenantContext` clobber. Next: **Phase 12 — Claude plugin & MCP harness + `cli/`**.
