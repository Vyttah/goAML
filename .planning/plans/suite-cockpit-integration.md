# Plan — Suite cockpit integration (AML software ⇄ goAML) + standalone parity

> **Status:** PLANNED (2026-06-09). Drives the next build track after Phase 1.5.
> **Why this exists:** a competitive teardown of **LexAML** (liveexshield.com — a deployed UAE AML suite
> with paying clients) showed the suite's shape and the one structural advantage we hold. This plan turns
> that into concrete work across **both** Vyttah products.

## The competitive finding (the "why")

LexAML is one monolith: Customer Onboarding → Sanction Screening → RBA → Transaction (pick customer + enter
deal) → **Transaction Approval (maker-checker)** → GoAML Xml (generate) → **manual upload to the goAML portal**.
Several UAE clients use it.

Mapped against Vyttah:

| LexAML capability | Vyttah home | State |
|---|---|---|
| Customer master / onboarding (entity, shareholders, directors, UBO, bank) | **AML software** | live |
| Sanction / PEP screening | **AML software** | live |
| RBA risk scoring | **AML software** | live |
| Case management (Tr-Case / ISTR) | **AML software** | live |
| Maker-checker approval | **both planes** | to build (AML has it; goAML to add) |
| goAML report build / validate / **XSD fidelity** | **goAML** | live (DPMSR) |
| Reporting-person ("GoAML Person") as a tenant default | **goAML** | to build |
| Lookups / dropdowns | **goAML** | partial (4 sets) → expand |
| **FIU submission** | **goAML** | **B2B REST auto-submit + poll** (LexAML = manual upload) |

**Verdict:** the Vyttah suite (AML software + goAML) matches LexAML on breadth and **beats it on automation**
(B2B auto-submission, multi-tenant SaaS, REST/MCP/CLI, schema-fidelity on the current 5.0.2 — LexAML's own
generated sample was the older "goAML XML Schema v2"). The whole thesis rests on **proving live B2B
submission** — gated on a real regulated-entity relationship; pursued in parallel (Phase E). An incumbent
choosing manual upload is the loudest signal that B2B needs validating before we bank the strategy on it.

## Locked decisions (this session, 2026-06-09)

1. **Cockpit = the AML software.** It owns all masters + transactions (like LexAML). **goAML is the report
   generator + system-of-record**: it receives and stores the *whole* thing (the report input/payload =
   parties + goods + transaction details, and a master snapshot) plus the generated XML and FIU status, so
   **logging into goAML shows the full transaction *and* its report** — but goAML keeps **no separate
   editable masters** (no parallel CRM).
2. **Maker-checker on both planes.** The AML software gates with its own (business) approval before filing;
   goAML adds its own report review/approval stage (the MLRO/compliance sign-off before FIU submit). Two-tier,
   not redundant: business checker upstream, compliance checker at the filing authority.
3. **Priority = demo-ready suite first.** Build the seam + UX gaps so the integrated suite demos end-to-end
   without waiting on FIU access; chase a client RE → live B2B proof in **parallel** (Phase E).
4. **MLRO is fed by goAML, not the AML software.** goAML stores the reporting-person as a tenant default and
   auto-injects it into every report → the AML software does **not** capture or send goAML-person data.

## What already exists (don't rebuild)

- **`report.input` (JSONB, NOT NULL)** already stores the full structured report input the engine built from
  (parties + goods + header); **`report.report_xml`** stores the marshalled XML; **`submission`** tracks FIU
  status. So "store the whole transaction + report" is mostly a **read/view** concern, not new storage.
  — `model/entity/report/Report.java`, `db/migration/tenant/V2__reports.sql`.
- **Phase 1.5 is DONE** (not deferred): federated SSO (`POST /api/v1/auth/federated/token` + `ServiceCredentialValidator`
  + `external_identity`/`trusted_service`), the **screening→goAML party feed** (`POST /api/v1/integration/screening/subjects`
  → `screened_subject` + `ScreeningPartyMapper`, customer→entity/person, directors→entity directors,
  shareholders/UBOs→parties, PEP/sanctions→comments; + a user-facing seed-DPMSR-from-subject flow), and the
  **accounting** raw-invoice push (reportability check → auto-draft). — `controller/integration/`,
  `service/integration/`, `model/entity/federated/`, `docs/14-suite-integration.md`.
