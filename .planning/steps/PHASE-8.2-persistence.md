# Phase 8.2 — Attachment persistence (tenant table + entity + repo)

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-8-s3-attachments.md](../plans/phase-8-s3-attachments.md). Second step of Phase 8.

---

## 1. Goal & why

The S3 seam (8.1) moves bytes; Step 8.2 records the **metadata** that ties bytes to a report. A new
tenant-scoped `attachment` table + JPA entity + repository, so the service (8.3) can persist one row per
uploaded document and the submit path can list them to pull from S3.

## 2. What was built

| File | Role |
|---|---|
| `db/migration/tenant/V3__attachments.sql` | `attachment` table: `id` PK, `report_id` FK→`report` **ON DELETE CASCADE**, `filename`, `content_type`, `size_bytes` BIGINT, `s3_key` (1024), `uploaded_by`, `created_at`; index on `report_id`. Bytes are **not** stored — only the S3 key. |
| `model/entity/attachment/Attachment` | Tenant-scoped JPA entity (no `@Table` schema); `@PrePersist` stamps `created_at`. |
| `repository/attachment/AttachmentRepository` | `findByReportIdOrderByCreatedAt`, `findByIdAndReportId(id, reportId)` (the second scopes a lookup to its report — used by remove). |

## 3. Key understanding / decisions

- **Name-clash handled by package + Javadoc.** The JPA `model.entity.attachment.Attachment` (aggregate,
  metadata only) is distinct from the engine value record `engine.packaging.Attachment` (carries bytes for
  the ZIP). They live in different packages; the submission service (8.3) will reference both by FQN/alias
  at the one call site — same approach as the Phase 7 `Report` vs JAXB-`Report` clash.
- **ON DELETE CASCADE at the DB**, mirroring `submission` — deleting a report removes its attachments in one
  statement (proven by the cascade test). (Orphaned S3 objects are handled in the service layer, 8.3.)
- **`s3_key` is `VARCHAR(1024)`** — the per-tenant/per-report prefix
  (`tenants/{tenantId}/reports/{reportId}/{attachmentId}-{filename}`) is long; 1024 leaves headroom.
- **`size_bytes` persisted** so list/UI can show sizes and the submit path can pre-check the total against
  `PackagingLimits` before fetching bytes.
- **`findByIdAndReportId`** (not just `findById`) so an attachment can only ever be resolved within its
  parent report — a small defence-in-depth on top of tenant `search_path` isolation.

## 4. Tests

- **`AttachmentPersistenceTest`** (Testcontainers, 2 tests):
  - round-trip — save a report + attachment in a tenant schema, read back metadata, `findByReportId…`
    returns it, `findByIdAndReportId` matches for the right report and is empty for a wrong one;
  - cascade — deleting the parent report removes the attachment row.

## 5. Verification

`./gradlew test jacocoTestCoverageVerification` → **BUILD SUCCESSFUL** (the two new tests pass; full suite
green; coverage gate holds — no production logic added yet, only the entity/repo). `git status` scoped to
Phase 8.2 files.

---

## Outcome
✅ The `attachment` table + entity + repo exist and round-trip, with FK cascade proven. Next: **8.3** — the
`AttachmentService` (add/list/remove, status-gated, S3-backed) + wiring `DefaultSubmissionService.submit()`
to pull attachment bytes into the ZIP.
