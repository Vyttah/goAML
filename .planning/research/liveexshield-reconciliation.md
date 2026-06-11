# LiveExShield ⇄ Vyttah cockpit ⇄ goAML 5.0.2 — field reconciliation

> Canonical reference for the "Create Transaction" parity work. Raw scrape:
> [`liveexshield-create-transaction-spec.json`](./liveexshield-create-transaction-spec.json) (captured 2026-06-11
> via Claude-for-Chrome, read-only). Our cockpit page: AML `Frontend_Customer/components/CreateTransactionComponent`.
> Filed XML target: goAML `t_person` / `t_entity` / `t_trans_item` (lenient party types — the FIU DPMSR samples).

## Headline findings

1. **LiveExShield has NO discrete "Indicators" field.** Its report-reason mechanism is a `Is STR/ISTR?` checkbox
   + free-text `Reason` + `Description`. goAML's XSD **mandates** `<report_indicators>` (≥1 code), so our explicit
   **"Reason for reporting (FIU indicator)"** multiselect is *more* correct — keep it. (LiveExShield must derive the
   indicator behind the scenes.)
2. **Customer panel in LiveExShield is read-only/prefilled** from a record picker. Ours is editable + prefilled —
   strictly better. Their mandatory set (`*`) matches what we now mark mandatory.
3. **Most "Transaction Details" dropdowns are metadata**, not FIU-XSD fields (Payment Mode, Channel, Source Of Found,
   Transaction Product/Purpose, Indemnified, Rate, Amount LC, Carrier…). We capture these as report metadata; only
   `Currency`, `Estimated Amount`, `Item Type`, `Item Size/Unit`, `Status Code`, `Status Comments`, `Reason`,
   `Action`, `Date`, `Internal Reference`, `Branch` map into the filed goAML XML.

## Transaction Details — mapping

| LiveExShield field | Mandatory | Our field | goAML XSD target | Status |
|---|---|---|---|---|
| Description Of The Report | ✱ | reason ("Description of report") | report `reason` | ✅ have |
| Action Taken By Reporting Entity | ✱ | action | report `action` | ✅ have |
| Date | ✱ | submissionDate (auto now) | report `submission_date` | ✅ have (auto) |
| Internal Reference no. | ✱ | reference | report `entity_reference` | ✅ have |
| Transaction Product (24) | ✱ | meta.transactionProduct | — metadata | ✅ **LiveEx 24 values** |
| Payment Mode (5) | ✱ | meta.paymentMode | — metadata | ✅ **LiveEx 5 values** |
| Beneficiary Name / Comments | | meta.beneficiaryName / Comments | — metadata | ✅ have |
| Late Deposite | | meta.lateDeposit (Yes/No) | — metadata | ✅ have |
| Channel (FACE/NON-FACE) | ✱ | meta.channel | — metadata | ✅ **LiveEx 2 values** |
| Source Of Found (32) | ✱ | meta.sourceOfFunds | — metadata | ✅ **LiveEx 32 values** |
| Transaction Purpose (61) | ✱ | meta.transactionPurpose | — metadata | ✅ **LiveEx 61 values** |
| BRANCH | | branch | report `rentity_branch` | ✅ have |
| Indemnified for Repatriation (YES/NO) | ✱ | meta.indemnified | — metadata | ✅ **added** |
| Executed By | | (reporting person) | reporting_person (server MLRO) | ✅ have (auto) |
| Currency | ✱ | goods.currencyCode (goAML currencies) | `t_trans_item/currency_code` | ✅ have |
| Rate | ✱ | meta.rate | — metadata | ✅ have |
| Invoice Amount | | meta.invoiceAmount | — metadata | ✅ have |
| Amount LC | | meta.amountLc | — metadata | ✅ have |
| Estimated Amount | | goods.estimatedValue ("Value") | `t_trans_item/estimated_value` | ✅ have |
| Item Type (45) | | goods.itemType (goAML item_types) | `t_trans_item/item_type` | ✅ have (goAML enum — authoritative, not LiveEx list) |
| Item Size | | goods.size | `t_trans_item/size` | ✅ **added** |
| Item Unit | | goods.sizeUom | `t_trans_item/size_uom` | ✅ **added** |
| Status Code (22) | ✱ | goods.statusCode (goAML item_status) | `t_trans_item/status_code` | ✅ have (goAML enum) |
| Status Comments | | goods.statusComments | `t_trans_item/status_comments` | ✅ **added** |
| Carrier Name / Details | | meta.carrierName / Details | — metadata | ✅ have |
| Is STR/ISTR? | | (subsumed by the indicators multiselect) | report `indicators` | ✅ have (better) |
| Reason | ✱ | (free-text Reason — same as Description Of The Report) | report `reason` | ◑ single reason field |
| Description | ✱ | goods.description | `t_trans_item/description` | ✅ have |

