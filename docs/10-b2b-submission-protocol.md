# 10 — B2B Submission Protocol

> How reports are actually submitted to the FIU over goAML's REST interface. This is the target of the
> `b2b/` client (Phase 6) and the `scheduler/` poller (Phase 9).
>
> ✅ **The client is built (Phase 6)** — `b2b/GoamlB2bClient` + `RestGoamlB2bClient` + `TokenManager`,
> tested against WireMock (and the Secrets Manager seam against LocalStack). It is **not yet wired to an
> HTTP endpoint** (Phase 7) and makes **no real FIU calls yet** (needs per-tenant base URLs + credentials —
> an external input). The protocol below is drawn from the goAML *B2B Developers Guide*; the exact
> paths/part-names/response shapes should still be verified against the real UAE test environment — the
> `RestGoamlB2bClient` URI constants + parse methods are the single place to adjust.

---

## 1. The shape of "goAML Web B2B"

goAML Web exposes a **REST** interface ("B2B") for machine-to-machine submission. Three things matter:

1. **Authentication** — get a session token (or use HTTP Basic).
2. **Submission** — POST a multipart ZIP (one report XML + attachments), get a `reportkey`.
3. **Status** — submission is **asynchronous**; poll an OData endpoint for the outcome.

Plus a **MessageBoard** API for FIU↔RE correspondence.

Each tenant (RE) uses **its own credentials and base URL** — both stored per-tenant
(`tenant_goaml_config.base_url` + `secrets_path` → Secrets Manager). There is a **test** environment and
a **prod** environment per FIU.

---

## 2. Authentication

```
POST {base}/api/Authenticate/GetToken
   → returns a session token; the client attaches it as a "SqlAuthCookie" on subsequent calls
```
- Alternative: **HTTP Basic** auth on each request (the `auth_mode` column is `TOKEN` or `BASIC`).
- The token is **cached per tenant in Redis** (`goaml:b2b:token:<tenantId>`, TTL) by `TokenManager`; on a
  **401**, the client `refresh()`es and retries once.
- The HTTP client is pinned to **HTTP/1.1** (goAML Web is legacy ASP.NET; the JDK client's default
  HTTP/2 h2c negotiation otherwise fails with `RST_STREAM`).

---

## 3. Submitting a report

```
POST {base}/api/Reports/PostReport
   Content-Type: multipart/form-data
   body: a ZIP containing exactly one report XML + zero-or-more attachment files
   → 200: returns a `reportkey` (the FIU's handle for this submission)
   → 400: validation failure (body contains errors)
   → 401: auth expired/invalid (re-auth and retry)
```

The ZIP is exactly what `engine/packaging/ReportZipPackager` produces (report XML first, then
attachments), under the UAE limits (5 MB/file, 20 MB/ZIP — `PackagingLimits.UAE_DEFAULT`). See
[05 — The Engine §5](05-engine.md#5-packaging-enginepackaging).

The returned `reportkey` is stored (in the future `submission` table) and used to poll status.

Other report operations: `deleteReport` (retract a submission).

---

## 4. Polling status (asynchronous, OData)

Submission doesn't return the final verdict — you poll for it:

```
GET {base}/odata/Odata/OdataReports?$filter=ReportKey eq '<reportkey>'
   → returns the report's current Status and any Errors
```
- The `scheduler/SubmissionPoller` (Phase 9) enumerates tenants, polls each in-flight submission with
  that tenant's creds, updates the local status, and fires notifications on transitions
  (Accepted / Rejected / Processing / Errors).
- `RetryService` re-submits transient failures with backoff.
- **Idempotency:** the report's `entity_reference` (unique per tenant) prevents duplicate submissions —
  this is why the validator hard-requires `entity_reference` (see [05 §3](05-engine.md)).

---

## 5. MessageBoard (FIU correspondence)

```
{base}/api/MessageBoard/*
```
Used to send/receive messages with the FIU (e.g. follow-up requests). `postMessage` is one of the
planned `b2b/` client operations; replies surface as notifications (Phase 10).

---

## 6. Refreshing lookups from the FIU

The FIU's code lists (countries, currencies, transmodes, funds, …) are downloadable:
```
{base}/.../OdataLookups
```
`getLookups` (b2b) + `LookupSyncService` will refresh the seed files in `lookups/ae/*` at runtime, so a
code-list change at the FIU doesn't require a release. Today those files are static placeholders (see
[05 §7](05-engine.md#7-lookups-enginelookup)).

---

## 7. The typed errors the client must expose

| Exception | When |
|-----------|------|
| `B2bAuthException` | HTTP 401 — credentials rejected/expired |
| `B2bValidationException` | HTTP 400 — the FIU rejected the report; carries the error body |
| `B2bTransportException` | network/transport failure, non-2xx that isn't 400/401 |

These let `service/` orchestration distinguish "re-auth and retry" from "fix the report" from "transient,
back off and retry."

---

## 8. The mapping to code (Phase 6 — built)

| Protocol piece | Class |
|----------------|-------|
| Auth + Redis token cache + 401 re-auth | `b2b/auth/TokenManager` + `DefaultTokenManager` |
| `PostReport`, `getReportStatus`, `deleteReport`, `postMessage`, `getLookups` | `b2b/GoamlB2bClient` + `RestGoamlB2bClient` (Spring `RestClient`, HTTP/1.1) |
| Per-tenant config the caller supplies | `b2b/B2bTenantConfig` (tenantId, baseUrl, secretsPath, authMode) |
| Per-tenant creds | resolved from `tenant_goaml_config.secrets_path` via `integration/aws/GoamlSecretsClient` |
| Typed errors | `b2b/error/{B2bAuthException, B2bValidationException, B2bTransportException}` |
| Tests | **WireMock** (200/400/401, reportkey + OData parse) + **LocalStack**/**Redis** integration tests; JaCoCo ≥90% gate |

> When you implement this, the per-tenant credential resolution (`tenant_goaml_config` → Secrets Manager
> → `TokenManager`) is the critical seam. Get it right and isolated, because every submission and every
> poll depends on using *the correct tenant's* identity with the FIU.

---

**Next:** [11 — Glossary](11-glossary.md).
