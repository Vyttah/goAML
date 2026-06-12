# 05 — The Engine

> The `engine/` package: turn input into a report, **validate** it (business rules **and** the
> authoritative XSD), marshal it to XML, and ZIP it. Package: `com.vyttah.goaml.engine`. Every class is a
> Spring `@Component` unless noted.
>
> This is the heart of the product. It is fully built and tested (Phases 4–5 + the XSD-first migration) but
> **not yet wired to any HTTP endpoint** — that's Phase 7.

---

## 1. Sub-packages

| Sub-package | Job |
|-------------|-----|
| `engine/build` | Assemble a `Report` from a neutral header + a body; the DPMSR convenience builder. |
| `engine/validation` | Check the report against **business rules** and the **authoritative XSD**. |
| `engine/marshal` | Serialize the `Report` to UTF-8 XML (and back). |
| `engine/packaging` | Build the in-memory ZIP (report XML + attachments) with size limits. |
| `engine/jurisdiction` | Load per-country config (UAE = `ae.yml`). |
| `engine/lookup` | Load FIU code-lists (`lookups/ae/*.json`) and answer "is this code valid?". |

All builders/validators operate on the **xjc-generated** model (`com.vyttah.goaml.domain.generated.*`,
see [04](04-domain-model.md)).

---

## 2. Builders (`engine/build/`)

Thin assemblers. **They do no validation.**

**`ReportHeader`** (record) — the neutral input shared by the generic builders. Fields:
`rentityId (Integer), rentityBranch, submissionCode (String, e.g. "E"), reportCode (ReportType),
entityReference, fiuRefNumber, submissionDate (OffsetDateTime), currencyCodeLocal (CurrencyType),
reportingPerson (TPersonRegistrationInReport), location (TAddress), reason, action,
reportIndicators (List<String>)`. Every field is nullable so one record serves all report codes;
conditional-mandatory rules are the validator's job.

**`ReportHeaderApplier`** — package-private static helper that copies the header fields onto the `Report`
and maps each indicator `String` into the report's `<report_indicators>` wrapper.

**`TransactionReportBuilder`** — `Report build(ReportHeader, List<Report.Transaction>)`. For **STR, AIFT,
ECDDT**.

**`ActivityReportBuilder`** — `Report build(ReportHeader, ActivityType)`. Sets the body via
**`Report.setReportActivity(...)`** (the populated choice slot — see [04 §3b](04-domain-model.md)). For
**SAR, AIF, ECDD, DPMSR**.

> ⚠️ The generic builders contain **no report-code→builder dispatch**. The authoritative code→shape
> mapping lives in the **validator** (`TRANSACTION_CODES` / `ACTIVITY_CODES`). The caller picks the
> matching builder — or, for DPMSR, uses the convenience builder below.

### 2a. The DPMSR convenience builder (schema-driven, invoice-generic)

DPMSR is Vyttah's first report type, so it gets a dedicated, full-coverage builder. It is
**not gold-specific** — it works for any DPMS invoice (any `item_type`, multiple goods lines, any of the
six party subjects).

- **`DpmsrReportInput`** (record) — the single contract. It stays thin by carrying the **generated leaf
  types** directly (`TPersonRegistrationInReport`, `List<ReportPartyType>`, `List<TTransItem>`, `TAddress`),
  so **every** DPMSR/activity field is reachable without a parallel DTO model that would drift from the XSD.
  Two entry forms:
  - the **record constructor** (good for programmatic callers — the suite-integration feeds / MCP / plugin;
    note the once-planned RabbitMQ accounting consumer was dropped in favour of **REST**, see PROJECT.md), and
  - a fluent **`DpmsrReportInput.builder()`** (good for hand-coding and tests).
- **`DpmsrReportBuilder`** (`@Component`) — `Report build(DpmsrReportInput)` and
  `ValidatedReport buildAndValidate(DpmsrReportInput, String jurisdictionCode)`. It applies the
  **DPMSR-fixed header values itself** — submission code `E`, `ReportType.DPMSR`, `CurrencyType.AED` — wraps
  parties + goods, and delegates header application + the choice slot to `ActivityReportBuilder`.
- **`GoamlParties`** — six static factories (`entity`, `entityMyClient`, `person`, `personMyClient`,
  `account`, `accountMyClient`) → a `ReportPartyType` with `reason`/`comments` set; the returned object is
  further customizable (`setRole`, `setSignificance`, …). Full coverage, no field hidden.
- **`GoamlWrappers`** — hides the JAXB wrapper classes: `reportParties(List)`, `goodsServices(List)`,
  `reportIndicators(List)`, plus a **generic** `wrap(wrapper, listAccessor, items…)` that handles the
  wrapper-per-owner types (e.g. `wrap(new TEntity.Phones(), TEntity.Phones::getPhone, phone)`).
