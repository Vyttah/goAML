# Phase 6 — AWS (Secrets Manager) + the goAML B2B client

> **Status: 🔲 PROPOSED (2026-06-04) — awaiting your approval before implementation.**
> Roadmap **Phase 6**. Brings the (built, tested) engine to the outside world: per-tenant goAML
> credentials from AWS Secrets Manager, and the goAML Web **B2B REST client** that submits reports.
> Wire protocol reference: [../../docs/10-b2b-submission-protocol.md](../../docs/10-b2b-submission-protocol.md).

---

## 1. What this phase is, and why

The engine can build → validate → marshal → ZIP a report, but nothing yet **talks to AWS or the FIU**.
Phase 6 builds the two outbound seams, as **standalone, fully-tested components** (not yet wired to an
HTTP endpoint — that wiring + report/submission persistence is **Phase 7**):

1. **`integration/aws/SecretsManagerClient`** — fetch a tenant's goAML B2B credentials from the
   `secrets_path` recorded in `tenant_goaml_config`. This is the **per-tenant identity seam**: every
   submission/poll must use *the correct tenant's* FIU credentials, so this must be clean and isolated.
2. **`b2b/` goAML B2B REST client** — authenticate (token cache + 401 re-auth), `postReport` (multipart
   ZIP → `reportkey`), `getReportStatus` (OData), `deleteReport`, `postMessage`, `getLookups`; with typed
   errors so callers can distinguish *re-auth* vs *fix-the-report* vs *transient-retry*.

**Scope decision (confirmed):** **Secrets Manager only** for the AWS piece this phase.
`S3StorageClient` (attachments) is built in **Phase 8** and `SesClient` (email) in **Phase 10**, each when
first used — no unused clients now. *(This narrows the original "6a builds all three" note in
`docs/09 §3` / `ROADMAP`; those will be updated to match in Step 6.5.)*

---

## 2. Decisions (my recommendations — flag any you want changed)

| # | Decision | Recommendation | Why |
|---|----------|----------------|-----|
| D1 | AWS SDK | **AWS SDK v2** (`software.amazon.awssdk:secretsmanager`), via the `bom` | Current SDK; LocalStack-compatible; matches `docs/09`. KMS is transparent (Secrets Manager decrypts with its KMS key on `GetSecretValue`) — **no separate KMS client needed** for read. |
| D2 | HTTP client | Spring **`RestClient`** (Boot 3.3 built-in) | Matches the documented choice (`docs/10 §8`); no extra dep; synchronous fits a per-tenant call. |
| D3 | Secret format | A **JSON document** at `secrets_path`: `{ "username", "password" }` (+ optional `clientCode`) | One secret per tenant; parsed into a small `GoamlCredentials` record. Mirrors `auth_mode` TOKEN/BASIC already in `tenant_goaml_config`. |
| D4 | Endpoint config | LocalStack endpoint override via config (`goaml.aws.endpoint`, `goaml.aws.region=me-central-1`) | Lets tests + local dev hit the **docker-compose LocalStack** `:4566`; empty in prod → real AWS (IRSA on EKS). |
| D5 | Token caching | **Redis** (`spring-boot-starter-data-redis`) — `TokenManager` caches the `SqlAuthCookie` **per tenant** (key `goaml:b2b:token:<tenantId>`) with a TTL; re-auth on 401 or miss | **Your call:** Redis instead of in-memory → shared across app instances (EKS-ready), TTL-expiring. Runs as a docker-compose service. |
| D6 | Submission wiring | **Not** in Phase 6 — no `SubmissionService`/persistence yet | There is no `report`/`submission` table until Phase 7. Phase 6 proves end-to-end **in a test** (build DPMSR → zip → WireMock `PostReport` → assert `reportkey`), not via a persisted service. |
| D7 | New deps | `software.amazon.awssdk:bom` + `secretsmanager` (impl); **`spring-boot-starter-data-redis`** (impl); **WireMock** (`wiremock-standalone`, test) | AWS SDK + WireMock are the two `docs/09 §5` flagged; Redis added per your call. |
| D8 | Test infra | **docker-compose** `localstack` + `redis` (run them, then `./gradlew test`); AWS/Redis-touching tests are **tagged/conditional** so the suite still passes when they're not up | **Your call:** run against the real compose containers, not Testcontainers. Postgres stays Testcontainers (unchanged). Gating keeps `./gradlew test` green on a bare checkout. |

---

## 3. Step breakdown (one commit per step, each green)

### Step 6.1 — docker-compose `redis` + `integration/aws/` Secrets Manager client (LocalStack-tested)
- `docker-compose.yml`: add a **`redis`** service (`redis:7-alpine`, port `6379`, healthcheck); LocalStack
  already exists. Update the comment ("Phase 6: + redis; localstack now used").
- `build.gradle`: add the AWS SDK v2 BOM + `secretsmanager`.
- `config/aws/AwsConfig` — a `SecretsManagerClient` (SDK) bean honouring `goaml.aws.{region,endpoint}`
  (endpoint override only when set, for LocalStack).
- `integration/aws/SecretsManagerClient` (thin wrapper, interface + `Default*`) →
  `GoamlCredentials get(String secretsPath)`; JSON-parse; typed `SecretsAccessException` on miss/parse fail.
