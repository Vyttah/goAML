# Phase 6.1 — AWS Secrets Manager client + compose Redis

> **Status: ✅ DONE (2026-06-04) — commit `e6a03d6`.**
> Part of [../plans/phase-6-aws-and-b2b.md](../plans/phase-6-aws-and-b2b.md). First step of Phase 6.

---

## 1. Goal & why

The engine can build/validate/marshal/zip a report but nothing talks to AWS yet. Phase 6.1 builds the
**per-tenant goAML-credentials seam**: given a `secrets_path` (from `tenant_goaml_config`), fetch that
tenant's goAML B2B credentials from **AWS Secrets Manager**. Every submission/poll later depends on using
*the correct tenant's* FIU identity, so this is isolated and tested on its own. It is **not** wired to any
HTTP endpoint (that's Phase 7).

## 2. What was built

| File | Role |
|---|---|
| `integration/aws/GoamlSecretsClient` (interface) | `GoamlCredentials fetch(String secretsPath)` — the seam. |
| `integration/aws/DefaultGoamlSecretsClient` | AWS SDK v2-backed impl; reads the secret string, parses JSON, validates. |
| `integration/aws/GoamlCredentials` (record) | `{username, password, clientCode?}`; `toString()` **masks** the password. |
| `integration/aws/SecretsAccessException` | One failure type for missing / unreadable / invalid / incomplete secrets — carries no secret material. |
| `config/aws/AwsProperties` (record) | Binds `goaml.aws.{region,endpoint}`; `hasEndpointOverride()`. |
| `config/aws/AwsConfig` | Produces the AWS SDK v2 `SecretsManagerClient` bean. |
| `build.gradle` | AWS SDK v2 BOM `2.28.16` + `secretsmanager`. |
| `docker-compose.yml` | Added `redis:7-alpine` (for 6.2); LocalStack already present. |
| `application.yml` | `goaml.aws.{region=me-central-1, endpoint=}`. |

## 3. Key understanding / decisions

- **Naming:** our wrapper is `GoamlSecretsClient`, **not** `SecretsManagerClient` (the doc-10 name), to avoid
  a confusing simple-name clash with the AWS SDK's own `SecretsManagerClient`. (Will reconcile docs in 6.5.)
- **No separate KMS client.** Secrets Manager decrypts with its KMS key transparently on `GetSecretValue`;
  reads don't need a KMS client. (KMS only matters if we manage keys directly — not here.)
- **Real AWS vs LocalStack** is decided by `goaml.aws.endpoint`: blank → real AWS, regional endpoint +
  default credentials chain (IRSA on EKS); set → endpoint override + static dummy creds (LocalStack accepts
  any). One switch, no profiles.
- **Secret shape** = a JSON document per tenant: `{"username","password","clientCode"?}`. `clientCode` is
  optional. Parsed leniently (`@JsonIgnoreProperties(ignoreUnknown=true)`).
- **Secrets never leak:** `GoamlCredentials.toString()` masks the password; exceptions carry only the path.

## 4. Verification

- `GoamlSecretsClientIT` (5 tests) against the **docker-compose LocalStack** (`secretsmanager`, `:4566`):
  fetch+parse, optional clientCode, missing secret → exception, invalid JSON → exception, missing password
  → exception, and `toString` masking.
- `@Tag("localstack")` + a `:4566` reachability check in `@BeforeAll` → runs when LocalStack is up, **skips
  cleanly** otherwise (so `./gradlew test` is green on a bare checkout).
- Full `./gradlew test` green with LocalStack up.

## 5. Out of scope (later)
S3 (Phase 8), SES (Phase 10), the b2b client (6.2/6.3), any persistence/endpoint wiring (Phase 7).
