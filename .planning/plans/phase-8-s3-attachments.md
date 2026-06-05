# Phase 8 — S3 Attachments (supporting documents in the submission ZIP)

> **Status: 🔲 PROPOSED (2026-06-05) — awaiting your approval before implementation.**
> Roadmap **Phase 8**. Adds per-report **supporting-document attachments** stored in S3 and pulled into
> the submission ZIP. Builds on the engine packager (`ReportZipPackager` already accepts
> `List<Attachment>`), the Phase 6 AWS pattern (`integration/aws/` + `config/aws/`), and the Phase 7
> report lifecycle (`report`/`submission` + `ReportController` + `DefaultSubmissionService`).

---

## 1. What this phase is, and why

The engine can already pack attachments — `ReportZipPackager.zip(reportXml, filename, List<Attachment>,
PackagingLimits)` validates extension/size/count and builds the ZIP — but Phase 7 always passes
`List.of()`. A DPMSR (and every goAML report) routinely carries **supporting documents** (invoices,
KYC/ID scans, photos of the goods). Phase 8 makes those first-class:

1. **Attach** a file to a `DRAFT`/`VALID` report → validated (ext/size/type) → stored in **S3** under a
   per-tenant prefix → an `attachment` row records the metadata + S3 key.
2. **List / remove** a report's attachments (remove deletes the S3 object + the row).
3. **Submit** then **pulls the attachment bytes from S3** and hands them to `ReportZipPackager` so the FIU
   ZIP carries `report.xml` **plus** the documents — all enforced within `PackagingLimits.UAE_DEFAULT`
   (5 MB/file, 20 MB/ZIP, ≤50 files, allowed extensions).

**Scope (your decisions this session):**
- **Upload path = proxy through the API** (multipart POST → app validates → streams to S3 → writes row).
  One trust boundary, server-side validation before storage, straightforward LocalStack/E2E testing.
- **No virus/content scanning in Phase 8** — extension allowlist + per-file/total size + content-type
  only (the existing `PackagingLimits` rules). AV (ClamAV/scan hook) is **deferred** to a later hardening
  phase and noted as a known gap in docs.

## 2. Decisions

