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

### Phase B (goAML) — Expanded lookups / dropdowns
- B.1 add item types, item status codes, DPMSR indicators, party role / identification / contact / address code
  sets (from the 5.0.2 XSD enums + known UAE codes); extend `LookupService`. B.2 wire SPA dropdowns. (Live
  FIU-OData lookup sync stays a later external follow-up; values provisional.)

### Phase C — The Deal module (the cockpit build, AML-side) + the real feed
- **C.1 (AML)** — `GoamlTransaction` (deal) entity in `aml-orm`: `companyId`, `customerId`/`customerKind`,
  selected party refs, goods fields (itemType, make, description, estimatedValue, currency, statusCode/comments,
  disposedValue, size/uom, registrationDate/number, identificationNumber), report fields (internalRef, date,
  description, actionTaken, reason, indicators[]), workflow `status` (DRAFT→PENDING_APPROVAL→APPROVED→FILED),
  `goamlReportId`, `goamlStatus`. Flyway + repo + service.
- **C.2 (AML)** — `customer-service` endpoints: CRUD the deal, **"File to goAML"** (assemble full bundle incl.
  deal → S1.3 call → store goamlReportId + the returned status), a status-pull/refresh, **and a report
  download proxy** (`GET /api/v1/goaml-filings/{id}/report.xml` → calls goAML's authenticated XML-download
  endpoint with the service assertion and streams it back) so the report is downloadable *inside* the AML UI,
  not only via a link to goAML. Resolve `Master*`→codes in the mapper.
- **C.3 (AML)** — Frontend_Customer screen (new route, e.g. `/goaml-filing`): pick customer → parties
  auto-loaded → enter the deal → maker-checker → **File to goAML** → show returned status. Next.js/React.
- **C.4 (goAML)** — extend the S1.3 intake to consume the deal → goods (`TTransItem`) + report header, producing
  a fully VALID DPMSR (entity-party customers validate fully; person-party need the heavier field set per docs/15).

### Phase D — Maker-checker (both planes) + "see it all in goAML"
- **D.1 (AML)** — deal approval before filing, reusing the `CaseManagementDecisionLog` pattern (maker creates,
  checker approves → only then "File" is enabled).
- **D.2 (goAML)** — report review stage: `VALID → PENDING_REVIEW → APPROVED → (MLRO) SUBMITTED`, RBAC + audit,
  configurable (standalone can skip). REST approve/reject + E2E + SPA review queue.
- **D.3 (goAML)** — "Transaction & Report" view: a read endpoint + SPA page rendering the stored `input`
  (parties/directors/UBO/goods/deal) + status + XML — a goAML login shows the whole thing.
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
| Deal / goods | bundle carries the deal | C.4 maps → `TTransItem` + header | **C.1–C.3** deal module (new) |
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
**Slice 1 (+ Phase A in parallel) → B → Phase C (deal module) → C.4 → D**, with **E in parallel** throughout.
