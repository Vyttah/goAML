# Plan — Suite cockpit integration (AML software ⇄ goAML) + standalone parity

> **Status:** PLANNED (grounded v2, 2026-06-09 — after reading the real AML stack at `dev/AML`).
> **Scope:** two repos — **goAML** (`dev/goAML`, this repo) and the **AML software** (`dev/AML`).
> **Origin:** a competitive teardown of **LexAML** (liveexshield.com — a deployed UAE AML suite with paying
> clients) + a full read of both codebases.

## The competitive finding (the "why")

LexAML is one monolith: Onboarding → Screening → RBA → Transaction (pick customer + enter the deal) →
Transaction Approval (maker-checker) → GoAML Xml (generate) → **manual upload to the goAML portal**. Several
UAE clients use it. Everything it bundles, Vyttah already has **live** in the AML software — except the
*transaction/deal* step and the *goAML filing* — and Vyttah ends in **B2B auto-submission**, not manual upload.

**Verdict:** the Vyttah suite (AML software + goAML) matches LexAML on breadth and beats it on automation,
**iff live B2B submission is proven** (externally gated — pursued in parallel). The work is to build the deal
+ the seam.

## Locked decisions

1. **Cockpit = the AML software** (owns all masters **and** transactions, like LexAML). **goAML = report
   generator + system-of-record:** it receives and stores the whole bundle (parties + goods + deal + master
   snapshot) + the generated XML + FIU status, so a goAML login shows the full transaction *and* its report —
   but goAML keeps **no separate editable masters**.
2. **The DPMSR "deal" is built IN the AML software** — a new transaction/filing module (entity + endpoints +
   Frontend_Customer screen). Officer picks a customer → enters the deal → "File to goAML" pushes the bundle.
3. **First we prove the pipe end-to-end** (Slice 1): RS256 auth bridge + map one company→tenant + push one
   customer's parties → goAML builds a DPMSR draft. No UI. De-risk auth + tenancy + mapping before building.
4. **Maker-checker on both planes** — AML business sign-off upstream; goAML adds its own MLRO/compliance review.
5. **MLRO fed by goAML, not AML** — goAML stores the reporting person as a tenant default and auto-injects it;
   the AML software sends none.

## Ground truth (what actually exists — verified in code, 2026-06-09)

### goAML (`dev/goAML`) — Spring Boot 3.3 / Java 21
- **System-of-record already there:** `report.input` (JSONB, NOT NULL) stores the full structured report; 
  `report.report_xml` the marshalled XML; `submission` the FIU status. → "store the whole transaction" is mostly
  a **view** task, not new storage. — `model/entity/report/Report.java`, `db/migration/tenant/V2__reports.sql`.
- **Phase 1.5 integration scaffolding is built:** federated SSO (`POST /api/v1/auth/federated/token` +
  RS256 `ServiceCredentialValidator` + `external_identity`/`trusted_service`), a **screening receiver**
  (`POST /api/v1/integration/screening/subjects` → `screened_subject` + `ScreeningPartyMapper`) modelled from
  the AML `customer-service` OpenAPI, the accounting raw-invoice push, and `tenant.external_org_ref` for org→tenant
  resolution. — `controller/integration/`, `service/integration/`, `model/entity/federated/`.
- **DPMSR full-schema fidelity:** `POST /api/v1/reports` binds `DpmsrReportPayload` (xjc-generated leaf types) →
  the XML carries every goAML element. B2B submit + poll + ZIP, XML view/download, attachments, notifications,
  CSV/XML import — all live (Phases 6–14).
- **4 lookup sets only** (countries, currencies, funds, transmode); **reporting person passed per-report** (no
  tenant default); **no maker-checker** (MLRO submit-gate only); **single schema 5.0.2**.

### AML software (`dev/AML`) — Spring Boot 4.0.2 / Java 21 + Next.js 15
- **Modules:** `aml-orm` (entities), `customer-service` :8081 (masters/KYC/screening/RBA/case), `admin-service`
  :8082 (Company/masters), `user-service` :8080 (auth). Postgres 16 + Flyway. **Frontend_Customer** :8083 =
  the compliance cockpit (Next.js/React 19/Tailwind/Zustand/React Query); Frontend_Admin :8084 = admin.
