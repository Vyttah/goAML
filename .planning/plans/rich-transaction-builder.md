# Plan — Rich Transaction Builder (LiveExShield parity, then beyond)

> **Track:** AML cockpit "Create Transaction" page (and detail views) on the frontend-direct integration.
> Goal: match — then beat — LiveExShield's transaction-creation UX, while the goAML XML stays 1:1 FIU-valid
> and **no field we hold or the user enters is ever lost**.
>
> **System-of-record for the integration:** `.planning/plans/goaml-as-aml-microservice.md` (this is a
> sub-track of it). **Lives in:** AML repo `Frontend_Customer` (UI) + goAML repo (contract, phase T2).

## Why (user intent)

LiveExShield's transaction screen shows, per selected customer: collapsible **Customer / Shareholder /
Director-Representative / Bank / UBO** sections, each a selectable relation table that expands to the
relation's full KYC (incl. an ID-documents "Detail" sub-table), plus a deep **Transaction Details** block.
The user wants us to "include everything like them, and be better" — without ever breaking the goAML XML or
dropping a field we already have or the user enters.

## Decisions (locked this session)

1. **Capture everything, never break the XML.** Show all LiveExShield fields. File only XSD-valid elements
   into the goAML XML so the FIU never rejects it; preserve the rest (display from KYC, which already lives
   in AML; metadata for user-entered extras). End-state: nothing missing across the system.
2. **Frontend-first, then goAML** (T1 UI → T2 contract). 
3. **Read-only prefilled** relation detail panels (KYC-of-record; the subject party stays editable).

## The contract reality (verified — drives what we file)

goAML **DPMSR is activity-shaped** (parties + goods, *no* transaction block). Against the FIU 5.0.2 XSD,
LiveExShield's "Transaction Details" fields split three ways:

- ✅ **Map today** (report/goods level): Description→`reason`, Action Taken→`action`, Date→`submissionDate`,
  Internal Ref→`entityReference`, Branch→`rentityBranch`, Currency/Estimated/Invoice Amount→goods
  `currencyCode`/`estimatedValue`, Item Type/Size/Unit/Status/Status Comments/Description→goods, Is STR/ISTR→
  `indicators`, Executed By→reporting person (auto).
- 🟡 **XSD supports, curated `DpmsrCreateRequest` doesn't expose** → carried in **T2** via the full payload:
  rich Director/Shareholder/UBO detail (email, address, occupation, **PEP**, source of wealth, **multiple ID
  documents**, party **role codes** SHRHL/UBO/ATR/DIR), proper **Bank account party** (institution/SWIFT/
  account/IBAN/currency/signatories), 2nd nationality.
- ❌ **Not in the FIU XSD at all** (FIU would reject/ignore): Payment Mode, Late Deposit, Channel, Rate,
  Amount LC, Carrier Name/Details, Indemnified for Repatriation, shareholding %. → **captured as report
  metadata only**, never emitted into the filed XML.

**Key enabler (already shipped in goAML):** the *full-schema-fidelity* path — `POST /api/v1/reports` binds
`DpmsrReportPayload` whose nested objects **are the generated xjc leaf types** (1:1 with the XSD). So T2 does
**not** need to extend the curated DTO — it switches the cockpit's assembly to the full payload, which can
carry every XSD element. See `.planning/plans/full-schema-fidelity.md`.

## Data sources (verified)

- **Relations list:** `GET /customers/{legal|natural}/{id}/relations` → `{directors, shareholders, ubos,
  representatives, banks}` (light rows: id, name, type, relationType, status, entryDate).
- **Per-relation KYC (read-only detail):** `getDirectorById` / `getShareholderById` / `getUBOById` /
  `getRepresentativeById` / `getBankById`. Persons carry a `details[]` ID-docs array
  (`idTypeId, idNumber, expiryDate, placeOfIssueId, documentUrl`). Field names per type are recorded in the
  build notes (firstName/lastName vs fullName vs legalName; nationalityId/countryOfResidenceId/occupationId/
  sourceOfFundsId/pepStatus/shareholdingPercent/dualNationality*; bank: bankName/accountTypeId/currencyId/
  accountNumber/iban/swift/modeOfSignatoryId/bankSignatoriesId).
