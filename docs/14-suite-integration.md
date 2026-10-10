# 14 — Suite integration & federated auth (Phase 1.5)

> The consumer contract for sibling Vyttah apps (**accounting / ERP** and the **AML screening
> software**) that drive goAML's DPMSR lifecycle. goAML is the **single system-of-record** and the
> **sole FIU submission authority**; sibling apps integrate as REST clients behind their own UIs.
> The standalone product (`auth.mode=native`) is unchanged — everything here is additive and inert
> until a `trusted_service` is registered.
>
> **See also — frontend-direct cockpit flow:** the AML cockpit now consumes goAML as a **frontend-direct
> microservice** (browser → goAML for create/approve/submit/xml; auth bootstrapped by one backend mint via
> `/api/v1/auth/federated/token`). This is **Model 1** applied from the browser, plus a curated
> `POST /api/v1/reports/dpmsr` entry and a per-`trusted_service` `default_role`. Full design + run guide:
> [.planning/plans/goaml-as-aml-microservice.md](../.planning/plans/goaml-as-aml-microservice.md).

## The integration models

```
 Accounting UI ─┐                         ┌─ POST /api/v1/auth/federated/token  → goAML JWT
 AML SW UI ─────┼─ federated user JWT ───►├─ POST /api/v1/reports               (build + validate)   [Model 1]
 (own UIs)      │                         ├─ POST /api/v1/reports/{id}/submit   (MLRO-gated)
                │                         ├─ GET  /api/v1/reports … /xml /status /attachments
                │                         └─ POST /api/v1/reportability/check   (authoritative verdict)
 Accounting ────┴─ service assertion ────► POST /api/v1/integration/accounting/transactions          [Model 2]
                                           POST /api/v1/integration/accounting/reports               [Model 2b]
 (server)                                  (raw invoice → detect → build draft → status pull)

 goAML ───────── service assertion ────► POST {aml}/api/v1/integration/goaml/report-status     [Model 2c]
 (server, outbound)                        (review / submission transition → AML → ERP accounting)
```

- **Model 1 — embedded consumer.** The sibling app authenticates its *user* via the federated
  token exchange, then calls the **existing** authenticated `/api/v1/reports*` API exactly as a
  native user would. The app builds the DPMSR itself; goAML validates, stores, submits, tracks.
- **Model 2 — raw push (accounting only).** The accounting *server* posts a self-contained invoice;
  goAML judges reportability, and if reportable builds a validated DPMSR draft itself.
- **Model 2c — status events out (S2).** The only path where goAML is the **caller**: it signs its own
  assertion (`iss=GOAML`) and pushes every review / submission transition to the AML platform, which forwards
  it to the ERP. So an ERP operator sees the FIU's answer to a filing without the ERP polling goAML.

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
{ "amount": 90000, "currencyCode": "AED", "involvesPreciousMetalsOrStones": true,
  "fundsType": "WIRE_TRANSFER", "counterpartyType": "LEGAL_ENTITY",
  "wireScope": "INTERNATIONAL", "viaExchangeHouse": false }
```

→ `{ "reportable": true, "reasons": ["International wire transfer … meets the DPMS threshold — reportable (UAE FIU goAML FAQ v1.10 Q51)"], "thresholdAed": 55000 }`

`currencyCode` defaults to AED and **v1 checks AED only** (a non-AED currency is rejected — the caller
converts first); `involvesPreciousMetalsOrStones` defaults to `true`. The threshold is **AED 55,000**.

The four funds-flow fields are **optional and additive** — omitting them keeps the original cash-trigger
behavior. When supplied, the verdict follows the official trigger table (MOE Circular 08/AML/2021 + UAE
FIU goAML FAQ v1.10 Q51; see [01 — Business Context](01-business-context.md) §6 for the table and the
2025 legal-basis update): individual non-cash payments and cheques/cards are never reportable; company
wires ≥ AED 55,000 are reportable except a local UAE bank wire not routed via an exchange house; all
international wires ≥ AED 55,000 are reportable. Unknown wire details on an explicit wire are flagged
reportable-for-review (conservative). Every reason cites the rule that fired.

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

## Model 2b — accounting *filing* push (the ERP goAML filing wizard)

The ERP (`vyttah-accounting`) also has a **operator-driven** path: a user walks the goAML filing wizard on a
Cash Receipt / Payment, the browser assembles the whole report, and accounting posts it. goAML makes **no
reportability judgement** here — the operator already decided to file. Same `/api/v1/integration/accounting`
root, same ACCOUNTING assertion, same B11 org cross-check.

| Method & path                                  | Purpose                                           |
|------------------------------------------------|---------------------------------------------------|
| `POST /reports`                                | file an assembled DPMSR → `201` (or `200` on a retry) |
| `GET  /reports/{entityReference}?companyId=`   | status + persisted validation messages            |
| `GET  /lookups/{jurisdiction}/{set}`           | FIU reference sets for the wizard's dropdowns      |

Request body (`AccountingReportRequest`) wraps the **full-fidelity** `DpmsrReportPayload` — byte-for-byte the
contract `POST /api/v1/reports` takes from the AML cockpit — with the accounting `companyId` the org claim is
checked against:

```json
{ "companyId": 881,
  "payload": { "entityReference": "ACC-881-REC-0001", "reportDate": "2026-10-07T00:00:00Z",
               "reason": "…", "action": "…", "indicators": ["DPMSJ"], "location": { … },
               "parties": [ { "entityMyClient": { … } } ], "goods": [ { … } ] } }