- **Masters (map to goAML parties):** `Customer`→`CustomerLegal`/`CustomerNatural`; related parties
  `Shareholder`/`Director`/`Ubo`/`Representative` (+ `*Identification` docs: idType/number/expiry/placeOfIssue
  + S3 refs); `CustomerBank`. Most attributes are FKs to `Master*` (country/gender/occupation/nationality…).
- **Tenancy:** `company_id` (String UUID) on every row + JWT claim + `X-Company-Id` header. `admin-service`
  owns `Company`. → maps to goAML `tenant.external_org_ref`.
- **Workflow:** `caseManagementStatus`/`kycStatus`/`riskMitigationStatus`; `CustomerRbaScore`;
  `CaseManagementDecisionLog` (PENDING→COMPLETED/REJECTED) — the maker-checker pattern to reuse.
- **Auth:** **HS256 shared-secret** (jjwt), issuer `vyattah-aml-user-service`, claims incl. `companyId` — **no
  RSA keypair**. customer-service OpenAPI at `/v3/api-docs`.
- **🔴 NO transaction/deal entity or screen** anywhere (onboarding/screening only) — the deal must be built.
- **🔴 Nothing calls goAML yet** (0 references) — the AML *sender* is greenfield (goAML's receiver exists).

## Field mapping — AML masters/deal → goAML DPMSR (the integration contract)

AML resolves its `Master*` FKs to **codes** before sending (ISO country, goAML gender/role codes, etc.) — the
screening payload is already "resolved-codes" shaped.

| goAML DPMSR element | Source in the AML software |
|---|---|
| Entity party `t_entity` (name, incorporation_*, business, tax_reg_number, phones, addresses) | `CustomerLegal` (legalName, dateOfIncorporation, countryOfIncorporation, businessActivity, trn, licenseNumber→entity_identification, phone, registeredOfficeAddress) |
| Person party `t_person_my_client` | `CustomerNatural` (names, genderId, dob, nationality, residence, occupation, pepStatus, emiratesId, countryOfBirth) |
| Director (`director_id`/related person) + role | `Director`/`Representative` (+ isManager/role) |
| Report parties / related persons (significance) | `Shareholder`, `Ubo` (+ shareholdingPercent, pepStatus) |
| `t_person_identification` (type/number/issue/expiry/issue_country) | `*Identification` (idType, idNumber, expiryDate, placeOfIssue) |
| Goods `t_trans_item` (item_type, estimated_value, status_code, size/uom, disposed_value, currency, registration_*) | **the new Deal entity** (built in AML) |
| Report header (entity_reference, submission_date, reason/description, action, indicators) | the new Deal entity (internal ref, date, description, action taken, indicators) |
| `reporting_person` (MLRO) | **goAML tenant default** (Phase A) — AML sends nothing |
| tenant / `rentity_id` | AML `company_id` → goAML `tenant.external_org_ref` → tenant's `rentity_id` |

Requiredness per field: goAML `docs/15-dpmsr-field-requirements.md` (note person-party needs gender+dob+id_number
+nationality1+residence+tax_reg_number+a phone block+≥1 full ID; entity party needs only `name`).

---

## Execution — slice/phase sequencing

Standing workflow (both repos): each step = **atomic commit** + that repo's full gate + planning sync +
`--no-ff` merge. goAML gate = `./gradlew test jacocoTestCoverageVerification` (+ frontend gate for SPA).

### ⭐ Slice 1 — Prove the pipe end-to-end (no UI)  ✅ CODE-COMPLETE (both repos)
Goal: one onboarded AML customer → goAML, tenant-scoped, over a signed service call.

> **Done.** goAML side on `main`: dev-seed trusted_service + company→tenant + active MLRO (`40f0fdd`),
> `companyId` Integer→String (`f03b199`). AML side on `Backend_Java` `feature/goaml-integration`:
> **S1.1** RS256 assertion signer (`18af3a3`), **S1.4a** push client + payload contract (`364472e`),
> **S1.4b** customer-load + `MasterCodeResolver` + `GoamlCustomerPushService` +
> `POST /api/v1/customers/{id}/push-to-goaml` (`74a0949`). Build via Temurin 21 + IntelliJ Maven
> (no install/Docker). goAML's receive→persist path was already proven by `ScreeningIntegrationE2ETest`;
> the AML side has unit tests (signer, client via MockRestServiceServer, assembler via mocked repos).
> Toolchain note: AML repo is `Backend_Java` (Maven, Java 21); build with
> `JAVA_HOME=~/.gradle/jdks/eclipse_adoptium-21-*/Contents/Home <IntelliJ>/maven3/bin/mvn -pl customer-service -am test`.
> **Remaining for a faithful report:** the goAML screening push stores a *subject* + maps *parties* (no goods),
> so it seeds a DPMSR draft; the **deal/goods** come from the AML deal module (Phase C). Representatives not
> pushed yet. Sanctions verdict not yet included in the push (optional).

- **S1.1 (AML)** — RS256 service-assertion capability in `user-service` (RSA keypair via env/secret +
  `ServiceAssertionService` that signs a short-lived assertion: iss=AML source, aud=goAML, exp). Expose the
  public key / register it.
- **S1.2 (goAML)** — Register a `trusted_service` row (source=SCREENING or a new AML source) with that public
  key + map one AML `company_id` → a goAML tenant (`external_org_ref`) with FIU config + (Phase A) reporting
  person. Confirm `ServiceCredentialValidator` accepts the AML assertion.
- **S1.3 (goAML)** — Integration intake that accepts the **AML-native bundle** (customer + resolved parties)
  and maps → a DPMSR draft, **idempotent on an AML ref**, reusing/extending `ScreeningPartyMapper`. (Deal is
  optional/stubbed in this slice — parties → draft.) MockMvc E2E.
- **S1.4 (AML)** — Minimal integration client in `customer-service`: given a customer, resolve `Master*`→codes,
  assemble the bundle, sign (S1.1), POST to goAML (S1.3). Prove a real customer → a goAML draft.

### Phase A (goAML) — Reporting-person as a tenant default, auto-fed  ✅ DONE (`feature/tenant-goaml-person`)
> Commits `68db70b` (A.1+A.2), `9e8ec9b` (A.3), `0e76ead` (A.4). Backend gate (test+jacoco) + frontend gate
> (typecheck/lint/68 vitest/build) green at each step.
- A.1 ✅ per-tenant `goaml_person` table (first/last/gender/ssn/id_number/nationality/email/occupation + is_active;
  one active per tenant via a partial unique index) + entity + repo.
- A.2 ✅ auto-inject on the curated create/validate/preview path when `reportingPerson` is omitted (now optional);
  injected person persisted in the report input; no default + none supplied → INVALID. Mapper made null-safe.
- A.3 ✅ admin REST `GET/POST/PUT/DELETE /api/v1/admin/goaml-persons` (TENANT_ADMIN; activating one demotes the rest).
- A.4 ✅ SPA "goAML reporting person (MLRO)" admin panel (table + add/edit + make-active + delete). → AML never sends the MLRO.

### Phase B (goAML) — Expanded lookups / dropdowns  ✅ DONE (`feature/phase-b-lookups`)
> Commits `908ec3e` (B.1+B.2), `2bd7ab9` (B.3). Backend gate (test+jacoco) + frontend gate
> (typecheck/lint/68 vitest/build) green at each step.
- B.1+B.2 ✅ three new `ae` lookup sets derived **directly from the 5.0.2 XSD enums** (codes + labels from the
  per-enum schema comments): `item_types.json` (63, `trans_item_type`), `item_status.json` (20,
  `trans_item_status`), `report_indicators.json` (423, `report_indicator_type`). `LookupService` auto-loads
  them (no service change). `LookupXsdConsistencyTest` cases added so they can never drift from the schema.
  No `ReportValidator` change — the XSD gate already enforces these enums.
- B.3 ✅ lookup API now serves `entries` `[{code,label}]` alongside `codes` (additive; `LookupService.entries()`
  retains the label per code). SPA `CodeSelect` shows "CODE — label", supports multi-select, and forwards the
  Form.Item id (was dropped). DPMSR builder: item type → `item_types`, status → `item_status`, indicators →
  multi `report_indicators`; item type is now a real goAML code (e.g. `GOLD`), not free text.
- **Feeds Phase C.3:** the AML deal form calls the same lookup API, so it gets code+label dropdowns for free.
- Deferred: party role / identification / contact / address code sets, and the live FIU-OData lookup sync
  (external follow-up; current values are the authoritative XSD enums, which is correct for structural validity).

### Phase C — The Deal module (the cockpit build, AML-side) + the real feed  ✅ COMPLETE (2026-06-10)

> **Ground-truth from research (2026-06-09):** goAML's report-creation path **already accepts goods + a full
> header** — `ScreeningSeedRequest.goods` (required) + `DpmsrCreateRequest.Goods` (14 item fields) →
> `DpmsrRequestMapper.goods()` → `TTransItem` XML; `reportService.create()` validates+persists. So C.4 is
> *wiring a service-authed entry point*, not new report logic. AML's `integration.goaml` package (Slice 1) pushes
> **parties only** and is the thing to extend. AML entities extend `AuditableEntity` (Long id, `uid`, `companyId`,
> `isDeleted`, audit); customer-service Flyway next = **V102**; Frontend_Customer = Next.js App Router +
> react-hook-form/yup + custom Tailwind form components + axios auto-auth.
>
> **Locked decisions:** (1) **one-shot filing endpoint** on goAML (not two-step subject+seed); (2) the AML deal
> form's item-type/status/indicator dropdowns come from a **customer-service proxy to goAML's lookup API** (no
> code drift; Phase B's code+label flows through); (3) **maker-checker stays Phase D** — C delivers DRAFT→FILED
> and proves the pipe first.

**Step order: C.4a → C.1 → C.2 → C.4b → C.3.** Each = atomic commit + that repo's full gate + planning sync;
goAML steps `--no-ff` to `main`, AML steps on `feature/goaml-integration`. The deal stores **goAML codes
directly** (item type/status/indicators are goAML's domain, not AML masters); currency reuses `MasterCurrency`.

- **C.4a (goAML)** ✅ **DONE** (`feature/phase-c-filing`, `500c7ca`) — service-assertion-authed **filing
  endpoint** `POST /api/v1/integration/screening/filings`: accepts the customer **party bundle
  (`ScreeningPartyPayload`) + deal goods (`DpmsrCreateRequest.Goods`) + header** in one call →
  `ScreeningPartyMapper` (parties) + goods + header → `DpmsrCreateRequest` → `reportService.create` →
  `{ filingRef, reportId, status, validationMessages }`. **Idempotent** on `FIL-<companyId>-<filingRef>`.
  MLRO auto-injected (Phase A); `submissionDate` server-stamped. `GET /filings/{filingRef}` for status.
  MockMvc+Testcontainers E2E proves a legal-customer + gold deal seeds a **VALID** DPMSR + idempotency + auth.
  **Two real DPMSR rules surfaced (carry into C.1–C.3):** the tenant needs a positive `rentity_id`
  (tenant_goaml_config) and a filing needs **≥1 report indicator** — so the AML deal form must require
  indicators, and the tenant must be FIU-configured. (Also pinned the test JVM heap to 2g — `40851a4` — the
  full gate was OOMing intermittently.)
- **C.1 (AML)** ✅ **DONE** (`feature/goaml-integration`, `077bef5`) — `GoamlTransaction` (deal) entity in
  `aml-orm` (extends `AuditableEntity`) + `GoamlFilingStatus` enum (DRAFT → PENDING_APPROVAL/APPROVED [D] →
  FILED/FAILED): `customerId`/`customerKind`, goods fields (itemType, itemMake, description,
  presentlyRegisteredTo, statusCode/statusComments, estimatedValue, disposedValue, currencyCode, size/sizeUom,
  registrationDate/registrationNumber, identificationNumber), report fields (internalRef, dealDate, reason,
  actionTaken, indicators[] via a `goaml_transaction_indicator` child table). **Goods + indicator values stored
  as goAML codes directly** (no AML master FK), so the push needs no resolution; MLRO + submission date owned by
  goAML. Flyway **V102** (customer-service) + `GoamlTransactionRepository` (tenant-scoped, soft-delete) + DTOs
  + hand-written mapper + `GoamlTransactionService` (CRUD, `currentUserService.requireCompanyId()`, `uid` via
  `UidGeneratorService`; a filed deal is immutable). 4 unit tests over mocked repos. Built via Temurin 21 +
  IntelliJ Maven (`-pl customer-service -am`). The only `@SpringBootTest` is `@Disabled`, so V102 SQL is
  validated at runtime (entity column types aligned), as elsewhere in the AML repo.
- **C.2 (AML)** ✅ **DONE** (`feature/goaml-integration`, `fa808ce`) — `customer-service` endpoints:
  `GoamlTransactionController` (deal CRUD at `/api/v1/goaml-deals`, always on) + **"File to goAML"**
  (`GoamlFilingController` `@ConditionalOnProperty` `POST /api/v1/goaml-deals/{id}/file` + `GoamlFilingService`):
  loads a deal, reuses the extracted `GoamlCustomerPushService.assembleSubject` for the party bundle, maps the
  deal → goods + header into `GoamlFilingPayload` (mirrors goAML `ScreeningFilingPayload` field-for-field),
  POSTs to goAML's **C.4a** `/filings` (`GoamlScreeningClient.file` → `GoamlFilingResult`), and records the
  report id + status on the deal (VALID → FILED, else FAILED). `filingRef` = the deal **uid**. 18 goaml tests
  green (filing-service assemble+file+record / INVALID→FAILED / 404; a `file()` MockRestServiceServer case;
  Slice-1 push/assertion/CRUD intact).
  > **Re-sequenced (kept each step a coherent feature-pair):** the **goAML-lookup proxy** moved to **C.3** (its
  > only consumer is the form, and it needs a goAML service-authed lookup endpoint — built together there); the
  > **report-download proxy** moved to **C.4b** (built with its goAML XML-by-ref backend).
- **C.4b** ✅ **DONE** — service-assertion-authed **report read**, both halves: **goAML** (`a0ede5f`, merged to
  `main`) `GET /api/v1/integration/screening/filings/{ref}/report.xml` → the marshalled XML by
  `entity_reference` (FIL-<companyId>-<ref>), `application/xml` + download filename, 404 when no report/XML;
  E2E proves a filed report's XML is downloadable by ref. **AML** (`eae48ae`, `feature/goaml-integration`)
  `GET /api/v1/goaml-deals/{id}/report.xml` proxy (`GoamlScreeningClient.downloadReportXml` +
  `GoamlFilingService.downloadReportXml`, requires the deal is filed) streams it inside the cockpit; 9 goaml
  tests green. (`GET /filings/{ref}` status was already added in C.4a.)
- **C.3a** ✅ **DONE** — lookup proxy, both halves: **goAML** (`04f42bc`, merged to `main`)
  `IntegrationLookupController` `GET /api/v1/integration/lookups/{jur}/{set}` (service-authed) → the
  authoritative `code+label` set (reuses `LookupService.entries` + Phase B labels); 404 unknown set, 401 no
  assertion; Testcontainers E2E. **AML** (`30c6b9a`, `feature/goaml-integration`) `GoamlLookupController`
  `GET /api/v1/goaml/lookups/{set}` proxy (`GoamlScreeningClient.lookup`); the cockpit talks only to
  customer-service. 14 goaml tests green.
- **C.3b** ✅ **DONE** (Frontend_Customer `feature/goaml-integration`, `dfc04f0`) — the **"goAML Filing"**
  screen: App-Router route `(main)/goaml-filing` + Sidebar nav item; pick a (legal) customer → enter the deal
  (item type / status from goAML lookups, value, currency, registration no., description) → report indicators
  (**multi-select** from goAML lookups, **≥1 required**) + reason/action → **File to goAML** (create deal →
  file) → shows goAML status + validation messages + a **Download report (XML)** action. react-hook-form/yup;
  reuses the custom Tailwind `FormSelect`/`FormMultiSelect`/`FormInput`/`TextArea` (NOT Ant — that's goAML's own
  SPA). Gate: `tsc --noEmit` + `next lint` clean; **full `next build` succeeds** (route `/goaml-filing` emitted)
  — note: `next build` needs **Node ≥18.18** (used nvm Node 22; the env default 18.16 is too old).

### Phase D — Maker-checker (both planes) + "see it all in goAML"
- **D.1 (AML)** — deal approval before filing, reusing the `CaseManagementDecisionLog` pattern (maker creates,
  checker approves → only then "File" is enabled).
- **D.2 (goAML)** — report review stage: `VALID → PENDING_REVIEW → APPROVED → (MLRO) SUBMITTED`, RBAC + audit,
  configurable (standalone can skip). REST approve/reject + E2E + SPA review queue. **Split:** **D.2a** backend
  ✅ DONE (merged `1d16989`) — `tenant_goaml_config.review_required` (default false, opt-in everywhere) + report
  review columns; `ReportReviewService` (submit-for-review/approve/reject/queue, MLRO approves+submits, no new
  role); submit gate requires APPROVED when review on; REST `/submit-for-review|approve|reject|review-queue`;
  `ReportReviewE2ETest` (4). **D.2b** ✅ DONE (merged `5e2b5c4`) — goAML SPA: `ReviewQueuePage` (route
  `reports/review` + "Review queue" nav, MLRO/TENANT_ADMIN) lists PENDING_REVIEW with approve/reject(remark);
  `ReportDetailPage` "Submit for review" on VALID + submit enabled for VALID|APPROVED; `ReviewQueuePage.test`
  (4); full frontend gate green on Node 18 (lint/tsc/72 vitest/build). **D.2 COMPLETE.**
- **D.3 (goAML)** — "Transaction & Report" view: a read endpoint + SPA page rendering the stored `input`
  (parties/directors/UBO/goods/deal) + status + XML — a goAML login shows the whole thing. ✅ **DONE.**
  **D.3a** (merged `6626375`) — `GET /reports/{id}/detail` → `ReportDetailView` (summary + parsed input +
  persisted validation + the D.2 review trail + hasXml); closed the gap where no endpoint returned a stored
  report's input/validation. **D.3b** (merged `0174e91`) — `ReportDetailPage` now loads `/detail` and adds
  **Filing details** (typed DPMSR sections from the input + raw-JSON fallback, both input shapes), **Validation**
  (messages table), and **Review** (status/reviewer/remark/when) panels; `ReportFilingDetails` + tests.
- **D.4 (AML)** — in the case/filing UI: show goAML status (poll/refresh), a link to open the report in goAML,
  **and a "Download report (XML)" action** backed by the C.2 download proxy — so an AML user sees *and downloads*
  the generated DPMSR without leaving the AML software. (End-state parity goal: the report is viewable +
  downloadable in **both** apps.)

### Phase E — Live B2B proof (parallel, external — NOT a code phase)
Secure a client RE + SACM registration → per-tenant FIU B2B URL + creds → submit a real DPMSR → confirm FIU
acceptance (`plans/go-live-integration-runbook.md`). **Also resolve:** did the FIU accept the third-party `USG…`
report that our XSD gate rejects (`employer_address_id` in a director)? Determines if our gate must relax.

## The seam (integration contract — summary)

| Concern | Mechanism | goAML side | AML side |
|---|---|---|---|
| Service trust | RS256 signed assertion | `ServiceCredentialValidator` + `trusted_service` (built) | **S1.1** keypair + signer (new) |
| Tenant resolution | `company_id` → `external_org_ref` | built | send `company_id` |
| Party feed | AML-native bundle, resolved codes | `ScreeningPartyMapper` + S1.3 intake | resolve `Master*`→codes |
| Deal / goods | one-shot filing call carries parties+goods+header | **C.4a** filing endpoint → `TTransItem` + header | **C.1–C.3** deal module (new) |
| Reporting person | goAML tenant default | **Phase A** | sends nothing |
| Approval | two-tier | **D.2** MLRO review | **D.1** business checker |
| Submit → FIU | B2B REST + poll | built | — |
| Status / SoR view | notifications + read view | **D.3** + XML download endpoint (built) | **D.4** display + **download proxy** (C.2) |

## Risks / constraints
- **🔴 Live B2B unproven & externally gated** (SACM/real RE) — the differentiator; Phase E de-risks in parallel.
- **Two Spring Boot majors** (goAML 3.3, AML 4.0.2) — fine; they integrate only over REST.
- **FK→code resolution** must be correct on the AML side (country/gender/role/id-type) or the XSD gate rejects.
- **Lookups are provisional** until real UAE FIU exports land.
- **Standalone-safe:** Phases A–B + goAML's SPA keep goAML usable alone; the seam is additive.

## Suggested order
~~Slice 1~~ ✅ → ~~Phase A~~ ✅ → ~~Phase B~~ ✅ → ~~**Phase C (deal module)**~~ ✅ (C.4a → C.1 → C.2 → C.4b →
C.3a → C.3b) → **D** (maker-checker both planes + "see it all in goAML") — **D.2 (goAML review gate) ✅ DONE**
(D.2a + D.2b), **D.3 (goAML read view) ✅ DONE** (D.3a + D.3b); next **D.1** (AML deal approval) → D.4 (AML
status+download) — with **E** (live B2B proof) in parallel throughout. **All goAML-side Phase-D work is done:
the review gate (VALID→PENDING_REVIEW→APPROVED→submit, opt-in per tenant) + the full "see it all" read view
(stored input + validation + review trail). Remaining Phase D is the AML-plane: D.1 deal approval + D.4
status/download display.**