- small record `GoamlCredentials(username, password, clientCode)`.
- **Test (docker-compose LocalStack):** tagged/conditional (`@EnabledIfEnvironmentVariable` or a
  `@Tag("localstack")` + an availability check on `:4566`) so it runs when LocalStack is up and skips
  cleanly otherwise. AWS CLI/SDK puts a secret, the client reads + parses it; missing path → exception.

### Step 6.2 — Redis token cache + `b2b/` auth + typed errors (WireMock-tested)
- `build.gradle`: add `spring-boot-starter-data-redis` (impl) + WireMock (test).
- `application.yml`: `spring.data.redis.{host,port}` (defaults `localhost:6379`, env-overridable).
- `b2b/GoamlB2bClient` (interface) + `b2b/RestGoamlB2bClient` (`RestClient`).
- `b2b/auth/TokenManager` — resolves creds via `SecretsManagerClient`, `POST /api/Authenticate/GetToken`,
  caches the `SqlAuthCookie` **in Redis** (key `goaml:b2b:token:<tenantId>`, TTL), re-auths on 401/miss;
  supports `auth_mode` BASIC as the alternative.
- Typed errors: `B2bAuthException` (401), `B2bValidationException` (400 + body), `B2bTransportException`.
- **Test:** WireMock for GetToken (200 → token cached in Redis; 401 → re-auth-and-retry; bad creds →
  `B2bAuthException`). Redis via the compose `redis` (tagged/conditional like 6.1).

### Step 6.3 — `postReport` + `getReportStatus` + the rest (WireMock-tested)
- `postReport(tenant, zipBytes) → reportkey` (multipart), `getReportStatus(reportkey)` (OData parse),
  `deleteReport`, `postMessage`, `getLookups`.
- Map HTTP 200/400/401 → reportkey / `B2bValidationException` / re-auth; other non-2xx → `B2bTransportException`.
- **Tests (WireMock):** 200 reportkey parse; 400 → `B2bValidationException` carrying the body; OData status
  parse; **end-to-end**: `DpmsrReportBuilder` → `ReportMarshaller` → `ReportZipPackager` → stubbed
  `PostReport` → assert `reportkey` (proves the ZIP the engine emits is what we submit).

### Step 6.4 — config of the new surface
- `application.yml`: `goaml.aws.{region,endpoint}` + `spring.data.redis.{host,port}` keys (documented;
  AWS endpoint empty by default → real AWS in prod).
- No secrets in the repo; LocalStack/Redis are dev/compose only.

### Step 6.5 — update docs + planning to match
- `docs/09` (Phase 6 → done; Secrets-only scope, S3/SES deferred), `docs/10` (client built),
  `docs/03` (AWS SDK v2 + Redis + WireMock deps; **compose** LocalStack/Redis for tests), `docs/02`
  (`integration/aws` + `b2b` now ✅), `docs/11` (SqlAuthCookie/TokenManager/Secrets Manager/Redis as built),
  and the **build/test recipe** in `CLAUDE.md`/`docs/03` (`docker compose up -d postgres localstack redis`).
  `.planning/STATE.md` + `ROADMAP.md` (Phase 6 ✅, Phase 7 next).

---

## 4. What this phase does NOT do
- No report/submission **persistence** and no HTTP submit endpoint (Phase 7).
- No **S3** attachments (Phase 8), no **SES** email (Phase 10), no **scheduler/poller** or `RetryService`
  (Phase 9), no `LookupSyncService` runtime refresh wiring (the `getLookups` *call* exists; scheduling it
  is later).
- No real FIU calls — all HTTP is **WireMock**; all AWS is **LocalStack**. Real per-tenant base URLs +
  credentials remain an open external input (`docs/09 §4`).

## 5. How it's verified
- With `docker compose up -d postgres localstack redis`, `./gradlew test` is green incl. the new
  LocalStack + Redis + WireMock tests; on a bare checkout (no localstack/redis) the suite is **still green**
  because those tests are tagged/conditional and skip.
- The per-tenant seam is exercised: a secret keyed to a tenant resolves to that tenant's creds; the token
  is cached **in Redis** per tenant; a 401 re-auths.
- End-to-end test: engine-produced DPMSR ZIP is accepted by the stubbed `PostReport` and the `reportkey`
  is parsed back.
- `git status`: only `build.gradle`, `docker-compose.yml`, `src/main/java/.../{config/aws,integration/aws,b2b}`,
  `src/test/...`, `application.yml`, and the Step-6.5 docs/planning change.

## 6. Risks / things I'll confirm in-step
- **Tagged/conditional tests** depend on the compose `localstack` + `redis` being up — I'll wire a clean
  skip (so CI without them still passes) and document the `docker compose up` recipe.
- **Exact goAML auth/endpoint paths** are from the B2B guide as transcribed in `docs/10`; if the real UAE
  test environment differs, the `RestClient` URIs are the single place to adjust.
- **`application.yml` working-tree change** that's been left untouched all session — I will not fold
  unrelated edits into it; only add the `goaml.aws.*` + `spring.data.redis.*` keys.

---

## Outcome (filled in after implementation)

_(Pending approval.)_
