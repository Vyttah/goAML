# Plan — goAML as a microservice the AML cockpit calls **directly** (frontend-direct DPMSR)

> **Status:** APPROVED — building. **Auth model DECIDED 2026-06-10: Option 1 (Federated SSO via a thin
> AML-backend token-mint).** Supersedes the AML-backend-proxy shape of
> the transaction flow built in Phase C/D (`plans/suite-cockpit-integration.md`); the **goAML side is already
> built and is exactly what this needs** — near-zero goAML code.
>
> **Author's note (durable memory):** this file is the system-of-record for the pivot. Keep it in sync with
> `STATE.md` + `discussion-log.md` as steps land (same rhythm as every prior phase).

---

## 1. The pivot (what the user asked for)

> "Consider goAML as a new microservice for the AML software. On the AML frontend there will be a **Create
> Transaction** menu and an **Approve Transaction** menu, and after approving, an option to **download the
> XML**. The frontend will **call the goAML service directly** to create, approve, and generate/validate the
> XML. Initial data (entity / shareholder / other details) it fetches from the **AML services** and assembles
> the **whole payload on the frontend**. Transaction + DPMSR + goAML data **stay in the goAML service**.
> Screening and the rest of the AML features are unaffected."

### What changes vs what's already built (Phase C/D)

| Dimension | Already built (Phase C/D — AML-backend proxy) | This pivot (frontend-direct) |
|---|---|---|
| Who calls goAML | AML **customer-service** proxies (`GoamlFilingService`, lookup proxy, XML proxy) | AML **frontend** calls goAML REST **directly** |
| Where the deal lives | AML DB (`goaml_transaction` + indicators, Flyway V102/V103) | **Only in goAML** (no AML persistence) |
| Payload assembly | AML backend `assembleSubject()` re-derives parties at file-time | **Frontend** fetches KYC + assembles the DPMSR payload |
| Lookups | AML proxy → goAML | Frontend → goAML lookups **directly** |
| Approval | AML deal-approval gate (`goaml_filing_approval_log`, V103) **then** goAML review | **goAML's own** review/approve workflow, driven from the AML UI |
| AML menus | one "goAML Filing" screen | **two** menus: **Create Transaction**, **Approve Transaction** (+ Download XML) |

**Net:** the new flow **retires the AML-side deal module + proxies** for transactions (they live on the
**unmerged** `feature/goaml-integration` branch, so nothing in AML mainline is disturbed — low stakes). The
**goAML report/review/submit/lookup/reportability API is reused unchanged.** Screening and all other AML
features are untouched.

---

## 2. Target architecture

```
 AML Frontend_Customer (Next.js, :3001)                         goAML service (:8090)  ── system-of-record
 ────────────────────────────────────────                       ─────────────────────────────────────────
 [Create Transaction]                                            POST /api/v1/auth/login            (native)
   pick customer ─► AML customer-service (:8081)                   └─ OR ─┐
        getNaturalCustomerDataById / getLegalCustomerDataById      ┌──────┘
        getDirector / getShareholder / getUbo + identifications   POST /api/v1/auth/federated/token  (SSO)
        getDropDownOptions(masters)                                  ▲  (assertion minted server-side only)
   user fills goods[] + indicators + report header + gap fields     │
   assemble DpmsrCreateRequest  ───────────────────────────────────┼─►  POST /api/v1/reports[/dpmsr]   (create+validate)
   (reportability pre-check) ─────────────────────────────────────►│    POST /api/v1/reportability/check
   lookups ────────────────────────────────────────────────────────┼─►  GET  /api/v1/lookups/ae/{set}
                                                                     │
 [Approve Transaction]                                               │
   list ───────────────────────────────────────────────────────────┼─►  GET  /api/v1/reports  ·  /review-queue
   open ───────────────────────────────────────────────────────────┼─►  GET  /api/v1/reports/{id}/detail
   submit-for-review / approve / reject ───────────────────────────┼─►  POST /api/v1/reports/{id}/submit-for-review|approve|reject
   submit to FIU (MLRO) ───────────────────────────────────────────┼─►  POST /api/v1/reports/{id}/submit
   [Download XML] ─────────────────────────────────────────────────┴─►  GET  /api/v1/reports/{id}/xml
```

