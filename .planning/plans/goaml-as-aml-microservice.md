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

**A1 — goAML auth bridge in the AML frontend.**
- `axiosInstanceGoaml` + a goAML-JWT provider: (Option 1) `POST /api/v1/goaml/token` on AML backend mints the
  assertion + federated-exchanges → JWT; (Option 2) `POST /api/v1/auth/login` to goAML. Cache token, attach
  Bearer, refresh on 401. (Option 1 also needs the thin AML-backend `GoamlTokenController` — reuse the existing
  assertion signer / `GoamlScreeningClient`.)
- `auth.js` goAML getters: `goamlCreateReport`, `goamlListReports`, `goamlReportDetail`,
  `goamlSubmitForReview/approve/reject`, `goamlSubmit`, `goamlDownloadXml`, `goamlLookup(set)`,
  `goamlReportabilityCheck`.

**A2 — "Create Transaction" page** (`src/app/(main)/create-transaction/page.tsx` + nav item).
- Pick customer (legal/natural) → fetch KYC via existing getters → **prefill** the party block (resolve FK ids
  → codes) → user completes goods[] (item type/status/value/currency from goAML lookups), indicators
  (multi-select, ≥1), report header (reason/action), and the **§5 gap fields** → optional
  **reportability check** → assemble `DpmsrCreateRequest` → `goamlCreateReport` → show `reportId` +
  VALID/INVALID + validation messages. (No AML persistence — the report lives in goAML.)

**A3 — "Approve Transaction" page** (`src/app/(main)/approve-transaction/page.tsx` + nav item).
- List goAML reports (`GET /api/v1/reports` or `/review-queue`) with status badges → open **detail**
  (`/detail`: filing + validation + review trail) → **Submit for review** / **Approve** / **Reject**+remark →
  **Submit to FIU** (MLRO) → **Download XML** (`/xml`). Maps 1:1 to goAML's review workflow; the AML UI is a
  thin driver.

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

## 9. Verification (end-to-end)

- **Auth:** browser obtains a goAML JWT (Option 1 mint, or Option 2 login); a direct `GET /api/v1/reports`
  from `:3001` succeeds (CORS + Bearer), tenant-scoped.
- **Create:** pick a customer → assemble `DpmsrCreateRequest` → create → `reportId` + VALID (entity party) /
  draft (person party); reportability check returns the right verdict.
- **Approve:** submit-for-review → approve (MLRO) → submit (502 without FIU creds, handled) → status.
- **Download:** `GET /{id}/xml` streams the marshalled goAML XML in the cockpit.
- **No drift:** screening + other AML features unchanged; standalone goAML defaults unchanged.