- **`ValidatedReport`** (record) — `(Report report, ValidationResult rules, ValidationResult xsd)` with
  `isValid()` = **both** gates pass. This is what draft-creation / the plugin read for a clear pass/fail.

`SampleReports` (test fixture) and `DpmsrReportBuilderTest` are the best worked examples.

---

## 3. Validation (`engine/validation/`) — two gates

Correctness is enforced by **two independent gates**, both returning the same result model so callers
handle them uniformly:

1. **`ReportValidator`** — conditional **business rules** keyed by `report_code` + jurisdiction.
2. **`XsdSchemaValidator`** — structural conformance against the **authoritative goAML XSD**.

### The shared result model
- **`Severity`** — `ERROR` (blocks submission) or `WARNING` (surfaced, doesn't block).
- **`ValidationMessage`** (record) — `(Severity, path, code, message)`. `code` is a stable machine code
  (e.g. `MANDATORY`). Factories `error(...)`, `warning(...)`.
- **`ValidationResult`** — accumulates messages. `isValid()` is true iff there are **no ERRORs**. Also
  `errors()`, `warnings()`, `messages()`, `hasCode(code)`.

### 3a. `XsdSchemaValidator` — the authoritative gate

`ValidationResult validate(byte[] reportXml)`. Validates marshalled report XML against
`xsd/goaml/5.0.2/goAMLSchema.xsd` (constant `SCHEMA_RESOURCE`).

- The schema is **self-contained** and carries **no XSD 1.1 `<xs:assert>`** rules, so the **standard JDK
  JAXP validator (XSD 1.0)** validates it fully — no Saxon / Xerces-EE needed.
- The compiled `Schema` is built **once at construction** (expensive, thread-safe) and reused; a
  `Validator` is created per call (a `Validator` is not thread-safe).
- Findings are **collected, not thrown**, into a `ValidationResult` (SAX line/col → message, machine code
  `XSD`, path `report`), so an XSD failure has the same shape as a business-rule failure.
- **XXE-hardened:** external DTD/schema resolution is disabled.

### 3b. `ReportValidator` — business rules

`ValidationResult validate(Report report, String jurisdictionCode)`. Injects `JurisdictionRegistry` +
`LookupService`. Flow: resolve jurisdiction (unknown → `UNKNOWN_JURISDICTION`, return) → validate header →
if `reportCode == null` return → validate shape + indicators → dispatch by code.

Constants (now keyed by the generated **`ReportType`** enum):
- `TRANSACTION_CODES = {STR, AIFT, ECDDT}`, `ACTIVITY_CODES = {SAR, AIF, ECDD, DPMSR}`
- `FIU_REF_REQUIRED = {AIF, AIFT, ECDD, ECDDT}`
- `LOCATION_REASON_ACTION_REQUIRED = {STR, SAR}`
- Max-lengths: entity_reference 255, fiu_ref 255, transaction number 50, reason 4000, action 4000.

**Header rules** (selected): `rentity_id` ≤ 0 → `MANDATORY`; `submission_code` blank → `MANDATORY`;
`report_code` null → `MANDATORY`, not allowed by jurisdiction → `REPORT_CODE_NOT_ALLOWED`;
`entity_reference` blank → `MANDATORY` (it's the idempotency key) / >255 → `MAX_LENGTH`; `submission_date`
null → `MANDATORY`; **`currency_code_local`** null → `MANDATORY`, else compared via
**`CurrencyType.value()`** ≠ jurisdiction default (AED) → `CURRENCY_MISMATCH`; `fiu_ref_number` required
for `{AIF,AIFT,ECDD,ECDDT}`; `location`/`reason`/`action` required for `{STR,SAR}`; `reporting_person` +
first/last name mandatory.

**Shape rules** (transaction XOR activity) — uses **`getReportActivity()`** (not `getActivity()`) to
detect the activity slot: `SHAPE_REQUIRED` / `SHAPE_CONFLICT` for each direction.

**Indicators:** `report.report_indicators` / `MANDATORY` if null/empty.

**Transaction rules** (per `report.transaction[i]`): `transactionnumber` mandatory / `MAX_LENGTH`;
`date_transaction` mandatory; `amount_local` mandatory / `POSITIVE`; `transmode_code` mandatory + `LOOKUP`
(against the `transmode` set if loaded). **Party-shape:** bi-party (`t_from*`/`t_to*`) XOR multi-party
(`getInvolvedParties().getParty()`), with `PARTY_SHAPE_CONFLICT` / `PARTY_REQUIRED` / `BIPARTY_FROM` /
`BIPARTY_TO`, and per-`t_party` `PARTY_SUBJECT` (exactly one of the **six** subject kinds) + `role`
mandatory.

**Activity rules** (`report.activity`): `report_parties` mandatory; each `report_party` must have
**exactly one subject** — **all six subject kinds** are accepted (person/account/entity × plain/my-client).
> This was widened during the DPMSR builder work: real DPMSRs use an **entity** party (with a director),
> not a person — the earlier person-only check wrongly rejected them.

**UAE DPMS rules** (DPMSR only, `validateDpms`): ≥1 `goods_services` line (`DPMS_GOODS_REQUIRED`); per line
`item_type` mandatory, `estimated_value` mandatory, `currency_code` `LOOKUP` (via `CurrencyType.value()`
against the `currencies` set); the AED total must meet `dpmsThreshold` (AED 55,000) → `DPMS_THRESHOLD`
(ERROR) when all lines are AED, or `DPMS_THRESHOLD_FX` (**WARNING**) when a non-AED line can't be summed.

> **⚠️ Gap / Not-yet-built:** there are still **no Emirates-ID/passport format-length rules**. Those come
> from the UAE **Business Rejection Rules (BRRs)** doc (open item — see [09](09-build-order-and-roadmap.md))
> and will extend this rule set.

---

## 4. Marshaller (`engine/marshal/`)

**`ReportMarshaller`** wraps JAXB:
- Builds **`JAXBContext.newInstance(ObjectFactory.class)`** once in the constructor (so the whole generated
  package is in context; expensive + thread-safe; reused). Failure → `IllegalStateException`.
- `byte[] marshal(Report)` — formatted, UTF-8, in-memory.
- `Report unmarshal(byte[])` — parse back.
- Any `JAXBException` → wrapped in **`MarshallingException`** (a `RuntimeException`).
- Element **ordering comes from the generated model's annotations** (from the XSD), not from here.

---

## 5. Packaging (`engine/packaging/`)

**`ReportZipPackager.zip(byte[] reportXml, String reportFilename, List<Attachment>, PackagingLimits)
→ byte[]`** — fully in-memory.
1. Pre-flight: empty XML / blank filename / too many attachments → `PackagingException`; then per-attachment
   checks (blank name, empty bytes, over size limit, disallowed extension).
2. Write the report XML entry first, then each attachment.
3. After zipping: if the ZIP exceeds `maxTotalBytes` → `PackagingException`.

**`Attachment`** (record): `(filename, bytes, contentType)`. **`PackagingLimits`** (record):
`(maxTotalBytes, maxAttachmentBytes, maxAttachmentCount, allowedExtensions)`; `0` = unlimited, empty set =
any extension. Constants: `NONE`, and **`UAE_DEFAULT`** — **5 MB/file, 20 MB/ZIP, ≤50 attachments**,
extensions `{pdf,png,jpg,jpeg,tif,tiff,doc,docx,xls,xlsx,txt}` (per the UAE submission guide). Target:
goAML Web `POST /api/Reports/PostReport` (see [10](10-b2b-submission-protocol.md)).

---

## 6. Jurisdiction (`engine/jurisdiction/`)

**`JurisdictionConfig`** (record): `(code, name, defaultCurrency, Set<ReportType> allowedReportTypes,
BigDecimal dpmsThreshold, lookupSet)` + `allows(ReportType)`. Note the field is now typed to the generated
**`ReportType`** enum.

**`JurisdictionRegistry`** — at construction loads + caches every `classpath*:jurisdictions/*.yml`
(SnakeYAML) into a map keyed by lowercased code. `find(code)` → `Optional`; `require(code)` throws if
unknown.

**`resources/jurisdictions/ae.yml`** — the UAE config:
```yaml
code: ae
name: United Arab Emirates
defaultCurrency: AED
dpmsThreshold: 55000          # DPMS cash reporting threshold, AED
lookupSet: ae
allowedReportCodes: [STR, SAR, AIF, AIFT, ECDD, ECDDT, DPMSR]
```
> The YAML key is `allowedReportCodes` (string list); the registry parses those strings into the
> `ReportType` enum set behind `allowedReportTypes`. `rentity_id`, goAML base URLs, and credentials are
> **per-tenant** (in `tenant_goaml_config` + Secrets Manager), not here. Size limits live in code
> (`PackagingLimits.UAE_DEFAULT`).

---

## 7. Lookups (`engine/lookup/`) + the lookup ⊆ XSD invariant

**`LookupService`** — at construction loads every `classpath*:lookups/*/*.json` (Jackson) into
`jurisdiction → (setName → Set<String> codes)`. Each file is a JSON **array** of `{"code": "...", ...}`
objects (only the code is kept). API: `hasSet(j, set)`, `isValid(j, set, code)`, `codes(j, set)`.

**Validation semantics:** **absent set = "cannot check" → rule skipped (no error)**; **code missing from a
loaded set = invalid → ERROR**. So lookup rules always guard with `hasSet(...)` before `isValid(...)`.

**The seed lookup files** (`resources/lookups/ae/`) — placeholders, later refreshed at runtime from the
goAML `OdataLookups` endpoint. They were reconciled with the XSD enums during the migration:

| File | Entries | Checked by validator? |
|------|---------|------------------------|
| `currencies.json` | AED, USD, EUR, GBP, SAR, INR | **Yes** — DPMS `goods_services.currency_code` (⊆ XSD `currency_type`) |
| `transmode.json` | ELCFT, SWIFT, CDM, ATM, ONBNK, OCT | **Yes** — `transaction.transmode_code` (⊆ XSD `conduction_type`) |
| `funds.json` | CASH, BANKD, CHQ, ELCFT, CRPTC, POS, JEWL | No (loaded, not referenced) |
| `countries.json` | AE, SA, GB, US, IN, PK, CN, DE, FR, CH | No (loaded, not referenced) |

> **The lookup ⊆ XSD-enum invariant.** goAML has two parallel sources of allowed values: the XSD
> enumerations (structural) and these lookup JSONs (business-rule). Any lookup the validator checks **must
> be a subset of its XSD enumeration** — otherwise a validator-clean report can still fail the schema gate
> (exactly the `transmode` drift fixed during the migration: the old placeholders `CASH/WIRE/CHEQUE…` were
> **disjoint** from the XSD `conduction_type` enum). **`LookupXsdConsistencyTest`** parses the XSD and
> asserts `transmode ⊆ conduction_type` and `currencies ⊆ currency_type`, failing fast on future drift.

---

## 8. Cross-cutting takeaways

1. **Three gates, separate concerns:** the generated model (structure), `XsdSchemaValidator` (XSD
   conformance), `ReportValidator` (conditional business rules).
2. **Builders are validation-free.** `DpmsrReportBuilder.buildAndValidate` is the convenience that runs
   both gates and returns a `ValidatedReport`.
3. **Report-code→shape mapping lives in the validator**, not the builders.
4. **Use `getReportActivity()`** for the activity slot — `getActivity()` is vestigial.
5. **Lookup philosophy:** absent set → skip; present-but-missing-code → error; and every checked lookup
   ⊆ its XSD enum (guarded by a test).
6. **No Emirates-ID/passport rules yet** (awaits BRRs); **file-size limits are in code**, not `ae.yml`.

---

## The DPMSR contract — full-schema fidelity

Two contracts feed the engine's `DpmsrReportInput`, both losslessly:

- **`DpmsrReportPayload`** (the REST report API contract, `POST /api/v1/reports`) — binds the **xjc-generated
  leaf types directly** (`TPersonRegistrationInReport`, `ReportPartyType` (all 6 subjects), `TTransItem`,
  `TAddress`). Because the contract *is* the generated schema, it carries **every** goAML element 1:1 and can
  never silently drift from the XSD. The generated `@XmlEnum` types bind by their schema `value()` via
  `GeneratedEnumJacksonModule` (scoped to `domain.generated`). The JSON mirrors the XML wrapper shape
  (`phones: { phone: [...] }`, inline `identification: [...]`, `personMyClient`).
- **`DpmsrCreateRequest`** (a curated, flat, ergonomic builder used by the MCP tools, the accounting/screening
  integrations, and the CSV importer) — maps to the same `DpmsrReportInput` via `DpmsrRequestMapper`. It
  exposes the common subset plus the high-value goods fields (`disposed_value`, `status_comments`,
  `registration_number`, `identification_number`); the accounting integration sets `registration_number` from
  the invoice (source-document) number.

**Server-applied (never caller-set, but present in output):** `rentity_id` (from `tenant_goaml_config`) and
the DPMSR-fixed `submission_code=E` / `report_code=DPMSR` / `currency_code_local=AED`.

**Notes from real third-party reports:** they use the inline `<identification>` form (not the
`<identifications>` wrapper); and some nest `<employer_address_id>`/`<employer_phone_id>` inside
`<director_id>`, which is **invalid** in `t_entity_person` per the official XSD — the XSD gate correctly
rejects it. See `.planning/plans/full-schema-fidelity.md`.

---

**Next:** [06 — Multi-Tenancy & Security](06-multitenancy-and-security.md).