```

Response (`AccountingReportResponse`):

```json
{ "entityReference": "ACC-881-REC-0001", "reportId": "…", "status": "VALID",
  "duplicate": false, "validationMessages": [] }
```

- **The ERP owns `entityReference`** — `ACC-<companyId>-<cashDocNo>`, injected by accounting, and it is the
  idempotency key: a retry answers `200` with `duplicate:true` and the existing report, never a `409`.
- **`INVALID` is a success**, not an HTTP error: the report exists with its findings in `validationMessages`
  so the ERP can store the status and show the reasons.
- **Reporting person** is omitted by the wizard and filled server-side from the tenant's active
  `tenant_goaml_person`; when a tenant has none, an ACTIVE MLRO app user's name is used so the filing is not
  INVALID on `reporting_person` alone.
- **Same submit gate** as `POST /transactions` (`tenant_goaml_config.auto_submit` → submit now, else keep the
  draft and notify the MLROs).
- **Audited** as `REPORT.FILED_BY_ERP` with the assertion's `sub` (the ERP user id) in `actor_email`.
- `submission_date` is also accepted spelled **`reportDate`** (a `@JsonAlias`): the goAML schema allows either
  element, and the shared browser assembler (`dpmsrPayload.ts`) emits that spelling.

| Failure                            | Status |
|------------------------------------|--------|
| missing/invalid service assertion  | `401`  |
| org claim missing or ≠ `companyId` | `401`  |
| `companyId` not mapped to a tenant | `404`  |
| unknown `entityReference` on GET   | `404`  |

### The lookup sets (F3)

`GET /api/v1/integration/accounting/lookups/{jurisdiction}/{set}` is the **same handler** as the SCREENING-side
`GET /api/v1/integration/lookups/{jurisdiction}/{set}` — `IntegrationLookupController` maps both paths. The
prefix is what selects the key: `IntegrationAuthFilter` verifies anything under `…/integration/accounting*`
against the **ACCOUNTING** trusted service and everything else under the integration root against
**SCREENING**. So the ERP's goAML filing wizard (through vyttah-accounting, ACCOUNTING-signed) and the AML
cockpit (through customer-service, SCREENING-signed) each read the same reference data while presenting its own
issuer — no shared key, no second verifier, no duplicated handler. Each prefix **rejects the other issuer's
assertion** (`IntegrationLookupDualPathE2ETest`).

No org cross-check on these routes, unlike the report routes above: the sets are FIU reference data, identical
for every tenant, so there is no tenant to protect and the caller names no company.

The sets vyttah-accounting offers the wizard: `countries`, `currencies`, `item_types`, `item_status`,
`report_indicators`, `legal_forms`, `party_roles`, `funds`. Accounting keeps its own allowlist (the browser
reaches its proxy with an ordinary user JWT) and caches each set for 10 minutes, which also keeps the wizard's
dropdowns from minting a single-use assertion each.

---

## Model 2c — report-status events back out (goAML → AML → ERP)

Everything above is inbound: a sibling system presents an assertion and goAML answers. This is the one path
where **goAML is the caller** (S2, decisions D22/D23). It exists because the ERP operator who filed a cash
receipt needs to see what the FIU said about it, and the alternative — the ERP polling goAML — would mean
giving the ERP a goAML identity per user and a reason to keep asking.

```
review / submission transition
  └─ public.report_status_outbox row            (same work as the status change)
       └─ ReportStatusPublisher  @Scheduled
            └─ POST {aml-base-url}/api/v1/integration/goaml/report-status
                 X-Service-Assertion: iss=GOAML  aud=aml  org=<tenant's SCREENING ref>  sub=<actor|system>
                 └─ AML verifies + de-duplicates on eventId
                      └─ PUT  {erp}/accounting-service/integration/goaml-filings/{entityReference}/status
