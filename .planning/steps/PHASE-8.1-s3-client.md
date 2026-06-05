# Phase 8.1 — S3 storage client + AWS config + dependency

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-8-s3-attachments.md](../plans/phase-8-s3-attachments.md). First step of Phase 8.

---

## 1. Goal & why

Phase 8 stores per-report supporting documents in S3 and pulls them into the submission ZIP. Step 8.1
builds the **object-storage seam** in isolation (no persistence, no endpoint yet): a small client that
puts/fetches/deletes attachment bytes for a given key, mirroring the Phase 6 Secrets Manager pattern. The
service layer (8.3) will own the key strategy + metadata; this client only moves bytes.

## 2. What was built

| File | Role |
|---|---|
| `integration/aws/S3StorageClient` (interface) | `put(key,bytes,contentType)`, `fetch(key)→byte[]`, `delete(key)` — the seam. |
| `integration/aws/DefaultS3StorageClient` | AWS SDK v2 `S3Client`-backed impl; reads the bucket from `goaml.aws.s3.bucket`; every failure → `S3AccessException`. |
| `integration/aws/S3AccessException` | One failure type (missing / misconfigured / SDK error) — carries the key, never the bytes. |
| `config/aws/AwsConfig` | New `@Bean S3Client` — same endpoint-override + LocalStack static-creds branch as the Secrets bean, **plus `forcePathStyle(true)`** under LocalStack. |
| `config/aws/AwsProperties` | Added nested `S3(String bucket)` record → binds `goaml.aws.s3.bucket`. |
| `build.gradle` | Added `software.amazon.awssdk:s3` (version from the existing BOM `2.28.16`); added `service/attachment/**` to the JaCoCo `coveredPackages` (the S3 client is already under the gated `integration/aws/**`). |
| `application.yml` | Added `goaml.aws.s3.bucket: ${GOAML_S3_BUCKET:goaml-attachments}` (only new key). |

## 3. Key understanding / decisions

- **Naming:** our wrapper is `S3StorageClient`, **not** `S3Client` (the SDK's own type), avoiding a
  simple-name clash — same convention as `GoamlSecretsClient` vs `SecretsManagerClient`.
- **Path-style addressing under LocalStack.** LocalStack does not serve the virtual-host bucket subdomains
  (`bucket.s3.…`) the SDK defaults to, so the S3 bean sets `forcePathStyle(true)` whenever an endpoint
  override is present. Real AWS keeps the default (virtual-host) addressing.
- **Eager bucket resolution.** `bucket()` is resolved to a local variable **before** the SDK call (not
  inside the request-builder lambda). Reason: the lambda is only invoked when the SDK builds the request,
  so a misconfigured bucket would otherwise surface lazily — and a Mockito mock never invokes the lambda
  at all, which masked the validation in the first test run. Eager resolution fails fast and is testable.
- **`byte[]` interface, not streaming.** Attachments are ≤ 5 MB (`PackagingLimits.UAE_DEFAULT`), so the
  client buffers; a streaming overload can be added later if ever needed without breaking callers.
- **Single bucket, per-tenant key prefixes** (the key strategy lands in 8.3) — not a bucket-per-tenant.
- **One config switch** for real-AWS-vs-LocalStack (`goaml.aws.endpoint`), reused from Phase 6.

## 4. Tests

- **`DefaultS3StorageClientTest`** (unit, Mockito over `S3Client`, 10 tests): put/fetch/delete happy paths;
  blank key; null bytes; `NoSuchKeyException`→`S3AccessException`; SDK errors on each op; unconfigured
  bucket (blank + null `s3`).
- **`S3StorageClientIT`** (`@Tag("localstack")`, socket-reachability `assumeTrue` skip): creates a bucket,
  put→fetch→delete round-trip + delete-then-fetch-throws + fetch-missing-throws against compose LocalStack.
- **`AwsConfigTest`** extended: builds the `S3Client` bean with/without endpoint override; updated the
  `AwsProperties` constructions for the new 3-arg record.

## 5. Verification

`docker compose up -d postgres localstack redis` → `./gradlew test jacocoTestCoverageVerification` →
**BUILD SUCCESSFUL**. With LocalStack up the IT ran for real (2 passed, 0 skipped); the 10 unit + 5 config
tests pass; the ≥90%/≥80% coverage gate holds. `git status` scoped to Phase 8.1 files.

---

## Outcome
✅ The S3 storage seam exists and is tested (unit + live LocalStack). Next: **8.2** — the `attachment` tenant
table + JPA entity + repo (`V3__attachments.sql`).
