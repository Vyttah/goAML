# 05 — The Engine

> The `engine/` package: turn input into a report, **validate** it, marshal it to XML, and ZIP it.
> Package: `com.vyttah.goaml.engine`. Every class is a Spring `@Component` unless noted.
>
> This is the heart of the product. It is fully built and tested (Phases 4–5) but **not yet wired to
> any HTTP endpoint** — that's Phase 7.

---

## 1. Sub-packages

| Sub-package | Job |
|-------------|-----|
| `engine/build` | Assemble a `Report` POJO from a neutral header DTO + a body. |
| `engine/validation` | Check the report against schema + business rules. |
| `engine/marshal` | Serialize the `Report` to UTF-8 XML (and back). |
| `engine/packaging` | Build the in-memory ZIP (report XML + attachments) with size limits. |
| `engine/jurisdiction` | Load per-country config (UAE = `ae.yml`). |
| `engine/lookup` | Load FIU code-lists (`lookups/ae/*.json`) and answer "is this code valid?". |

---

## 2. Builders (`engine/build/`)

Thin assemblers. **They do no validation.**

**`ReportHeader`** (record) — the neutral input shared by both builders. Every field is nullable so one
DTO serves all 7 report codes; conditional-mandatory rules are the validator's job. Fields:
`rentityId, rentityBranch, submissionCode, reportCode, entityReference, fiuRefNumber, submissionDate,
currencyCodeLocal, reportingPerson, location, reason, action, reportIndicators (List<String>)`.

**`TransactionReportBuilder`** — `Report build(ReportHeader header, List<Transaction> transactions)`.
Applies the header, sets the transaction list. For **STR, AIFT, ECDDT**.

**`ActivityReportBuilder`** — `Report build(ReportHeader header, Activity activity)`. Applies the
header, sets the activity. For **SAR, AIF, ECDD, DPMSR**.

**`ReportHeaderApplier`** — package-private static helper that copies the 12 header fields onto the
`Report` and maps each indicator `String` into a `ReportIndicator`.

> ⚠️ The builders contain **no report-code→builder dispatch**. The authoritative code→shape mapping is
> in the **validator** (`TRANSACTION_CODES` / `ACTIVITY_CODES`). The caller picks the matching builder.

---

## 3. Validation (`engine/validation/`) — the core of correctness