```

**What is published.** One event per transition, for tenants that have an `ACCOUNTING` external ref and only
while `goaml.integration.outbound.enabled` is true:

| Transition | Where it comes from | `status` on the event |
|---|---|---|
| submitted for review | `ReportReviewService.submitForReview` | `PENDING_REVIEW` |
| approved | `ReportReviewService.approve` | `APPROVED` |
| rejected by the reviewer | `ReportReviewService.reject` | `REJECTED` |
| sent to the FIU | `SubmissionService.submit` | `SUBMITTED` |
| the FIU refused it at submit time | `SubmissionService.submit` (`B2bValidationException`) | `REJECTED` |
| transport / auth failure | `SubmissionService.submit` | `FAILED` |
| the FIU's verdict on a later poll | `SubmissionService.refreshStatus` | `ACCEPTED` / `REJECTED` |

**`status` is the transition, not the report's status afterwards.** A reviewer's rejection puts the report back
to `VALID` so it can be reworked, and a submission the FIU refuses is restored to `VALID`/`APPROVED` by
`submit`'s own error handling. In both cases the event says `REJECTED`, because the ERP is being told what
happened to *its filing* — not what goAML's work queue looks like now. A poll that finds no change publishes
nothing.

**The body.**

```json
{ "eventId": "<outbox row id>", "tenantId": "…", "amlCompanyId": "<SCREENING ref>",
  "erpCompanyId": "<ACCOUNTING ref>", "reportId": "…", "entityReference": "ACC-3-REC-2026-000002",
  "status": "APPROVED", "remark": "<reviewer remark | FIU errors>", "actorUserId": "…|null",
  "occurredAt": "2026-10-08T09:30:00Z" }
