# Phase 8.3 — Attachment service + submit wiring

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-8-s3-attachments.md](../plans/phase-8-s3-attachments.md). Third step of Phase 8 —
> the orchestration heart.

---

## 1. Goal & why

Tie the S3 seam (8.1) and the metadata table (8.2) into a usable lifecycle: validate + store + remove
attachments, and make the existing submit path actually **bundle** them. After this step the only thing
missing is the HTTP surface (8.4).

## 2. What was built

| File | Role |
|---|---|
| `service/attachment/AttachmentService` (interface) | `add(reportId, tenantId, actor, filename, contentType, bytes)`, `list(reportId)`, `remove(reportId, attachmentId, actor)`. |
| `service/attachment/DefaultAttachmentService` | Validates → `S3StorageClient.put` → persist row → audit; list; remove (`S3StorageClient.delete` → delete row → audit). Status-gated. |
| `service/attachment/AttachmentExceptions` | `AttachmentNotFoundException` (404), `ReportNotEditableException` (409), `AttachmentRejectedException` (400). |
| `service/submission/SubmissionExceptions` | Added `SubmissionPackagingException` (422) — report + attachments exceed the ZIP limits. |
| `service/submission/DefaultSubmissionService` | **Wired:** `loadAttachments(reportId)` pulls each row's bytes from S3 into an engine `Attachment`; `packager.zip(...)` now gets the real list (was `List.of()`); `PackagingException`→`SubmissionPackagingException`, `S3AccessException`→`SubmissionTransportException`. Two new constructor deps: `AttachmentRepository`, `S3StorageClient`. |

## 3. Key understanding / decisions

- **Order = S3 first, then the row.** `add` puts to S3 *before* saving the metadata row; `remove` deletes
  the object *before* the row. A failure between the two leaves at worst a harmless orphaned S3 object —
  never a DB row pointing at missing bytes. (A reconcile sweep is out of scope.)
- **Validate at upload AND at submit.** `add` mirrors the packager's per-file rules (blank/empty,
  ≤ 5 MB, allowed extension) against `PackagingLimits.UAE_DEFAULT` to fail fast; the packager stays the
  single source of truth and re-enforces **count + total ≤ 20 MB** at submit. A per-file overflow that
  somehow reaches submit (or a too-large *total*) surfaces as `SubmissionPackagingException` and the report
  **stays `VALID`** (the fix is to drop an attachment) — distinct from a FIU rejection.
- **Editable gate.** Attachments are mutable only while the report is `DRAFT`/`VALID`/`INVALID`; once
  `SUBMITTED`/`ACCEPTED`/`REJECTED`/`FAILED` add+remove both → 409. Frozen at submission.
- **Name clash stays clean.** `DefaultSubmissionService.loadAttachments` imports the engine
  `engine.packaging.Attachment` and lets the lambda var (the JPA entity) stay inferred — both `Attachment`
  types coexist with no FQN noise, as planned.
- **S3 key:** `tenants/{tenantId}/reports/{reportId}/{attachmentId}-{filename}`, with `/` and `\` in the
  filename replaced by `_` so a crafted filename can't nest keys. Original filename kept in the DB + ZIP.
- **`findByIdAndReportId`** for remove — an attachment can only be resolved within its parent report.

## 4. Tests

- **`DefaultAttachmentServiceTest`** (9, Mockito): add success (S3 put + row + audit, key shape); add to
  missing report (404, no S3); add to submitted report (frozen, no S3); add rejects blank/empty/oversize/
  bad-extension/no-extension (no S3); list (+ missing report); remove (S3 delete + row delete + audit);
  remove missing attachment (404); remove on submitted report (frozen).
- **`DefaultSubmissionServiceTest`** extended: updated for the two new constructor deps;
  `submitPullsAttachmentsFromS3IntoTheZip` (captures the posted ZIP, **unzips it**, asserts it contains
  `PAY-1.xml` + `invoice.pdf`); `submitFailsWhenAttachmentsExceedPackagingLimits` (6 MB file →
  `SubmissionPackagingException`, `postReport` never called, report stays `VALID`, no submission saved).
  The 9 pre-existing submission tests still pass (empty attachment list = prior behaviour).

## 5. Verification

`./gradlew test jacocoTestCoverageVerification` → **BUILD SUCCESSFUL**. JaCoCo: `DefaultAttachmentService`
and `DefaultS3StorageClient` report **0 missed instructions** (100%); the gate (≥90%/≥80% on the gated
packages, now including `service/attachment/**`) holds. `git status` scoped to Phase 8.3 files.

---

## Outcome
✅ Attachments have a full service lifecycle and are pulled into the submission ZIP. Next: **8.4** — the
REST surface (`AttachmentController` multipart add/list/remove) + DTO + the `GlobalExceptionHandler`
mappings for the new exceptions, with a Spring E2E.