> **Filed-field decisions:** `Item Type`, `Status Code`, `Currency` use the **goAML lookup enums** (item_types=63,
> item_status=20, currencies) — NOT LiveExShield's free lists — because those values must validate against the FIU
> XSD. LiveExShield's lists are recorded in the JSON for reference only.

## Customer (subject) — extras LiveExShield shows that we don't file

These are LiveExShield metadata/display fields with **no home in the lenient goAML `t_person`/`t_entity`**. We file
everything the XSD supports; **all of these are now captured (not filed) in the "Customer details (captured)" block,
prefilled from KYC** — so nothing LiveExShield shows is missing, and the filed XML stays clean:

- **Legal:** Countries of Source of Funds, Management Company, Countries of Operation, Jurisdiction,
  Licensing Authority/Other Details, License Category, Address Expiry Date, Core System ID, Is My Client.
  (We FILE: Legal Name, Business Activity, License/Incorp No., Date of Inc, Country of Inc, TRN→tax_number,
  Email, Phone, City/Address.)
- **Natural:** Profession (also mapped → occupation, filed), Residency Status, Core System ID, Is My Client.
  (We FILE: First/Last name, DOB, Nationality, Country of birth, Occupation, Source of wealth, Alias,
  Dual nationality→nationality2, ID document, Email, Phone, City/Address.)

## Relation detail panels (read-only KYC) — LiveExShield mandatory (`*`) set

- **Shareholder (NATURAL):** Full Name✱, Country of Residence✱, Nationality✱, DOB✱, Phone✱, Source of Funds✱, Occupation✱, PEP✱ (+ Alias, Place of Birth, Email, Address, Source of wealth, Expected income, Shareholding %, Dual nationality).
- **Shareholder (LEGAL):** Country of Incorporation✱, Type✱, License Type✱, License Number✱, Business Activity✱, Countries of Operation✱, Source of funds✱, Phone✱.
- **Shareholder (TRUST):** Full name✱, Registered Address✱.
- **Director/Representative:** Country of Residence✱, Nationality✱, Place of Birth✱, Email✱, Occupation✱ (+ flags IS CEO/MD, IS Representative, IS MANAGER).
- **Bank:** Bank Name✱, Account Number✱, IBAN✱, Swift✱ (we file these as a `t_account` party — done in T2).
- **UBO:** Full Name✱, Country of Residence✱, Nationality✱, Source of funds✱, Occupation✱.

> Our relation panels are read-only KYC + ID-docs sub-table (already built). Role codes (DIR/SHRHL/UBO/ATR) + IDs
> flow into `entity.directorId[]` for legal subjects. ID-document column headers were empty in LiveExShield's sample
> (not observed → not invented); ours come from the AML KYC `details[]`.

## Dropdown value sources (decision)

| Dropdown | Source we use | Why |
|---|---|---|
| Payment Mode, Channel, Source of funds, Transaction product, Transaction purpose | **LiveExShield's exact hardcoded lists** (from the saved spec) | User chose full LiveExShield parity for these metadata dropdowns |
| Indemnified for Repatriation, PEP, Late deposit, Is my client | Yes/No | Simple enums |
| Item Type, Status Code, Currency | **goAML lookups** | Must validate against the FIU XSD (filed fields) |
| Customer-detail captured fields (License type, Jurisdiction, Residency status, Profession, …) | **AML masters** resolved to display names, prefilled from KYC | Captured metadata; the customer's own configured values |
