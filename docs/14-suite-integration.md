# 14 — Suite integration & federated auth (Phase 1.5)

> The consumer contract for sibling Vyttah apps (**accounting / ERP** and the **AML screening
> software**) that drive goAML's DPMSR lifecycle. goAML is the **single system-of-record** and the
> **sole FIU submission authority**; sibling apps integrate as REST clients behind their own UIs.
> The standalone product (`auth.mode=native`) is unchanged — everything here is additive and inert
> until a `trusted_service` is registered.

## Two integration models

```
 Accounting UI ─┐                         ┌─ POST /api/v1/auth/federated/token  → goAML JWT
 AML SW UI ─────┼─ federated user JWT ───►├─ POST /api/v1/reports               (build + validate)   [Model 1]
 (own UIs)      │                         ├─ POST /api/v1/reports/{id}/submit   (MLRO-gated)
                │                         ├─ GET  /api/v1/reports … /xml /status /attachments
                │                         └─ POST /api/v1/reportability/check   (authoritative verdict)
 Accounting ────┴─ service assertion ────► POST /api/v1/integration/accounting/transactions          [Model 2]
 (server)                                  (raw invoice → detect → build draft → status pull)
```

- **Model 1 — embedded consumer.** The sibling app authenticates its *user* via the federated
  token exchange, then calls the **existing** authenticated `/api/v1/reports*` API exactly as a
  native user would. The app builds the DPMSR itself; goAML validates, stores, submits, tracks.
- **Model 2 — raw push (accounting only).** The accounting *server* posts a self-contained invoice;
  goAML judges reportability, and if reportable builds a validated DPMSR draft itself.

Both can be used together: the same report is visible to all users of the tenant regardless of how
it was created. **MLRO submit-gating is preserved in every path** — embedding a UI cannot bypass the
compliance gate.

---

## Service trust — the signed assertion

Every server-to-server call (the token exchange, and the Model 2 push) is authenticated by a
**short-lived RS256 JWT** the caller signs with its registered private key. goAML verifies it against
the source's registered public key. No shared secret crosses the wire.

| Claim   | Value                                                              |
|---------|-------------------------------------------------------------------|
| `iss`   | the source system name (`ACCOUNTING` / `SCREENING`)               |
| `aud`   | **`goaml`** (rejected otherwise)                                   |
| `sub`   | the external **user id** (for the token exchange)                 |
| `email` | the external user's email (used for JIT provisioning)             |
| `org`   | the external **org id** → resolves the goAML tenant              |
| `exp`   | ≤ **5 minutes** after `iat` (longer-lived assertions are rejected) |

Registration (goAML admin / migration, shared `public` schema):

- **`trusted_service`** — `(source_system, public_key_pem, status)`. One active service per source.
- **`tenant_external_ref`** — `(source_system, external_org_ref) → tenant`. Maps the caller's org id
  (e.g. the accounting `companyId`) to a goAML tenant.
- **`external_identity`** — `(source_system, external_user_id) → app_user`. Maps a federated user to
  a goAML user. Created automatically by JIT provisioning on first exchange (default role
  **`ANALYST`**); role hints in the assertion are advisory — goAML stays authoritative for RBAC.

## Auth modes

`goaml.auth.mode` (env `GOAML_AUTH_MODE`), default **`native`**:

| Mode        | `/api/v1/auth/login` | `/api/v1/auth/federated/token` |
|-------------|----------------------|--------------------------------|
| `native`    | ✅                    | 404 (disabled)                 |
| `federated` | 404 (disabled)       | ✅                              |
| `both`      | ✅                    | ✅                              |

---

## Federated token exchange

`POST /api/v1/auth/federated/token`

```json
{ "sourceSystem": "ACCOUNTING", "assertion": "<RS256 JWT>" }
```

→ `200 { "accessToken": "<goAML JWT>" }` — a standard tenant-scoped goAML JWT, used as
`Authorization: Bearer …` against the entire authenticated API.

| Failure                                             | Status |
|-----------------------------------------------------|--------|
| bad signature / wrong key / expired / wrong `aud`   | `401`  |
| authenticated but identity/tenant unresolved (JIT off, unknown org) | `403` |
| `auth.mode=native`                                  | `404`  |

---

## Model 1 — embedded consumer (the `/api/v1/reports*` API)

With a federated JWT the sibling app drives the full lifecycle. RBAC is unchanged.