- **Report lifecycle + MLRO submit-gate**, B2B client + ZIP packager + on-demand/scheduled status poll,
  XML view/download, attachments, notifications, CSV/XML import. — Phases 6–14.
- **DPMSR full-schema fidelity:** the REST API binds `DpmsrReportPayload` (xjc-generated leaf types) so a
  caller can supply — and the XML carries — **every** goAML element. — `.planning/plans/full-schema-fidelity.md`.

---

## Build track — goAML (this repo)

Standing workflow (unchanged): each step = **atomic commit** + full backend gate
(`./gradlew test jacocoTestCoverageVerification`) + frontend gate where applicable
(`npm run typecheck && lint && test && build`) + planning-doc sync + `--no-ff` merge to `main`.

### Phase A — Reporting-person (MLRO) as a tenant default, auto-fed  ⟵ start here (cheapest, highest value)

- **A.1** — New per-tenant `goaml_person` table (next tenant migration): the goAML reporting person fields
  (`first_name`, `last_name`, `gender`, `ssn`, `id_number`, `nationality`, `email`, `occupation`, plus
  `is_active`, timestamps). Multiple allowed, one active (mirrors LexAML's "GoAML Person" list). JPA entity +
  repo. (Per-tenant schema keeps the MLRO PII inside tenant isolation.)
- **A.2** — Auto-inject on report build: when an inbound payload's `reportingPerson` is null, fill it from the
  tenant's **active** `goaml_person`. Touches `DefaultReportService` / the DPMSR builder path; `reportingPerson`
  becomes optional on the wire. Unit + E2E (payload without reporting-person → valid report carrying the
  tenant default; no active person → clear 4xx).
- **A.3** — Admin REST (`/api/v1/admin/goaml-person` CRUD + set-active) + RBAC (TENANT_ADMIN/MLRO) + a SPA
  admin screen (like LexAML's setting). Vitest.

### Phase B — Expanded lookups / dropdowns

- **B.1** — Add the goAML code sets the DPMSR form needs beyond today's 4 (countries, currencies, funds,
  transmode): **item types**, **item status codes**, **DPMSR report indicators**, **party role codes**,
  **identification types**, **contact/communication types**, **address types**. Seed from the 5.0.2 XSD
  enumerations where they exist + known UAE code lists; new JSON under `resources/lookups/ae/`; extend
  `LookupService` set names + validation. (The live FIU-OData lookups-refresh sync stays a later/external
  follow-up — see STATE "Smaller follow-ups".)
- **B.2** — Wire the new dropdowns into the SPA builder + the transaction/report view (Zod mirror + tests).

### Phase C — Maker-checker approval (goAML plane)

- **C.1** — Report review stage: states `VALID → PENDING_REVIEW → APPROVED` before `SUBMITTED`
  (REJECTED-back-to-DRAFT path). Add `reviewed_by`/`approved_by`/`approved_at` + audit; submit guard requires
  `APPROVED`. Role split: ANALYST = maker (create/edit), a checker role approves, **MLRO submits** (keep the
  existing MLRO gate as the final compliance step). Config flag so a standalone deployment can run the simple
  flow; suite runs full maker-checker. Migration + entity + service + unit tests.
- **C.2** — REST `approve`/`reject` endpoints + RBAC + E2E (maker can't approve own; submit blocked until
  APPROVED; reject returns to DRAFT with comments).
- **C.3** — SPA: a review queue + approve/reject UI; reflect the new states on the detail page.

### Phase D — AML → goAML report build seam + the "see it all in goAML" view

- **D.1** — Integration intake that mirrors the existing accounting/screening pattern: a service-assertion-authed
  endpoint that accepts the **AML-native** transaction + masters (the `customer-service` shape already used for
  screening) **plus** the deal/goods/transaction details, maps to a DPMSR (reuse/extend `ScreeningPartyMapper`
  + a new transaction/goods mapper), creates the report (stores full `input` + the native snapshot for display),
  **idempotent on the AML transaction ref**. Keep the **direct** path too: a client may instead POST a full
  `DpmsrReportPayload` to `/api/v1/reports` (already works). MockMvc E2E.
- **D.2** — "Whole transaction + report" view in goAML: a read endpoint returning the stored `input`
  (parties / directors / UBO / goods / transaction details) + status + XML, and a SPA **Transaction & Report**
  detail page that renders it richly (not just status + raw XML). Satisfies decision #1 — a goAML login shows
  the full picture. (Builds on the existing detail page + `GET /reports/{id}/xml`.)
- **D.3** — Status feedback to the cockpit: confirm/extend the status-pull endpoint + notifications the AML
  software polls/stores; document the back-link contract.

### Phase E — Live B2B proof (parallel, external — NOT a code phase)

Secure a real client RE relationship + SACM registration → per-tenant FIU B2B URL + credentials → submit a
real DPMSR → confirm FIU acceptance. Follow `plans/go-live-integration-runbook.md`. This validates the single
differentiator; run it alongside A–D. **Also resolve the open question:** did the FIU actually accept the
third-party `USG…` report that nested `employer_address_id` in a director (which our XSD gate rejects)? If yes,
our gate is stricter than the FIU's live validation and we reconcile; if no, our gate is correct.

---

## Build track — AML software (separate repo — requirements, to be planned there)

goAML cannot be edited from this repo; these are the integration requirements the AML software's own plan must
cover. The goAML side of each is already built or is in Phases A–D above.

1. **Federated SSO** — sign a short-lived RS256 service assertion with the AML software's registered key and
   exchange it at `POST /api/v1/auth/federated/token` for a goAML JWT; attach it to all goAML calls.
   *(goAML side: built.)*
2. **"File to goAML" action** — from a selected customer + its transaction, call the goAML intake (Phase D.1)
   with native masters + deal/goods details (or assemble + POST a full `DpmsrReportPayload`). Map AML master
   fields → the contract. *(goAML side: D.1.)*
3. **Drop goAML-person capture** — do not collect/send the reporting person; goAML feeds it from its tenant
   default. *(goAML side: Phase A.)*
4. **Maker-checker upstream** — gate with the AML software's existing approval/case workflow before filing.
5. **Status back-link** — store the returned goAML report id; poll/display FIU status in the AML case UI.
   *(goAML side: D.3.)*

---

## The seam (integration contract)

| Concern | Mechanism | goAML side |
|---|---|---|
| Identity / SSO | RS256 service assertion → goAML JWT | `POST /api/v1/auth/federated/token` (built) |
| Party/master feed | AML-native push, mapped to goAML parties | `ScreeningPartyMapper` (built) + D.1 |
| Report creation | native intake **or** full `DpmsrReportPayload` | D.1 + `POST /api/v1/reports` (built) |
| Reporting person (MLRO) | goAML tenant default, auto-injected | Phase A — **AML sends nothing** |
| Approval | AML business checker → goAML MLRO checker | AML workflow + Phase C |
| Submission to FIU | goAML B2B REST + poll | built (Phases 6/7/9) |
| Status / receipt | notifications + status pull | D.3 (built core) |
| System-of-record / audit | full `input` + XML + submission stored | built (Report) + D.2 view |

## Risks / constraints

- **🔴 Live B2B is unproven and externally gated** (SACM registration needs a regulated-entity relationship).
  It is the differentiator; Phase E de-risks it in parallel. Do not let A–D imply it's proven.
- **Both-plane maker-checker** must not become double busywork — frame as business sign-off (AML) vs compliance
  sign-off (goAML/MLRO); make goAML's stage configurable so standalone stays simple.
- **Lookups are seeds** until real UAE FIU exports land (existing known gap) — Phase B uses XSD enums + known
  codes; flag values as provisional.
- **Standalone-safe:** Phases A–C keep goAML usable standalone (its own SPA builder, optional maker-checker);
  the seam (D) is additive.

## Suggested order

**A → B → D.2 → C → D.1/D.3**, with **E in parallel**. (A and B are cheap, visible, fully in-repo; D.2 makes
the "see it all in goAML" story real; C and the D.1 intake are the larger builds.)