Everything the AML frontend needs already exists on goAML. The **only** server secret that cannot move to the
browser is the **token signer** (see §3).

---

## 3. The auth crux — how a browser authenticates to goAML directly  ✅ DECIDED → Option 1 (Federated SSO)

**Verified hard constraint:** `POST /api/v1/auth/federated/token` requires an **RS256 assertion signed with
the source system's PRIVATE KEY** (`ServiceCredentialValidator` verifies it against `trusted_service.public_key_pem`).
**A private signing key must never live in a browser.** So a browser cannot mint the assertion itself.

But a browser **can** hold and use a goAML **user JWT**: `/api/v1/auth/login` is `permitAll` and browser-callable,
CORS is configurable (`goaml.web.allowed-origins` → `http://localhost:3001`, covers `/api/**`, allows
`Authorization` + `OPTIONS`), and every `/api/v1/reports*` call just needs `Authorization: Bearer <goAML JWT>`.

So "frontend calls goAML directly" is fully achievable **for every transaction operation** — the only question
is **how the browser obtains the goAML JWT**:

### Option 1 — Federated SSO via a thin AML-backend token-mint  ✅ CHOSEN (2026-06-10)
- On entering the goAML menus, the AML frontend calls **one** thin AML-backend endpoint
  `POST /api/v1/goaml/token` → AML backend mints the RS256 assertion (key already configured:
  `GOAML_INTEGRATION_PRIVATE_KEY_PEM`) for the logged-in user → calls goAML `/auth/federated/token` →
  returns the **goAML JWT** to the browser.
- The browser caches that JWT and calls **everything else on goAML directly** (create, review/approve,
  submit, lookups, reportability, xml).
- goAML: `GOAML_AUTH_MODE=both`; `trusted_service` (SCREENING) + tenant mapping already seeded; JIT provisions
  the user. **Role mapping:** the federated exchange must map the AML approver → goAML **MLRO** (approve/submit
  are MLRO-only); create + submit-for-review need only ANALYST.
- **Pros:** transparent SSO (no second login), per-user attribution in goAML's audit, reuses the
  already-built assertion signer + federated endpoint. The single token-mint is auth-bootstrap, **not** a
  transaction proxy — so the user's "frontend calls goAML directly" intent holds for all real operations.
- **Cons:** one AML-backend endpoint remains (the token mint).

### Option 2 — Native goAML login (separate credential)
- The browser logs into goAML via `/api/v1/auth/login` (per-user goAML accounts, or a per-tenant goAML
  service account the AML app logs in as) → goAML JWT directly. **Zero** AML-backend involvement.
- goAML: `GOAML_AUTH_MODE=native` (default) + CORS for `:3001`.
- **Pros:** purely frontend→goAML, including auth; simplest infra (no assertion/trust).
- **Cons:** a second credential to manage; a shared service account loses per-user attribution; per-user
  goAML accounts are manual to provision.

### Option 3 — per-call service assertion from the browser — ❌ NOT VIABLE (key in browser).

**Recommendation: Option 1.** It reconciles "direct" with the security reality and reuses everything built.

---

## 4. Payload contract — keep frontend assembly tractable

`POST /api/v1/reports` binds the **full-schema** `DpmsrReportPayload` (xjc leaf types — heavy to assemble in a
browser). `ReportService.create(DpmsrCreateRequest, …)` (the **curated** shape) already exists internally.

**Recommendation (additive, standalone-safe):** expose a curated REST entry
**`POST /api/v1/reports/dpmsr`** → `DpmsrCreateRequest` → `ReportService.create(...)` → same
`CreateReportResponse {reportId, status, validationMessages}`. The frontend then assembles the **curated**
contract (header + reporting person + location + parties + goods[]) instead of the full xjc tree.

*Alternative (no goAML change):* port the goAML SPA transform `frontend/src/lib/dpmsrPayload.ts`
(`toDpmsrPayload`) to the AML frontend and POST `DpmsrReportPayload` to the existing endpoint. More frontend
work; chosen only if we want zero goAML edits.

---

## 5. Verified contract reference (from the 2026-06-10 verification fan-out)