| # | Decision | Choice |
|---|----------|--------|
| D1 | Upload transport | **Proxy through the API** — `POST /api/v1/reports/{id}/attachments` multipart; the app validates, streams to S3, writes the `attachment` row. (Presigned-PUT rejected: two-step flow, validation can't run before storage, harder to test.) |
| D2 | Storage layout | **One bucket, per-tenant + per-report prefix:** `tenants/{tenantId}/reports/{reportId}/{attachmentId}-{filename}`. Bucket name from config (`goaml.aws.s3.bucket`). |
| D3 | Persistence | New **tenant** table `attachment` (migration `db/migration/tenant/V3__attachments.sql`): `id` UUID PK, `report_id` UUID FK→`report` ON DELETE CASCADE, `filename`, `content_type`, `size_bytes` BIGINT, `s3_key`, `uploaded_by` UUID, `created_at` TIMESTAMPTZ. Index on `report_id`. **No bytes in Postgres** — only metadata + the S3 key. |
| D4 | Validation timing | **Fail-fast at upload** (reject early): extension allowlist + per-file ≤ 5 MB + content-type, reusing `PackagingLimits.UAE_DEFAULT`. **Re-enforced at submit** by `ReportZipPackager` (count + total ≤ 20 MB) — the packager stays the single source of truth so a report that accreted too many files still can't be packaged. |
| D5 | Mutability gate | Attach/remove allowed only while report status ∈ {`DRAFT`, `VALID`, `INVALID`}. **Once `SUBMITTED`/`ACCEPTED`/`REJECTED` → 409** (attachments are frozen at submission). |
| D6 | Submit wiring | `DefaultSubmissionService.submit()` fetches each attachment row → `S3StorageClient.fetch(key)` → builds `List<Attachment>` → `packager.zip(xml, filename, attachments, UAE_DEFAULT)`. The empty `List.of()` at the current call site is replaced. Total-size overflow → the packager throws → mapped to a clear 4xx (report stays `VALID`, not submitted). |
| D7 | S3 client shape | Mirror the Phase 6 Secrets pattern: `integration/aws/S3StorageClient` (interface) + `DefaultS3StorageClient` (SDK v2 `S3Client`) + `S3AccessException`; the `S3Client` bean added to the existing `config/aws/AwsConfig` (same endpoint-override + LocalStack static-creds logic as `SecretsManagerClient`). |
| D8 | RBAC | attach/list/remove → **ANALYST or MLRO** (same as report create); list/read tenant-scoped. Submit stays **MLRO-only** (unchanged). |
| D9 | Scanning | **Deferred** — no AV engine in Phase 8 (documented gap). Validation = ext/size/content-type only. |

## 3. Step breakdown (one commit per step, each green; per-step doc in `steps/`)

### Step 8.1 — S3 client + AWS config + dependency
- **`build.gradle`**: add `software.amazon.awssdk:s3` (BOM `2.28.16` already pins the version); extend
  the JaCoCo `coveredPackages` only if a new package is introduced (S3 client lives in the existing
  `integration/aws/**`, already gated).
- **`integration/aws/S3StorageClient`** (interface): `void put(String key, byte[] bytes, String
  contentType)`, `byte[] fetch(String key)`, `void delete(String key)`.
- **`integration/aws/DefaultS3StorageClient`** (SDK v2 `S3Client`); all failures → `S3AccessException`.
- **`integration/aws/S3AccessException`**.
- **`config/aws/AwsConfig`**: add `@Bean S3Client s3Client(AwsProperties)` — reuse the endpoint-override +
  static-creds-for-LocalStack branch; `forcePathStyle(true)` for LocalStack compatibility.
- **`config/aws/AwsProperties`**: add an `s3` sub-record (`bucket`) → `goaml.aws.s3.bucket`.
- **`application.yml`**: add **only** `goaml.aws.s3.bucket: ${GOAML_S3_BUCKET:goaml-attachments}`.
- **Tests:** unit (Mockito over the SDK `S3Client`, all error paths → `S3AccessException`); a tagged
  `@Tag("localstack")` `S3StorageClientIT` (socket-reachability `assumeTrue` skip) doing a real
  put→fetch→delete round-trip against compose LocalStack.

### Step 8.2 — Persistence (attachment table + entity + repo)
- **Migration** `db/migration/tenant/V3__attachments.sql` (per D3) + index on `report_id`.
- **`model/entity/attachment/Attachment`** (JPA, no `Entity` suffix, table `attachment`).
  ⚠️ Name-clash note: the JPA `Attachment` ≠ the engine `engine.packaging.Attachment` record — keep them
  in distinct packages, reference by FQN/alias in the submission service where both appear.
- **`repository/attachment/AttachmentRepository`**: `findByReportIdOrderByCreatedAt`, `findByIdAndReportId`.
- **Tests:** Testcontainers — provision a tenant, assert `attachment` exists in the tenant schema,
  round-trip a row, and `ON DELETE CASCADE` removes attachments when a report is deleted.

### Step 8.3 — Service (attachment orchestration + submit wiring)
- **`service/attachment/AttachmentService`** (interface + `Default*`):
  `add(reportId, tenantId, actorUserId, filename, contentType, bytes)` → guard report status (D5) →
  validate ext/size/content-type against `UAE_DEFAULT` (D4) → `S3StorageClient.put` → persist row →
  return metadata; `list(reportId, tenantId)`; `remove(reportId, attachmentId, tenantId)` →
  `S3StorageClient.delete` + delete row (status-gated).
- **`DefaultSubmissionService.submit()`**: replace `List.of()` — load attachment rows, `S3StorageClient
  .fetch` each, build `List<engine.packaging.Attachment>`, pass to `packager.zip(...)` (D6). Packager
  total-size overflow → typed service outcome → 4xx (report stays `VALID`).
- **Audit:** `ATTACHMENT.ADD` / `ATTACHMENT.REMOVE` via the existing `AuditService`.
- **Tests:** unit (Mockito for `S3StorageClient` + repos) — add/list/remove, status-gate 409,
  oversize/bad-extension rejection, submit-with-attachments builds the ZIP, submit total-overflow path.

### Step 8.4 — Web (REST) + DTOs
- **`controller/report/AttachmentController`** (or methods on a thin attachment controller):
  `POST /api/v1/reports/{id}/attachments` (multipart, ANALYST/MLRO),
  `GET /api/v1/reports/{id}/attachments`,
  `DELETE /api/v1/reports/{id}/attachments/{attachmentId}`. Tenant + actor from
  `@AuthenticationPrincipal UserPrincipal`.
- **DTOs** (`model/dto/attachment/…`): `AttachmentView` (+ `from()` factory). Multipart via
  `MultipartFile`.
- **`GlobalExceptionHandler`**: map `S3AccessException` → 502; oversize/bad-type → 400; not-found → 404;
  status-gate → 409.
- **Tests:** `@SpringBootTest` + Testcontainers + (mocked S3 via `@MockBean S3StorageClient`, or a tagged
  LocalStack E2E): login → create report → attach a file → list → submit (mocked b2b) asserts the ZIP
  carried the attachment → remove on a submitted report → 409; ANALYST attach ok; tenant isolation.

### Step 8.5 — Docs + planning sync
- `docs/07` (new `attachment` table + entity), `docs/02` (`integration/aws/S3StorageClient` ✅, attachment
  service/controller rows), `docs/06` (attachment endpoints + RBAC + exception mapping), `docs/03`
  (local seed note: `aws --endpoint-url … s3 mb s3://goaml-attachments` + curl multipart upload example),
  `docs/09`/`ROADMAP`/`STATE`/`CLAUDE.md` (Phase 8 ✅, Phase 9 next, progress 8/14). Note the **AV-scanning
  gap** as a known concern. Fill the plan outcome + per-step docs.

## 4. JaCoCo / coverage
The S3 client lives in the already-gated `integration/aws/**`; add the new
`service/attachment/**` + `controller/report/**` (already gated) to `coveredPackages` and hold the same
**≥90% instruction / ≥80% branch** bar. Unit tests (Mockito/`@MockBean`) carry coverage; Testcontainers +
the tagged LocalStack IT prove the wiring.

## 5. What this phase does NOT do
Virus/content scanning (deferred hardening phase — D9), the async status poller + retry (Phase 9),
notifications (Phase 10), non-DPMSR report types, the React UI (Phase 13). No presigned URLs (D1). No real
FIU calls in tests (b2b mocked/WireMock).

## 6. How you'll test it manually (after this phase)
With `docker compose up -d postgres localstack redis` and the app running: create the bucket once
(`aws --endpoint-url http://localhost:4566 s3 mb s3://goaml-attachments`), log in (ANALYST/MLRO),
`POST /api/v1/reports` to create a DPMSR, then `POST /api/v1/reports/{id}/attachments` (multipart) to
attach a PDF → `GET …/attachments` to list. On MLRO `POST …/{id}/submit`, the ZIP now carries the report
XML **plus** the attachment (the b2b stub/endpoint receives the larger multipart). `docs/03` ships the
copy-paste seed + curl.

## 7. Verification
`docker compose up -d postgres localstack redis` → `./gradlew test jacocoTestCoverageVerification` green;
the E2E drives create→attach→list→submit-with-attachment→remove; status-gate 409, oversize/bad-type 400,
RBAC + tenant isolation asserted; `git status` scoped to Phase 8 dirs + the 8.5 docs.

## 8. Notes / to confirm in-step
- **Per-tenant isolation in S3** is by key prefix (D2), not separate buckets — a single bucket keeps infra
  simple; tenant scoping is enforced in the service (every fetch/delete resolves the row under the
  request's tenant schema first, so a key is only ever derived from tenant-owned metadata).
- **Orphan-object safety:** add the row **after** a successful `S3StorageClient.put`; on `delete`, remove
  the S3 object then the row. A failure between the two is logged and surfaces as 502 — at worst a
  harmless orphaned object (no dangling DB row pointing at missing bytes). A reconcile sweep is out of
  scope (could be a later ops task).
- **Streaming vs. buffering:** Phase 8 buffers (≤5 MB/file is small); the `S3StorageClient` interface
  takes `byte[]` to keep it testable. If large-file streaming is ever needed it's an additive method.
- **`Attachment` name clash** (engine record vs. JPA entity) is handled by distinct packages + FQN at the
  one call site (the submission service), mirroring how the Phase 7 `Report`/JAXB-`Report` clash was kept
  clean.

---

## Outcome — ✅ COMPLETE (2026-06-05)

Delivered across `07afd21` (8.1) · `727701e` (8.2) · `d85bd09` (8.3) · `77de56e` (8.4) · this commit (8.5):

- **8.1 S3 client** — `integration/aws/S3StorageClient` (+ `DefaultS3StorageClient`, `S3AccessException`);
  `S3Client` bean in `AwsConfig` (LocalStack endpoint-override + `forcePathStyle`); `awssdk:s3` dep;
  `goaml.aws.s3.bucket`. Unit (Mockito) + LocalStack IT.
- **8.2 persistence** — `V3__attachments.sql` (tenant `attachment` table, metadata + `s3_key`,
  `report_id` FK ON DELETE CASCADE); `Attachment` entity + `AttachmentRepository`. Testcontainers
  round-trip + cascade.
- **8.3 service** — `AttachmentService`/`DefaultAttachmentService` (validate ext/size → S3 → row;
  status-gated; audited) + `AttachmentExceptions`; `DefaultSubmissionService` wired to **pull attachment
  bytes from S3 into the ZIP** (`PackagingException`→422, `S3AccessException`→502). Unit tests incl. an
  unzip-and-assert of the posted ZIP.
- **8.4 web** — `AttachmentController` (multipart `POST` / `GET` / `DELETE`, ANALYST+MLRO) +
  `AttachmentView` + `GlobalExceptionHandler` mappings; Testcontainers E2E (attach → list → submit-carries
  → frozen-after-submit 409).
- **8.5 docs/planning sync** — `docs/{02,03,06,07,09}`, `CLAUDE.md`, `.planning/{ROADMAP,STATE}`, + the
  AV-scanning gap recorded in docs/09 §5 and the local seed (`s3 mb` + curl multipart) in docs/03.

**Decisions realised:** upload **proxied through the API** (D1, not presigned); **no AV scanning** (D9,
deferred + documented); single bucket + per-tenant/per-report key prefix; validate-at-upload **and**
re-enforce-at-submit (packager = single source of truth); **S3-first/row-second** ordering
(orphan-object-safe); attachments **frozen once submitted**; the engine vs JPA `Attachment` clash kept
clean by package + FQN.

**Coverage:** `DefaultS3StorageClient` + `DefaultAttachmentService` **100% instruction**; gate (≥90%/≥80%
on the gated packages, now incl. `service/attachment/**`) passes. The attachment flow is **manually
testable via the API** (create the `goaml-attachments` bucket in LocalStack; see docs/03).

**Not done (later phases):** AV/content scanning (deferred hardening), async status poller + retry (9),
notifications (10), non-DPMSR report types, React UI (13).