### The result model
- **`Severity`** — `ERROR` (blocks submission) or `WARNING` (surfaced, doesn't block).
- **`ValidationMessage`** (record) — `(Severity severity, String path, String code, String message)`.
  `path` = dotted location (e.g. `report.transaction[0].amount_local`); `code` = stable machine code
  (e.g. `MANDATORY`). Static factories `error(...)`, `warning(...)`.
- **`ValidationResult`** — accumulates messages. `isValid()` is true iff there are **no ERRORs**
  (warnings are allowed). Also `errors()`, `warnings()`, `messages()`, `hasCode(code)`.

### Entry point
`ReportValidator.validate(Report report, String jurisdictionCode) → ValidationResult`. Injects
`JurisdictionRegistry` + `LookupService`. Flow:
1. Resolve jurisdiction. Unknown → single ERROR `UNKNOWN_JURISDICTION`, return.
2. Validate header.
3. If `reportCode == null`, return (all shape rules key off the code).
4. Validate shape + indicators.
5. Dispatch by code: transaction codes → transaction rules; activity codes → activity rules.

Constants:
- `TRANSACTION_CODES = {STR, AIFT, ECDDT}`, `ACTIVITY_CODES = {SAR, AIF, ECDD, DPMSR}`
- `FIU_REF_REQUIRED = {AIF, AIFT, ECDD, ECDDT}`
- `LOCATION_REASON_ACTION_REQUIRED = {STR, SAR}`
- Max-lengths: entity_reference 255, fiu_ref 255, transaction number 50, reason 4000, action 4000.

### Header rules (all codes unless noted)

| Path | Code | Sev | Check |
|------|------|-----|-------|
| `report.rentity_id` | `MANDATORY` | ERROR | null or ≤ 0 |
| `report.submission_code` | `MANDATORY` | ERROR | null |
| `report.report_code` | `MANDATORY` | ERROR | null |
| `report.report_code` | `REPORT_CODE_NOT_ALLOWED` | ERROR | not in jurisdiction's allowed codes |
| `report.entity_reference` | `MANDATORY` | ERROR | blank (it's the idempotency key) |
| `report.entity_reference` | `MAX_LENGTH` | ERROR | > 255 |
| `report.submission_date` | `MANDATORY` | ERROR | null |
| `report.currency_code_local` | `MANDATORY` | ERROR | blank |
| `report.currency_code_local` | `CURRENCY_MISMATCH` | ERROR | ≠ jurisdiction default (AED) |
| `report.fiu_ref_number` | `FIU_REF_REQUIRED` | ERROR | blank AND code ∈ {AIF,AIFT,ECDD,ECDDT} |
| `report.fiu_ref_number` | `MAX_LENGTH` | ERROR | non-blank AND > 255 |
| `report.location` | `MANDATORY` | ERROR | null AND code ∈ {STR,SAR} |
| `report.reason` | `MANDATORY` | ERROR | blank AND code ∈ {STR,SAR} |
| `report.action` | `MANDATORY` | ERROR | blank AND code ∈ {STR,SAR} |
| `report.reason` | `MAX_LENGTH` | ERROR | non-blank AND > 4000 |
| `report.action` | `MAX_LENGTH` | ERROR | non-blank AND > 4000 |
| `report.reporting_person` | `MANDATORY` | ERROR | null (returns early) |
| `report.reporting_person.first_name` | `MANDATORY` | ERROR | blank |
| `report.reporting_person.last_name` | `MANDATORY` | ERROR | blank |

### Shape rules (transaction XOR activity)

| Path | Code | Sev | Check |
|------|------|-----|-------|
| `report.transaction` | `SHAPE_REQUIRED` | ERROR | transaction-code but no transactions |
| `report.activity` | `SHAPE_CONFLICT` | ERROR | transaction-code but an activity is present |
| `report.activity` | `SHAPE_REQUIRED` | ERROR | activity-code but no activity |
| `report.transaction` | `SHAPE_CONFLICT` | ERROR | activity-code but transactions present |

### Indicators (all codes)
`report.report_indicators` / `MANDATORY` / ERROR — null or empty list.

### Transaction rules (STR/AIFT/ECDDT), per transaction `report.transaction[i]`

| Path | Code | Sev | Check |
|------|------|-----|-------|
| `.transactionnumber` | `MANDATORY` | ERROR | blank |
| `.transactionnumber` | `MAX_LENGTH` | ERROR | > 50 |
| `.date_transaction` | `MANDATORY` | ERROR | null |
| `.amount_local` | `MANDATORY` | ERROR | null |
| `.amount_local` | `POSITIVE` | ERROR | ≤ 0 |
| `.transmode_code` | `MANDATORY` | ERROR | blank |
| `.transmode_code` | `LOOKUP` | ERROR | non-blank, `transmode` set loaded, code not in it |

**Party-shape per transaction** — bi-party XOR multi-party:
- `fromSides` = count(`t_from` present) + count(`t_from_my_client` present); same for `toSides`.

| Path | Code | Sev | Check |
|------|------|-----|-------|
| `report.transaction[i]` | `PARTY_SHAPE_CONFLICT` | ERROR | has both bi-party sides AND `t_party` |
| `report.transaction[i]` | `PARTY_REQUIRED` | ERROR | has neither |
| `report.transaction[i]` | `BIPARTY_FROM` | ERROR | bi-party but fromSides ≠ 1 |
| `report.transaction[i]` | `BIPARTY_TO` | ERROR | bi-party but toSides ≠ 1 |
| `...t_party[j]` | `PARTY_SUBJECT` | ERROR | subject count ≠ 1 (exactly one of the 6 subject variants) |
| `...t_party[j].role` | `MANDATORY` | ERROR | role blank |

### Activity rules (SAR/AIF/ECDD/DPMSR)

| Path | Code | Sev | Check |
|------|------|-----|-------|
| `report.activity.report_parties` | `MANDATORY` | ERROR | null/empty |
| `report.activity.report_party[i]` | `PARTY_SUBJECT` | ERROR | subject count ≠ 1 (person or person_my_client — person-only) |

### UAE DPMS rules (DPMSR only) — `validateDpms`
Threshold from `jurisdiction.dpmsThreshold()` (AED 55,000).

| Path | Code | Sev | Check |
|------|------|-----|-------|
| `report.activity.goods_services` | `DPMS_GOODS_REQUIRED` | ERROR | no goods lines |
| `...goods_services[i].item_type` | `MANDATORY` | ERROR | blank |
| `...goods_services[i].estimated_value` | `MANDATORY` | ERROR | null (line skipped from the sum) |
| `...goods_services[i].currency_code` | `LOOKUP` | ERROR | non-blank, `currencies` set loaded, code not in it |
| `report.activity.goods_services` | `DPMS_THRESHOLD_FX` | **WARNING** | local total < threshold AND ≥1 non-AED line (can't FX-convert → asks for manual check) |
| `report.activity.goods_services` | `DPMS_THRESHOLD` | ERROR | local total < threshold AND all lines AED/null currency |

Summation: a line is added to the local total only if its currency is null or AED. Non-AED lines aren't
summed and flip the warning-vs-error branch. The threshold check is skipped entirely if `dpmsThreshold`
is null.

> **⚠️ Gap / Not-yet-built:** there are **no Emirates-ID or passport format/length validation rules**
> anywhere in the validator or `ae.yml`, despite being commonly expected for the UAE. This is future
> work — the full **Business Rejection Rules (BRRs)** doc is an open item (see
> [09 — Build Order & Roadmap](09-build-order-and-roadmap.md)) and will extend this rule set.

---

## 4. Marshaller (`engine/marshal/`)

**`ReportMarshaller`** wraps JAXB:
- Builds `JAXBContext.newInstance(Report.class)` **once** in the constructor (expensive + thread-safe;
  reused). Failure → `IllegalStateException`.
- `byte[] marshal(Report)` — formatted (pretty-printed), UTF-8, to an in-memory stream.
- `Report unmarshal(byte[])` — parse back.
- Any `JAXBException` → wrapped in **`MarshallingException`** (a `RuntimeException`).
- Element **ordering is not set here** — it comes from the `domain` POJO annotations
  (see [04](04-domain-model.md)).

---

## 5. Packaging (`engine/packaging/`)

**`ReportZipPackager.zip(byte[] reportXml, String reportFilename, List<Attachment> attachments,
PackagingLimits limits) → byte[]`** — fully in-memory.
1. Pre-flight: empty XML / blank filename / too many attachments → `PackagingException`;
   then per-attachment checks (blank name, empty bytes, over size limit, disallowed extension).
2. Write the report XML entry first, then each attachment entry.
3. After zipping: if the produced ZIP exceeds `maxTotalBytes` → `PackagingException`.

**`Attachment`** (record): `(String filename, byte[] bytes, String contentType)`. The filename is used
verbatim as the zip entry name (validated, not transformed).

**`PackagingLimits`** (record): `(long maxTotalBytes, long maxAttachmentBytes, int maxAttachmentCount,
Set<String> allowedExtensions)`. `0` = unlimited; empty set = any extension. Constants:
- `NONE` — no limits.
- **`UAE_DEFAULT`** — **5 MB per file**, **20 MB per ZIP**, **max 50 attachments**, allowed extensions
  `{pdf, png, jpg, jpeg, tif, tiff, doc, docx, xls, xlsx, txt}`. These match the UAE submission guide.

Target: goAML Web `POST /api/Reports/PostReport` takes this multipart ZIP (see [10](10-b2b-submission-protocol.md)).

---

## 6. Jurisdiction (`engine/jurisdiction/`)

**`JurisdictionConfig`** (record): `(String code, String name, String defaultCurrency,
Set<ReportCode> allowedReportCodes, BigDecimal dpmsThreshold, String lookupSet)` + `allows(ReportCode)`.

**`JurisdictionRegistry`** — at construction, loads + caches every `classpath*:jurisdictions/*.yml` via
SnakeYAML into a `ConcurrentHashMap` keyed by lowercased code. `find(code)` → `Optional`;
`require(code)` → throws if unknown.

**`resources/jurisdictions/ae.yml`** — the complete UAE config:
```yaml
code: ae
name: United Arab Emirates
defaultCurrency: AED
dpmsThreshold: 55000        # DPMS cash reporting threshold, AED
lookupSet: ae              # directory under classpath:lookups/
allowedReportCodes: [STR, SAR, AIF, AIFT, ECDD, ECDDT, DPMSR]
```
> **What's deliberately NOT here:** `rentity_id`, goAML base URLs, and credentials are **per-tenant**
> (in `tenant_goaml_config` + Secrets Manager), not jurisdiction-wide. Packaging size limits live in
> code (`PackagingLimits.UAE_DEFAULT`), not this YAML. The only threshold present is `dpmsThreshold`.

---

## 7. Lookups (`engine/lookup/`)

**`LookupService`** — at construction, loads every `classpath*:lookups/*/*.json` (Jackson) into a
nested map `jurisdiction → (setName → Set<String> codes)`. Each file is a JSON **array** of either bare
strings or `{"code": "..."}` objects (only the code is kept). The jurisdiction comes from the parent
dir; the set name from the filename.

API:
- `hasSet(jurisdiction, setName)` — was this set loaded?
- `isValid(jurisdiction, setName, code)` — loaded **and** contains the code?
- `codes(jurisdiction, setName)` — the set, or null.

**The critical validation semantics:**
- **Absent set = "cannot check" → rule is skipped** (no error).
- **Code missing from a loaded set = invalid → ERROR.**

This is why lookup rules always guard with `hasSet(...)` before `isValid(...)`.

**The seed lookup files** (`resources/lookups/ae/`) — placeholders, later refreshed at runtime from the
goAML `OdataLookups` endpoint:

| File | Entries | Consumed by validator? |
|------|---------|------------------------|
| `countries.json` | AE, SA, GB, US, IN, PK, CN, DE, FR, CH | No (loaded, not referenced) |
| `currencies.json` | AED, USD, EUR, GBP, SAR, INR | **Yes** — DPMS `goods_services.currency_code` |
| `funds.json` | CASH, BANKD, WIRE, CHEQUE, CARD, CRYPTO | No (loaded, not referenced) |
| `transmode.json` | CASH, WIRE, CHEQUE, BANKD, CARD, CRYPTO | **Yes** — `transaction.transmode_code` |

> Only `transmode` and `currencies` are actually checked today. `countries` and `funds` are loaded and
> queryable but not yet referenced by any rule.

---

## 8. Cross-cutting takeaways

1. **Builders are validation-free.** Validation is a separate, explicit step.
2. **Report-code→shape mapping lives in the validator** (`TRANSACTION_CODES`/`ACTIVITY_CODES`), not the
   builders.
3. **JAXB ordering is in the domain POJOs**, not the marshaller.
4. **Lookup philosophy:** absent set → skip; present-but-missing-code → error.
5. **No Emirates-ID/passport rules yet**; **file-size limits are in code**, not `ae.yml`.

---

**Next:** [06 — Multi-Tenancy & Security](06-multitenancy-and-security.md).