**goAML report lifecycle** — all user-JWT, tenant-scoped (`controller/report/ReportController`):
| Purpose | Method & path | Body / returns | Role |
|---|---|---|---|
| Create (+validate) | `POST /api/v1/reports` | `DpmsrReportPayload` → 201 `{reportId, status VALID\|INVALID, validationMessages}` | ANALYST, MLRO |
| Create (curated) — *to add* | `POST /api/v1/reports/dpmsr` | `DpmsrCreateRequest` → same `CreateReportResponse` | ANALYST, MLRO |
| List | `GET /api/v1/reports` | `ReportView[]` (tenant-scoped) | ANALYST, MLRO, TENANT_ADMIN |
| Get | `GET /api/v1/reports/{id}` | `ReportView` | ANALYST, MLRO, TENANT_ADMIN |
| Detail (full filing + review trail + hasXml) | `GET /api/v1/reports/{id}/detail` | `ReportDetailView` | ANALYST, MLRO, TENANT_ADMIN |
| XML download | `GET /api/v1/reports/{id}/xml` | `application/xml` + `Content-Disposition` | ANALYST, MLRO, TENANT_ADMIN |
| Status (refresh from FIU) | `GET /api/v1/reports/{id}/status` | `StatusView {reportKey, status, errors}` | ANALYST, MLRO, TENANT_ADMIN |
| Submit-for-review | `POST /api/v1/reports/{id}/submit-for-review` | needs `review_required=true` else 409; `VALID→PENDING_REVIEW` | ANALYST, MLRO |
| Approve | `POST /api/v1/reports/{id}/approve` | `PENDING_REVIEW→APPROVED`; non-PENDING→409 | **MLRO only** |
| Reject | `POST /api/v1/reports/{id}/reject` | remark **mandatory** (else 400); `→VALID` | **MLRO only** |
| Review queue | `GET /api/v1/reports/review-queue` | `ReportView[]` (PENDING_REVIEW) | MLRO, TENANT_ADMIN |
| Submit to FIU | `POST /api/v1/reports/{id}/submit` | `VALID` (or `APPROVED` if review on) → FIU; `SubmissionView {submissionId, reportKey, status}` | **MLRO only** |
| Reportability | `POST /api/v1/reportability/check` | `{amount, currencyCode?, involvesPreciousMetalsOrStones?}` → `{reportable, reasons[], thresholdAed}` | ANALYST, MLRO, TENANT_ADMIN |
| Lookups (user) | `GET /api/v1/lookups/{jur}/{set}` | `{codes[], entries:[{code,label}]}`; `jur=ae`; sets `item_types`/`item_status`/`report_indicators`/`countries`/`currencies` | any authenticated (role-less) |

> No separate `validate` endpoint — **create returns the verdict** (`status` + `validationMessages`). The FIU
> submit returns 502 / "Secret not found" without per-tenant B2B creds (Phase E — external).

**AML Frontend_Customer mechanics** (`dev/AML/Frontend_Customer`):
- Axios factory `createAxiosInstance(baseURL)` in `utils/react-query/axios/axios.js`; instances
  `axiosInstanceUser` (:8080) + `axiosInstanceCustomer` (:8081). **Add** `axiosInstanceGoaml`
  (`NEXT_PUBLIC_API_GOAML_SERVICE_URL=http://localhost:8090/api/v1`) with a **goAML-JWT** interceptor (the
  default interceptor attaches the *AML* `localStorage.token` — goAML needs its own token, cached separately,
  refreshed on 401).
- Menu: static `navItems` in `components/layout/Sidebar.tsx`; routes `src/app/(main)/{route}/page.tsx`.
- Form primitives: `FormSelect` onChange `(e:{target:{id,name?,code?}})`, `FormMultiSelect` value =
  `MultiSelectOption[]`, `FormInput`/`FormDatePicker` (YYYY-MM-DD); repeating arrays via **useState + DataTable**
  (no `useFieldArray`). Lookups → `toOptions()` → `{id:code, name:label, code}`.

**AML KYC → DPMSR party getters** (`Frontend_Customer/utils/react-query/axios/auth.js` →
`Backend_Java/customer-service`):
- `getNaturalCustomerDataById` / `getLegalCustomerDataById`; `getDirector`/`getShareholder`/`getUbo` (+
  `…IdentificationsById`); `getDropDownOptions(types)` (countries/genders/nationalities/occupations/currencies/
  id-types — `{id, code, name, countryCode}`). Resolve FK ids → codes on the frontend via the masters map.

