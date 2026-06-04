# Phase 6.3 — `GoamlB2bClient` operations + >90% test coverage

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-6-aws-and-b2b.md](../plans/phase-6-aws-and-b2b.md). Completes Phase 6's B2B client
> and adds the JaCoCo coverage gate + unit tests requested mid-phase.

---

## 1. Goal & why

With auth + token cache in place (6.2), 6.3 adds the actual **goAML B2B operations** — submit a report ZIP,
poll status, delete, message, fetch lookups — with correct 200/400/401 handling and a single auth-retry.
This is the seam the future submission service (Phase 7) and poller (Phase 9) call. Plus: **deterministic
unit tests to >90% coverage** (your request), independent of Docker.

## 2. What gets built

| File | Role |
|---|---|
| `b2b/GoamlB2bClient` (interface) | `postReport`, `getReportStatus`, `deleteReport`, `postMessage`, `getLookups`. |
| `b2b/RestGoamlB2bClient` | `RestClient` impl: attaches auth (TOKEN cookie via `TokenManager`, or BASIC), maps 200/400/401/other, retries once on 401. |
| `b2b/ReportStatus` (record) | Parsed OData status `(reportKey, status, errors)`. |
| `b2b/B2bConfig` (update) | A shared `JdkClientHttpRequestFactory` bean (HTTP/1.1) injected into both `TokenManager` and the client — removes the duplicated factory. |
| `build.gradle` | **JaCoCo** plugin + report + a coverage-verification rule scoped to the new Phase 6 packages at **0.90**; generated domain excluded. |

## 3. Design / understanding

- **Auth application:** TOKEN → `Cookie: SqlAuthCookie=<token>` from `TokenManager.token(cfg)`; on a 401 the
  client calls `TokenManager.refresh(cfg)` and retries **once**, then gives up (`B2bAuthException`). BASIC →
  HTTP Basic header from creds (`GoamlSecretsClient`), no caching.
- **Status mapping (per op):** 200 → parse result; 400 → `B2bValidationException(body)`; 401 →
  re-auth+retry-once; any other non-2xx or I/O error → `B2bTransportException`.
- **`postReport`** sends the engine's ZIP as `multipart/form-data` (one file part) to
  `/api/Reports/PostReport`; the response body is the `reportkey` (trimmed). **`getReportStatus`** parses the
  OData JSON (`value[0].Status/Errors`) into `ReportStatus`.
- **HTTP/1.1** everywhere (shared factory) — see 6.2.
- **Wire-detail caveat:** exact goAML paths/part-names/response shapes are from `docs/10` (the B2B guide as
  transcribed); the `RestClient` URIs + parse methods are the single place to adjust against the real UAE
  test environment. WireMock controls them in tests.

## 4. Testing strategy (the >90% coverage ask)

Two layers, both kept:
- **Unit tests (deterministic, no Docker — drive coverage):** Mockito mocks the AWS SDK `SecretsManagerClient`,
  the Redis `StringRedisTemplate`, and `TokenManager`; **WireMock** (in-process, always runs) provides the
  HTTP. Cover every branch: secrets parse/validate/missing/invalid; token cache-hit/miss/refresh/empty/401/
  transport; client postReport 200/400/401-retry/transport, status parse, delete/message/lookups, BASIC vs
  TOKEN auth; the `GoamlCredentials` mask; `B2bProperties` default.
- **Integration tests (real infra, conditional):** `GoamlSecretsClientIT` (LocalStack), `TokenManagerIT`
  (Redis) — tagged + reachability-gated, as before.
- **JaCoCo:** `jacocoTestReport` after `test`; `jacocoTestCoverageVerification` enforces **≥90%** on the new
  `b2b` + `integration.aws` + `config.aws` packages (excludes the generated domain). Overall project number
  reported to the user too.

## 5. Out of scope
Submission persistence / HTTP endpoint (Phase 7), the scheduler/poller + `RetryService` (Phase 9), the
runtime lookup-refresh scheduling (the `getLookups` *call* exists; scheduling it is later).

## 6. Verification
`docker compose up -d localstack redis` → `./gradlew test jacocoTestCoverageVerification` green; new-package
coverage ≥90% (report under `build/reports/jacoco`); bare checkout (no infra) still green (ITs skip; unit
tests + WireMock carry coverage).

---

## Outcome

**Built:** `GoamlB2bClient` (interface) + `RestGoamlB2bClient` with all five ops (`postReport`,
`getReportStatus`, `deleteReport`, `postMessage`, `getLookups`), `ReportStatus` (OData parse), and a shared
HTTP/1.1 `b2bRequestFactory` bean in `B2bConfig` (now used by both `TokenManager` and the client — the
duplicated factory removed). Auth: TOKEN → `SqlAuthCookie` from `TokenManager` with **refresh+retry-once on
401**; BASIC → HTTP Basic from `GoamlSecretsClient`. Error mapping: 400 → `B2bValidationException(body)`,
401 → retry then `B2bAuthException`, other non-2xx / I/O → `B2bTransportException`.

**Tests (kept both layers):**
- **Unit (no Docker, deterministic — drive coverage):** `DefaultGoamlSecretsClientTest` (mock SDK),
  `DefaultTokenManagerTest` (mock Redis + WireMock), `RestGoamlB2bClientTest` (mock TokenManager/secrets +
  WireMock, all ops + outcomes), `B2bSubmissionE2ETest` (real engine build→marshal→zip → stubbed
  `PostReport` → reportkey), plus `GoamlCredentialsTest`, `AwsConfigTest`, `B2bConfigAndPropertiesTest`,
  `B2bErrorsTest`.
- **Integration (real infra, conditional/tagged):** `GoamlSecretsClientIT` (LocalStack), `TokenManagerIT`
  (Redis) — unchanged, skip cleanly when infra is down.

**Coverage (JaCoCo):** new Phase 6 code (`b2b` + `integration.aws` + `config.aws`, generated domain
excluded) = **98.7% instructions / 89.3% branches**. The gate
(`jacocoTestCoverageVerification`, ≥90% instruction / ≥80% branch on those packages) **passes**.
Project-wide (excl. generated + bootstrap) ≈ 84.7% instr — informational; the hard gate is scoped to the
new code. Report at `build/reports/jacoco/test/html/index.html`.

**Verified:** `docker compose up -d localstack redis` → `./gradlew test jacocoTestCoverageVerification`
**BUILD SUCCESSFUL**; on a bare checkout the ITs skip and the unit tests + in-process WireMock keep the
build green and coverage intact.

**Wire-detail caveat (unchanged):** the goAML paths/part-names/response shapes follow `docs/10`; the
`RestGoamlB2bClient` constants + parse methods are the single place to adjust against the real UAE test
environment.

**Phase 6 not yet done:** Step **6.5** (docs/planning sync — reconcile the `GoamlSecretsClient` rename in
`docs/10`, mark Phase 6 ✅, note Secrets-only scope) still remains before Phase 6 closes. (There was no
separate 6.4 file — the `goaml.aws.*` / `spring.data.redis` / `goaml.b2b.*` config keys were added inline in
6.1/6.2.)