```

`occurredAt` is ISO-8601 and is what both receivers order by — never arrival time, so a retry that overtakes a
later event cannot move a status backwards. `actorUserId` is null when the FIU answered a background poll.

**Delivery.** At-least-once. A row is marked sent only on a 2xx, so a lost acknowledgement causes a
redelivery rather than a lost event; AML de-duplicates on `eventId` and accounting ignores an `occurredAt` that
is not newer than what it holds. Failures walk a bounded backoff — 1 m, 5 m, 30 m, 2 h, then 12 h forever — with
the reason in `last_error`. There is no dead-letter state: an answer the FIU has already given is worth
delivering late, and a permanent misconfiguration should stay visible rather than become a dropped row.

**Keys.** goAML signs with `goaml.integration.outbound.private-key-pem` (PKCS#8 RSA, env/secrets only). Its
public half is registered on the AML side as a `GOAML` row in that platform's `trusted_service` table — the
mirror of how goAML holds the AML platform's `SCREENING` key.

> **`aud` is a single JSON string here, not an array.** goAML signs with jjwt 0.12, whose
> `audience().add(…)` serialises `aud` as an array even for one value; the AML platform verifies with jjwt
> 0.11.5, whose `requireAudience(…)` reads it as a `String`. The array form would therefore fail verification
> on every event, and a verifier must not echo token contents — so the only symptom anywhere would be a bare
> 401. `OutboundAssertionSigner` uses the legacy single-string form deliberately and
> `OutboundAssertionSignerTest` asserts the serialised bytes rather than the parsed claim.

**Topology.** The publisher honours `goaml.scheduler.enabled`, the same master switch the FIU poller uses, so
the Helm chart's single dedicated pod does the publishing and N web replicas do not send N copies of every
event. It takes no database lock, so two instances with the switch on would both claim the same rows — bounded
by the receivers' de-duplication, not by this side.

---

## What the standalone deployment sees

Nothing changes. `auth.mode` defaults to `native`, the federated endpoint is `404`, and the
integration endpoints are inert with no registered `trusted_service`. The suite features light up only
when an operator registers a trusted service and the org/identity mappings.

---

## Screening integration (the AML software)

The AML screening software (Vyttah `customer-service`) integrates two ways: **Model 1** (embedded — drive
the report API with a federated JWT, available to it today) and a dedicated **screened-party push**.

### Push a screened customer (server-to-server)

`POST /api/v1/integration/screening/subjects` — `X-Service-Assertion` authed for `SourceSystem.SCREENING`,
tenant resolved from `companyId` via `tenant_external_ref`. Body is the `ScreeningPartyPayload` contract: an
already-**resolved** view of a customer (the screening side maps its master FKs → ISO/goAML codes before
pushing, like accounting):

- `subjectType` `NATURAL` | `LEGAL`, the customer (`natural` / `legal`), plus `directors[]`, `shareholders[]`,
  `ubos[]` and a `sanctions` block (`riskFlag` + `hits[]`).

→ **`202`** with the mapped goAML party set + the PEP/sanctions context recorded on the customer party.
goAML stores it as a reusable **`screened_subject`**, idempotent on `SCR-<companyId>-<customerUid>`.

| Method & path | Purpose |
|---|---|
| `POST /api/v1/integration/screening/subjects` | push/refresh a screened customer |
| `GET  /api/v1/integration/screening/subjects/{customerUid}?companyId=` | fetch one |
| `GET  /api/v1/integration/screening/subjects?companyId=` | all from this company |

### Platform admin endpoints (AML admin-service → goAML, server-to-server)

`X-Service-Assertion` authed for `SourceSystem.SCREENING`; the assertion `org` claim is the AML `companyId`.

| Method & path | Purpose |
|---|---|
| `POST /api/v1/integration/admin/tenants/status` | bulk "is this companyId provisioned?" (not tenant-scoped) |
| `POST /api/v1/integration/admin/tenants` | provision the tenant for `org` + map `SCREENING/<org>` |
| `PUT  /api/v1/integration/admin/users/{externalUserId}` | create/update the goAML user backing an AML user |
| `POST /api/v1/integration/admin/tenants/external-refs` `{sourceSystem, externalOrgRef}` | add another system's org ref to the **caller's** tenant (goAML ↔ ERP wiring: `ACCOUNTING/<erpCompanyId>` when an ERP workspace is linked) → `201`; `409` if that ref is already mapped anywhere; `404` if `org` is not provisioned; `400` if `org` is missing |
| `DELETE /api/v1/integration/admin/tenants/external-refs?sourceSystem=&externalOrgRef=` | remove it again (ERP workspace unlinked) → `204`; `404` if absent or owned by another tenant; `409` when asked to remove the caller's own `SCREENING` mapping |

After the `ACCOUNTING` ref exists, accounting pushes with `companyId=<erpCompanyId>` resolve the same tenant as the
screening pushes with `companyId=<amlCompanyId>` — one tenant, two external ids. Both changes are audited on the
target tenant (`ADMIN.TENANT_EXTERNAL_REF_CREATE` / `…_DELETE`).

### Seed a report from a screened subject (user-facing)

A goAML user (the SPA's **Screening** page) browses screened subjects and seeds a DPMSR draft from one —
the subject carries the **parties** (customer → Entity/Person; directors → entity directors;
shareholders/UBOs → additional parties), the user supplies the goods + reporting MLRO + report reference:

| Method & path (JWT, tenant-scoped) | Role | Purpose |
|---|---|---|
| `GET  /api/v1/screening/subjects` (+ `/{ref}`) | ANALYST/MLRO/TENANT_ADMIN | browse |
| `POST /api/v1/screening/subjects/{ref}/seed-report` | ANALYST/MLRO | create a DPMSR draft from the subject |

**Person-party note:** a goAML DPMSR **person** party requires gender, birthdate, country-of-birth, a national
id, nationality, residence, a phones block, a full identification document (coded type + issue/expiry dates)
**and** a `tax_reg_number` flag. A screening profile rarely carries all of these, so a person-seeded report is
a **draft the analyst completes** before submission; an **entity**-party customer (legal) seeds a fully VALID
report directly. (Mapping fills gender→`-`, country-of-birth/residence→nationality fallbacks, and uses the
required top-level `id_number`; the optional identifications block is omitted to avoid partial-data XSD errors.)