### Field gaps the **form must collect** (goAML needs them; AML KYC lacks/omits them)
- **occupation** (person + related parties) — captured in AML but currently unmapped.
- **residence country** (natural person) — not in AML KYC.
- **incorporation state** + **commercial name** (legal entity) — not distinctly in AML KYC.
- **full ID document block** (coded type + number + issue/expiry + issue country) + **multiple IDs** — AML maps
  only the first / Emirates ID.
- **tax registration number (TRN)** for legal entities — present in AML but must be ensured populated.
- A DPMSR **person** party also needs `country_of_birth`, a `<phones>` wrapper, and `tax_reg_number` to be
  fully VALID (else it seeds an analyst-completed draft — same rule the engine enforced in Phase 1.5c).
- A filing needs **≥1 report indicator** and the tenant a **positive `rentity_id`** (already auto-injected as a
  tenant default — Phase A `tenant_goaml_person`).

---

## 6. Build breakdown (proposed; each step = atomic commit + planning-doc sync, same rhythm)

> **G# = goAML repo · A# = AML repo (`feature/goaml-integration` or a fresh branch).** goAML touches are tiny;
> the weight is on the AML frontend.

**G1 — goAML enablers (config + 1 small endpoint).**
- **G1.1 ✅ DONE** — added curated `POST /api/v1/reports/dpmsr` (`DpmsrCreateRequest` → existing
  `ReportService.create(...)` → `CreateReportResponse`; ANALYST/MLRO). `ReportController.createDpmsr` +
  `ReportApiE2ETest.curatedDpmsrEndpointCreatesValidatesAndPersists` (create → VALID → list → XML → idempotency
  409). Additive, standalone-safe — the existing full-fidelity `POST /api/v1/reports` is untouched. Targeted
  E2E green.
- **G1.2 — config (runtime, no code).** Launch goAML with `GOAML_AUTH_MODE=both` (Option 1 needs the federated
  on-ramp **and** keeps native login) and `GOAML_ALLOWED_ORIGINS=http://localhost:3001` (dev; the prod cockpit
  origin later) — both are existing env knobs (`application.yml` `goaml.auth.mode` / `goaml.web.allowed-origins`).
- **G1.3 ✅ DONE — approver → MLRO via a per-trusted-service `default_role`.** The federated JIT default is
  **ANALYST**, but the cockpit "Approve Transaction" calls goAML's **MLRO-only** approve/submit. Rather than
  fragile per-user seeding, added a **`trusted_service.default_role`** (shared **V6** migration; nullable →
  ANALYST fallback, so existing services unchanged) honoured in `DefaultFederatedAuthService.provision`. The
  dev seeder now registers **SCREENING with `jit_provisioning=true` + `default_role=MLRO`**, so any cockpit user
  hitting `/auth/federated/token` is auto-provisioned as an MLRO in the demo tenant — no manual per-user step.
  `FederatedTokenE2ETest.jitHonoursTheTrustedServiceDefaultRole` proves it. (Standalone-safe; the screening
  service-push path is unaffected — JIT only governs federated exchange.) Full gate green (one unrelated
  WireMock socket flake, confirmed by isolated re-run).