- **Masters (id→code/name):** `getDropDownOptions('countries,nationalities,occupations,sources-of-funds,
  id-types,director-id-types,account-types,currencies,modes-of-signatory,signatories,...')`.
- **goAML lookups:** `goamlLookup('item_types'|'item_status'|'currencies'|'report_indicators')`.
- **Collapsible UI:** reuse `components/ui/Table/NestedDataTable.tsx` (chevron expand/collapse) and/or a thin
  `CollapsibleSection` styled with `headingTitleHeight`/`headingTitle`.

## Build

### Phase T1 — Frontend (no goAML change; posts the curated `/reports/dpmsr`, mapping what maps today)

- **T1.1 `CollapsibleSection`** — `headingTitleHeight` header (chevron + title + optional right action) that
  collapses its body. Matches the onboarding design language.
- **T1.2 Relation KYC helpers** (`relationKyc.ts`) — fetch detail by relationType; id→code & id→name maps;
  field definitions per relation type for the read-only panels; goAML contribution builder (legal-entity
  related persons → `entity.directors[]` with **role codes** DIR/SHRHL/UBO/ATR; legal-shareholder → entity
  party; banks → held for T2).
- **T1.3 `RelationGroupSection`** — per group (Shareholder / Director-Representative / Bank / UBO): a
  multi-select relation table + read-only detail panel(s) for each selected relation (KYC fields + the
  ID-documents "Detail" sub-table), all inside a `CollapsibleSection`.
- **T1.4 Rewrite `CreateTransactionComponent`** — compose: breadcrumb + Legal/Natural pill + collapsible
  **Customer** (subject party, editable, KYC-prefilled, richer fields) + the four relation sections + **Goods**
  + collapsible **Transaction Details** (report-level mapped fields + extra LiveExShield fields captured but
  marked not-filed-until-T2). One relations fetch, split into groups.
- **T1.5 Gate** — `tsc --noEmit` + `next lint` clean; commit (my files only).

### Phase T2 — goAML carry-through (file *everything*)

- Switch the cockpit's create call from the curated `/reports/dpmsr` to the full **`DpmsrReportPayload`** on
  `POST /api/v1/reports`; assemble parties as full `ReportPartyType` (person/entity/**account**), directors
  with `identifications[]`/PEP/role, goods with all `TTransItem` fields, nationality2 — so every KYC field +
  user entry round-trips to the XML. XSD-invalid LiveExShield fields → report metadata only.
- Gate: goAML `./gradlew test jacocoTestCoverageVerification` if any goAML code changes; XSD round-trip lock;
  frontend gate; planning-doc sync.

### Phase T3 — Approve/Detail parity (later)

- Expand the Approve-Transaction detail panel to render the same rich, sectioned, collapsible view of a built
  report (parties with roles + ID-docs, goods, metadata), beating LiveExShield's read view.

## Constraints / guardrails

- **XML integrity is absolute** — T1 only ever sends XSD-valid curated fields; nothing experimental reaches
  the marshaller. The FIU must never reject our output.
- **No data loss** — read-only panels display KYC verbatim (already stored in AML); user-entered extras that
  the XSD can't hold are kept as metadata, never silently dropped. Full XML carry-through lands in T2.
- **MLRO submit-gating preserved** (federated user); embedding the UI can't bypass the compliance gate.
- **Commit only my files** in the AML repos via explicit pathspecs; never touch the user's modified
  poms/yml/.env. Never commit secrets.

## Verification

- **T1:** pick a customer → Customer section prefills; each relation group shows its table; selecting rows
  reveals read-only KYC + ID-docs sub-table; create posts a curated DPMSR that builds **VALID** (directors
  carry role codes); `tsc`+`lint` green.
- **T2:** a created report's XML carries the rich party/goods fields + bank account party; XSD round-trip
  passes; FIU-unsupported fields are absent from the XML but present in the stored metadata.