| Method & path                          | Role            | Purpose                          |
|----------------------------------------|-----------------|----------------------------------|
| `POST /api/v1/reports`                 | ANALYST+        | create **and validate** a DPMSR  |
| `GET  /api/v1/reports`                 | ANALYST+        | list the tenant's reports        |
| `GET  /api/v1/reports/{id}`            | ANALYST+        | one report                       |
| `GET  /api/v1/reports/{id}/xml`        | ANALYST+        | the marshalled goAML XML         |
| `POST /api/v1/reports/{id}/submit`     | **MLRO**        | submit to the FIU                |
| `GET  /api/v1/reports/{id}/status`     | ANALYST+        | refresh + read FIU status        |
| `POST /api/v1/reportability/check`     | ANALYST+        | authoritative reportability verdict |
| attachments                            | ANALYST+        | see [08 / attachments]           |

`create` returns `{ reportId, status (VALID/INVALID), validationMessages[] }`. A browser client needs
its origin in the CORS allow-list (`goaml.web.cors.*`).

### Reportability check

`POST /api/v1/reportability/check` lets a client ask goAML's authoritative verdict before building or
submitting (detection rules live in goAML — no rule drift across apps):

```json
{ "amount": 90000, "currencyCode": "AED", "involvesPreciousMetalsOrStones": true }
```

→ `{ "reportable": true, "reasons": ["cash ≥ AED 55,000", "precious-metals/stones dealing"] }`

`currencyCode` defaults to AED and **v1 checks AED only** (a non-AED currency is rejected — the caller
converts first); `involvesPreciousMetalsOrStones` defaults to `true`. The DPMS cash threshold is
**AED 55,000**.

---

## Model 2 — accounting raw-invoice push

Server-to-server, authenticated by the `X-Service-Assertion` header (the same signed assertion; **not**
a user JWT). Under `/api/v1/integration/accounting`:

| Method & path                                   | Purpose                                   |
|-------------------------------------------------|-------------------------------------------|
| `POST /transactions`                            | push one invoice → immediate verdict      |
| `GET  /transactions/{documentNumber}?companyId=`| status of one document                    |
| `GET  /transactions?companyId=&status=`         | all goAML reports from this company        |

`POST /transactions` → **`202`**:

```json
{ "goamlRef": "ACC-777-SAL-1001", "reportable": true,
  "reportId": "…", "status": "VALID", "reasons": ["…"] }
```

- **Verdict immediately.** Not reportable → `{ reportable:false, reportId:null, status:"NOT_REPORTABLE" }`.
- **Reportable** → goAML builds a **validated DPMSR draft** (tenant resolved from `companyId` via
  `tenant_external_ref`; reporting person = a tenant MLRO).
- **Idempotent** on the derived reference `ACC-<companyId>-<documentNumber>` — a retried push returns
  the existing report, so the accounting outbox can retry safely.
- The request body is the `AccountingTxnPayload` contract (source document, cash settlement, party —
  corporate→entity / individual→person, and `goods[]`). Goods classification → goAML `item_type` is
  owned by goAML (`CommodityMapping`): `METAL→GOLD`, diamonds→`DIMND`, jewellery→`JEWEL`,
  stones/pearl→`GEM`, `WATCH→WATCH`. A **watch counts as DPMS goods only when the line carries
  precious-metal or stone value**.

### Submit gating (`tenant_goaml_config.auto_submit`)

After a VALID draft is created:

- **`auto_submit = true`** → goAML submits to the FIU immediately (audited). A transport/credential
  failure is swallowed and **falls back to the MLRO gate** — a FIU outage never fails the push.
- **`auto_submit = false`** (default, the safe path) → the draft waits; goAML notifies the tenant's
  MLROs (in-app, + email if enabled) that a **one-click submit** is waiting.

| Failure                          | Status |
|----------------------------------|--------|
| missing/invalid service assertion | `401`  |
| `companyId` not mapped to a tenant | `404` |

### Delivery guarantee

goAML offers **at-least-once-safe** ingestion via idempotency on the external reference. No-loss is the
**accounting side's** responsibility: an outbox + retry until a `202` is observed. Retries are safe
because a duplicate push returns the existing report.

---

## What the standalone deployment sees

Nothing changes. `auth.mode` defaults to `native`, the federated endpoint is `404`, and the
integration endpoints are inert with no registered `trusted_service`. The suite features light up only
when an operator registers a trusted service and the org/identity mappings.

> **Screening (Model for the AML software) — `1.5c`, pending.** The screening REST push
> (`/api/v1/integration/screening`) and a goAML-side manual-entry form are a later sub-phase, gated on
> the screening payload schema. The AML software can already use **Model 1** today.