**A1 ✅ DONE — goAML auth bridge (both repos).**
- **A1a (AML `customer-service`, commit `7a399e0`):** `GoamlTokenController` `POST /api/v1/goaml/token`
  (`@ConditionalOnProperty goaml.integration.base-url`) mints the RS256 assertion for the logged-in user +
  exchanges it via `GoamlScreeningClient.federatedToken` → goAML `/auth/federated/token`, returning the goAML
  JWT. Reuses `GoamlAssertionService` (+ `sourceSystem()` accessor) + `GoamlTokenResult`. Test:
  `GoamlScreeningClientTest.federatedTokenExchanges…` (MockRestServiceServer). Built green (Temurin21+IntelliJ
  Maven); only my files committed (user's poms/yml untouched).
- **A1b (AML `Frontend_Customer`, commit `0f57680`):** `axiosInstanceGoaml` — dedicated instance that
  bootstraps + caches the goAML JWT (de-duped in-flight), attaches **only** the goAML Bearer (no AML
  token/company header, no custom headers — matches goAML CORS), with its **own** 401 handler that refreshes
  the goAML token without firing the AML logout/redirect. `auth.js` goAML getters: `goamlCreateReport`
  (curated `/reports/dpmsr`), `goamlListReports`/`goamlReviewQueue`/`goamlGetReportDetail`/`goamlGetReportStatus`,
  `goamlSubmitForReview`/`goamlApproveReport`/`goamlRejectReport`, `goamlSubmitReport`, `goamlLookup(set)`,
  `goamlCheckReportability`, `goamlDownloadReportXml` (Blob). `NEXT_PUBLIC_API_GOAML_SERVICE_URL`
  (+ optional `NEXT_PUBLIC_GOAML_WEB_URL`) in `.env.example`. **Frontend build verified in A2** (first consumer).
- **goAML config (G1.2, runtime — for the live E2E):** launch goAML with `GOAML_AUTH_MODE=both` +
  `GOAML_ALLOWED_ORIGINS=http://localhost:3001`; customer-service with `GOAML_INTEGRATION_BASE_URL` + the dev
  private key (already the case in the running dev session). The dev SCREENING service (G1.3) JIT-provisions the
  cockpit user as MLRO, and its company must map to the demo tenant (`GOAML_DEV_SCREENING_COMPANY_ID` = the AML
  companyId, e.g. `vyttah`).

**A2 ✅ DONE** (AML `Frontend_Customer`, commit `ca45180`) — one-page Create Transaction builder calling goAML
directly: customer pick (legal|natural) → KYC prefill (master-id→code mapped; legal incl. best-effort
directors) → gap fields (incorporation state / commercial name / residence / TRN / full ID doc) → goods rows
(goAML lookups) → indicators (≥1) + reason/action → optional reportability check → assemble curated
`DpmsrCreateRequest` → `goamlCreateReport` (`POST /reports/dpmsr`) → reportId + VALID/INVALID + messages; no
AML persistence; MLRO auto-injected. New nav + route. **Gate green (Node 22): tsc + lint clean, `next build`
OK, `/create-transaction` 9.06 kB — verified the A1b auth bridge compiles/wires.** Field-mapping fidelity
(esp. nationality/occupation code-sets) to be confirmed in a live cockpit pass. Detail spec below.

**A2 detail (as built)** (`src/app/(main)/create-transaction/page.tsx` + nav item):

*Structure* — mirror `DpmsrBuilderPage` (goAML SPA, the proven layout) but in AML's form primitives
(FormSelect/FormMultiSelect/FormInput/FormDatePicker + useState/DataTable; no Form.List/useFieldArray). One
multi-section page under a new **"Create Transaction"** `navItem`, component dir
`components/CreateTransactionComponent/`.

*Step 0 — masters + lookups (parallel `useQuery`):* `getDropDownOptions('countries,genders,nationalities,occupations,currencies,id-types')`
(AML masters → `toOptions`) + `goamlLookup('item_types'|'item_status'|'report_indicators')` (goAML codes).
Build a FK-id→code resolver from the masters (nationalityId→ISO, genderId→code, countryId→ISO, idTypeId→code).

*Step 1 — pick customer:* a customer-kind toggle (Legal | Natural) + a `FormSelect` of customers
(`getLegalCustomer('page=0&size=200')` / the natural-list getter) — reuse the existing filing-component picker.

*Step 2 — load + PREFILL party (the core new work):* on select, fetch KYC and map → the party block:
- **Legal →** `getLegalCustomerDataById` + `getLegalCustomerRelationDetails` (directors) + per-director
  `getDirectorIdentificationsById` → `Party.entity` (name←legalName, incorporationNumber←licenseNumber/inc#,
  incorporationCountryCode←countryOfIncorporationId→ISO, dateOfIncorporation, phone) + `directors[]`
  (firstName/lastName/nationality→ISO/residence←countryOfResidenceId→ISO/birthdate + first id→idNumber).
- **Natural →** `getNaturalCustomerDataById` → `Party.person` (firstName/lastName, gender→code,
  birthdate←dob, nationality→ISO, idNumber←emiratesId, phone, address).
- Prefill is **editable** — every field lands in a form control the user can correct.

*Step 3 — fill the GAP fields (§5; required for VALID):* occupation (FormSelect occupations→code), residence
country (FormSelect countries→ISO) for a natural person, incorporation state + commercial name (FormInput) for
an entity, **TRN** (FormInput) + a **full ID document** (type→code, number, issue/expiry dates, issue country)
for a natural-person party. Surface inline that a **person party** without TRN + full ID doc will create an
analyst-completed **draft** (engine rule), an entity party creates VALID.

*Step 4 — goods[] (useState + DataTable):* add/remove rows; per row itemType (lookup item_types, req),
statusCode (item_status), estimatedValue (req), currencyCode (currencies, default AED), + optional itemMake/
description/size/sizeUom/registrationDate/disposedValue/registrationNumber/identificationNumber/statusComments.

*Step 5 — header + indicators:* reason, action (FormInput); indicators (FormMultiSelect report_indicators,
**≥1 required**); reportingPerson **omitted** (goAML auto-injects the tenant MLRO — Phase A); submissionDate
defaults now.

*Step 6 — optional reportability pre-check:* a "Check reportability" button →
`goamlCheckReportability({amount: Σ estimatedValue, currencyCode, involvesPreciousMetalsOrStones:true})` →
show `{reportable, reasons, thresholdAed}` (advisory; doesn't block submit).

*Step 7 — assemble + create:* build the curated `DpmsrCreateRequest` (yup-validated) →
`goamlCreateReport(payload)` → on success show `reportId` + `status` (VALID/INVALID) + validationMessages
table; a "Go to Approve Transaction" link. **No AML persistence** — the report lives in goAML.

*Gate:* `tsc --noEmit` + `next lint` clean + `next build` green (Node ≥18.18 via nvm 22) — **this is the first
real consumer of A1b, so it verifies the auth bridge compiles + wires**. Commit only my files.

*Prereq for a live VALID create:* the demo tenant needs a positive `rentity_id` in `tenant_goaml_config`
(see §8a) — seed once.

**A3 ✅ DONE** (AML `Frontend_Customer`, commit `820dc08`) — Approve Transaction page: lists goAML reports
(`goamlListReports`) with status badges; per-status row actions drive the workflow directly against goAML —
VALID → **Submit for review** / **Submit to FIU**; PENDING_REVIEW → **Approve** / **Reject**+remark (inline);
APPROVED → **Submit to FIU**; SUBMITTED/ACCEPTED/REJECTED → **Refresh status**; any → **Download XML** +
**Details**. The inline **Details** panel loads `/detail` (`goamlGetReportDetail`) → header + review trail +
parties + goods + validation messages + raw-filing fallback. approve/reject/submit are MLRO-gated server-side;
errors surfaced from goAML's response body. Optional "Open in goAML" deep-link (`NEXT_PUBLIC_GOAML_WEB_URL`).
New nav + route.

**A4 — wire-up + lookups direct + docs.**
- Repoint the (now-direct) lookup calls at goAML; retire the old AML lookup/XML proxies for this flow (leave
  the dormant Phase C/D deal module on its unmerged branch — do **not** rip out). Update `STATE.md` +
  `discussion-log.md`; note the env vars in the AML `.env.example`.

---

## 7. Files

**goAML (small):**
- *Edit/config:* `application.yml` / env — `goaml.auth.mode`, `goaml.web.allowed-origins`.
- *New (if §4):* `controller/report/…` curated `POST /api/v1/reports/dpmsr` (+ E2E). Reuses
  `service/report/ReportService.create(DpmsrCreateRequest,…)` — no new report logic.

**AML `Backend_Java/customer-service` (Option 1 only):**
- *New:* `GoamlTokenController` (`POST /api/v1/goaml/token`) → reuse the existing RS256 assertion signer +
  `GoamlScreeningClient` to federated-exchange → return the goAML JWT.

**AML `Frontend_Customer` (the weight):**
- *Edit:* `utils/react-query/axios/axios.js` (`axiosInstanceGoaml` + goAML-JWT interceptor),
  `utils/react-query/axios/auth.js` (goAML getters), `components/layout/Sidebar.tsx` (2 nav items),
  `.env.example` (`NEXT_PUBLIC_API_GOAML_SERVICE_URL`, `NEXT_PUBLIC_GOAML_WEB_URL?`).
- *New:* `src/app/(main)/create-transaction/page.tsx` + `components/CreateTransaction*` (customer picker, party
  prefill, goods/indicator/header forms, payload assembler);
  `src/app/(main)/approve-transaction/page.tsx` + `components/ApproveTransaction*` (list + detail + workflow
  actions + Download XML).

---

## 8. Constraints / risks

- **Security:** the token signer (private key) stays **server-side** (Option 1) — never shipped to the browser.
  Standalone goAML is unaffected (defaults: `auth.mode=native`, blank CORS, no `trusted_service`).
- **Role mapping (Option 1):** approve/submit are **MLRO-only** — the federated AML approver must map to a goAML
  **MLRO**, or those actions 403. Decide role-mapping in the exchange (or pre-provision `external_identity`).
- **Data ownership:** all DPMSR/report data is in **goAML**; the AML cockpit is a stateless driver (no
  `goaml_transaction` writes in this flow). The unmerged Phase C/D deal module stays dormant (no AML mainline
  change).
- **Field gaps (§5):** the Create form must collect occupation / residence / incorporation-state /
  commercial-name / full-ID / TRN; a person party without the full ID doc + TRN seeds an **analyst-completed
  draft** (engine rule), not a VALID report.
- **Live FIU submit** still needs per-tenant B2B creds (Phase E — external); without them, submit → 502.
- **Node:** the AML `next build` needs Node ≥18.18 (the team uses nvm Node 22); the goAML SPA tests still run on
  Node 18 (unrelated).

## 8a. Live verification — goAML half (2026-06-10) ✅

Ran a self-contained live smoke (`dev-local/goaml-direct-verify.sh`, gitignored) against an **isolated**
goAML on `:8099` over a throwaway `goaml_smoke` DB (running stack untouched; both torn down after). Used the
dev RSA key to mint a real SCREENING assertion — **no AML login needed**. All green:
1. **Federated exchange** `POST /auth/federated/token` → goAML JWT.
2. **JWT carries `roles:["MLRO"]`** → **G1.3 default_role works live** (cockpit user can create *and*
   approve/submit).
3. Direct **`GET /api/v1/reports`** (browser-style Bearer) → 200, tenant-scoped.
4. Direct **`GET /lookups/ae/item_types`** → code+label entries.
5. Curated **`POST /api/v1/reports/dpmsr`** → **VALID** → **G1.1 works live**.
6. **CORS** preflight from `http://localhost:3001` → `Access-Control-Allow-Origin` echoed → **G1.2 works live**.

**Finding (config prereq, not a defect):** the dev seeder creates **no `tenant_goaml_config`**, so a fresh
tenant has `rentity_id=0` → reports build **INVALID** until a config row with a **positive `rentity_id`**
exists. Seed one before any live cockpit create:
```sql
INSERT INTO public.tenant_goaml_config (id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode)
VALUES (gen_random_uuid(), '<demo-tenant-id>', 'AE', 3177, 'https://goaml.test/uae', 'goaml/dev/creds', 'TOKEN');
```
**The customer-service `/goaml/token` leg** (A1a) is unit-tested (`GoamlScreeningClientTest`,
MockRestServiceServer); its *live* leg needs the AML user-service password (not guessed) — exercise it via
`dev-local/goaml-token-smoke.sh` once customer-service is rebuilt with A1a.

## 9. Verification (end-to-end)

- **Auth:** browser obtains a goAML JWT (Option 1 mint, or Option 2 login); a direct `GET /api/v1/reports`
  from `:3001` succeeds (CORS + Bearer), tenant-scoped.
- **Create:** pick a customer → assemble `DpmsrCreateRequest` → create → `reportId` + VALID (entity party) /
  draft (person party); reportability check returns the right verdict.
- **Approve:** submit-for-review → approve (MLRO) → submit (502 without FIU creds, handled) → status.
- **Download:** `GET /{id}/xml` streams the marshalled goAML XML in the cockpit.
- **No drift:** screening + other AML features unchanged; standalone goAML defaults unchanged.
