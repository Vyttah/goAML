# Phase 8.4 — Web (REST) for attachments

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-8-s3-attachments.md](../plans/phase-8-s3-attachments.md). Fourth step of Phase 8.

---

## 1. Goal & why

Expose the attachment lifecycle (8.3) over HTTP so a user/UI can upload, list, and remove supporting
documents, and prove end-to-end that submit bundles them. After this step Phase 8 is functionally complete.

## 2. What was built

| File | Role |
|---|---|
| `controller/report/AttachmentController` | `POST /api/v1/reports/{reportId}/attachments` (multipart `file`, 201), `GET …/attachments` (list), `DELETE …/attachments/{attachmentId}` (204). Thin — delegates to `AttachmentService`; tenant + actor from `UserPrincipal`. |
| `model/dto/attachment/AttachmentView` | Response DTO (metadata only) + `from(Attachment)`. |
| `exception/GlobalExceptionHandler` | Mapped the new exceptions: `AttachmentNotFoundException`→404, `ReportNotEditableException`→409, `AttachmentRejectedException`→400, `SubmissionPackagingException`→422. |

## 3. Key understanding / decisions

- **Proxy-through-API upload (D1).** The file is a `multipart/form-data` part read into `byte[]` in the
  controller and handed to the service — one trust boundary, validation server-side before S3. No
  presigned URLs.
- **RBAC:** add/remove = `ANALYST` or `MLRO` (same as report create); list also allows `TENANT_ADMIN`.
  Submit stays MLRO-only (unchanged from Phase 7).
- **Frozen-after-submit shows through the API:** once the report is `SUBMITTED`, remove returns **409**
  (proven in the E2E) — the service editable-gate surfaces as a clean HTTP status.
- **`IOException` reading the part** → `AttachmentRejectedException` (400), so a broken upload is a client
  error, not a 500.
- **Packaging-too-large → 422** (report stays `VALID`), distinct from a FIU rejection (also 422 but with
  the FIU error body) and from a per-file upload rejection (400).

## 4. Tests

- **`AttachmentApiE2ETest`** (`@SpringBootTest` RANDOM_PORT + Testcontainers; `@MockBean` on both
  `GoamlB2bClient` and `S3StorageClient` for determinism — the real S3 path is `S3StorageClientIT`):
  - `attachListSubmitWithAttachmentThenRemove` — create → multipart attach (201, S3 `put` verified) →
    list contains it → submit (200, S3 `fetch` verified — the bytes were pulled into the ZIP) → remove on
    the now-submitted report → **409** (frozen);
  - `rejectsDisallowedExtension` — `malware.exe` → **400**;
  - `removeOnDraftDeletesFromS3AndRepo` — attach then remove on an editable report → **204**, S3 `delete`
    verified, gone from the list.

## 5. Verification

`docker compose up -d postgres localstack redis` → `./gradlew test jacocoTestCoverageVerification` →
**BUILD SUCCESSFUL**; the 3 E2E scenarios pass; the coverage gate (now including
`controller/report/**` with the new controller + `service/attachment/**`) holds. `git status` scoped to
Phase 8.4 files.

---

## Outcome
✅ Attachments are reachable over REST end-to-end and the submission ZIP carries them, E2E-proven. Phase 8
is functionally complete. Next: **8.5** — docs + planning sync (Phase 8 ✅, Phase 9 next), with the
AV-scanning gap noted.
