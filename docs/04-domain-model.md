# 04 — Domain Model

> The `domain/` package: Java POJOs that, via JAXB, serialize to/from the goAML v4.0 `<report>` XML.
> Package: `com.vyttah.goaml.domain`.

> ⚠️ **Superseding decision (2026-06-03): XSD-first.** These hand-modeled v4.0 POJOs are being replaced
> by **xjc-generated JAXB types built from the authoritative latest goAML XSD (target 5.0.x, XSD 1.1)**.
> This doc still accurately describes the *current* code, but the data model is migrating — see
> [`.planning/plans/xsd-first-foundation.md`](../.planning/plans/xsd-first-foundation.md). Once codegen
> lands, the element-ordering rules and naming quirks below become the schema's responsibility, not ours.

---

## 1. What this layer is (and isn't)

These classes are a **hand-modeled, representative subset** of the goAML schema v4.0 `<report>` tree.
JAXB (Jakarta `jakarta.xml.bind`) turns a `Report` object graph into XML and back.

What they are: plain data holders with JAXB annotations controlling element names and ordering.

What they are **not**: they carry **no business validation**. The `*_my_client` "stricter" variants
are structurally identical to their base types — the extra mandatory-field rules live in the **engine's
validator** (Phase 5), not here. POJOs just shape the XML; the engine enforces correctness.

---

## 2. The golden rule: element ordering is annotation-driven

The goAML XSD uses `<sequence>`, so **element order in the XML is significant** — emit fields out of
order and the FIU rejects the report. Two facts make this work:

1. Every class is `@XmlAccessorType(XmlAccessType.FIELD)` — JAXB binds private fields directly.
2. Every class declares `@XmlType(propOrder = {...})` giving the exact wire order.

So order is governed **only** by `propOrder`, never by field declaration order, getter order, or the
marshaller. If you add a field, you **must** add it to `propOrder` in the right place.

There is **no `ObjectFactory` and no `package-info.java`**, and no namespace is declared anywhere — so
JAXB emits **unqualified, no-namespace XML**, which matches the goAML convention. The only
`@XmlRootElement` is `Report` (`<report>`); everything else is a reusable `@XmlType` complex type.

---

## 3. The root: `Report` (`domain/Report.java`)

`@XmlRootElement(name = "report")`. Its `propOrder` is **header fields first, then the body**:

```
rentityId, rentityBranch, submissionCode, reportCode,
entityReference, fiuRefNumber, submissionDate, currencyCodeLocal,
reportingPerson, location, reason, action,
transactions, activity, reportIndicators
```

| Field | Java type | XML element | Meaning |
|-------|-----------|-------------|---------|
| `rentityId` | `Integer` | `rentity_id` | FIU-assigned Reporting Entity ID |
| `rentityBranch` | `String` | `rentity_branch` | RE branch |
| `submissionCode` | `SubmissionCode` enum | `submission_code` | E / M |
| `reportCode` | `ReportCode` enum | `report_code` | STR/SAR/.../DPMSR — **drives the shape** |
| `entityReference` | `String` | `entity_reference` | RE's own ref — used as the per-tenant idempotency key |
| `fiuRefNumber` | `String` | `fiu_ref_number` | the FIU request being answered (conditional) |
| `submissionDate` | `OffsetDateTime` | `submission_date` | uses the date adapter |
| `currencyCodeLocal` | `String` | `currency_code_local` | AED for UAE |
| `reportingPerson` | `ReportingPerson` | `reporting_person` | the MLRO/officer filing |
| `location` | `TAddress` | `location` | (reuses the address type) |
| `reason` | `String` | `reason` | conditional (STR/SAR) |
| `action` | `String` | `action` | conditional (STR/SAR) |
| `transactions` | `List<Transaction>` | `transaction` (repeating, no wrapper) | **transaction shape** |
| `activity` | `Activity` | `activity` | **activity shape** |
| `reportIndicators` | `List<ReportIndicator>` | wrapper `report_indicators` → `indicator` | why this report was flagged |

> `transactions` is ordered **before** `activity` in `propOrder`, so whichever shape you populate lands
> in the correct schema slot. A `Report` carries fields for both shapes but uses exactly one; the POJO
> does **not** enforce the either/or — the **validator** does (`SHAPE_REQUIRED` / `SHAPE_CONFLICT`).

List setters null-guard and defensively copy (`v == null ? new ArrayList<>() : new ArrayList<>(v)`).

---

## 4. Enums (the only type-safe code lists)

- **`ReportCode`** (`domain/enums/`): `STR, SAR, AIF, AIFT, ECDD, ECDDT, DPMSR`. Serializes by name.
- **`SubmissionCode`**: `E` (electronic), `M` (manual). The schema's `-` (unknown) is intentionally not
  modeled.

Everything else that *looks* like a code (countries, currencies, transmission modes, funds, ID types,
roles, genders…) is a **plain validated `String`**, because those are **FIU-defined lookup lists** that
refresh at runtime — see §8.

---

## 5. Dates: `GoamlDateTimeAdapter` (`domain/adapter/`)

An `XmlAdapter<String, OffsetDateTime>` used on **every** date field. Pattern:
**`yyyy-MM-dd'T'HH:mm:ss`** — second precision, **no timezone suffix**.
- marshal: converts to UTC, drops the offset, formats.
- unmarshal: parses as local datetime, attaches UTC.

There is **no date-only type** — birthdates, ID issue/expiry, incorporation dates all use this datetime
adapter (so e.g. an Emirates-ID issue date serializes as `2020-01-15T00:00:00`).

---

## 6. Money

All amounts and quantities are **`BigDecimal`**: `Transaction.amountLocal`,
`TForeignCurrency.foreignAmount` & `foreignExchangeRate`, and `GoodsServices.estimatedValue` /
`disposedValue` / `size`. No `double`/`float` anywhere.

---

## 7. The complex types (the building blocks)

Organized into sub-packages. Each lists its `propOrder` so you can see the wire shape.

### Parties — `domain/party/`

**`TPerson` / `TPersonMyClient`** (`t_person` / `t_person_my_client`) — **structurally identical**;
the only difference is the `@XmlType` name (so JAXB emits the stricter schema type). propOrder:
`gender, title, firstName, middleName, prefix, lastName, birthdate, birthPlace, mothersName, alias,
ssn, passportNumber, passportCountry, idNumber, phones, addresses, nationality1, nationality2,
nationality3, residence, email, occupation, employerName, identifications, comments, deceased,
deceasedDate, taxNumber, taxRegNumber, sourceOfWealth`. `birthdate`/`deceasedDate` use the date adapter;
`phones` and `addresses` are wrapped lists (`phones`→`phone`, `addresses`→`address`); `identifications`
is an **un-wrapped** repeating `identification`.

**`TPersonIdentification`** (`t_person_identification`) — propOrder: `type, number, issueDate,
expiryDate, issuedBy, issueCountry, comments`. `type` is a lookup string (e.g. `EID` = Emirates ID,
`PASSPORT`). Dates use the adapter.

**`TEntity` / `TEntityMyClient`** (`t_entity` / `t_entity_my_client`) — identical to each other.
propOrder: `name, commercialName, incorporationLegalForm, incorporationNumber, business, phones,
addresses, email, url, incorporationState, incorporationCountryCode, incorporationDate, taxNumber,
comments`. A company/organization (vs a natural person).

**`TAccount` / `TAccountMyClient`** (`t_account` / `t_account_my_client`) — identical to each other.
propOrder: `institutionName, swift, branch, account, currencyCode, accountName, iban, comments`.

**`TAddress`** (`t_address`) — propOrder: `addressType, address, town, city, zip, countryCode, state,
comments`. Reused widely (report `location`, person/entity addresses, goods address).

**`TPhone`** (`t_phone`) — propOrder: `contactType, communicationType, countryPrefix, number,
extension, comments`. ⚠️ **Element-name trap:** the elements carry a `tph_` prefix —
`tph_contact_type, tph_communication_type, tph_country_prefix, tph_number, tph_extension` (then plain
`comments`).

### Transactions — `domain/transaction/`

**`Transaction`** (`transaction`) — supports **bi-party** OR **multi-party**. propOrder:
`transactionNumber, internalRefNumber, transactionLocation, transactionDescription, dateTransaction,
teller, authorized, lateDeposit, datePosting, valueDate, transmodeCode, transmodeComment, amountLocal,
tFromMyClient, tFrom, tToMyClient, tTo, tParties, goodsServices`.
- ⚠️ **Element-name trap:** `transactionNumber` serializes as **`transactionnumber`** (all lowercase,
  no underscore). `amountLocal` → `amount_local` (BigDecimal). `lateDeposit` is `Boolean`.
- **Bi-party** = exactly one *from*-side and one *to*-side, using either the plain or `_my_client`
  variant: `tFrom`/`tFromMyClient` + `tTo`/`tToMyClient`.
- **Multi-party** (new in v4.0) = a list `tParties` (`t_party`, un-wrapped) of subjects with roles.

**`TFrom` / `TFromMyClient`** (`t_from` / `t_from_my_client`) — same propOrder
(`fromFundsCode, fromFundsComment, fromForeignCurrency, fromAccount, fromPerson, fromEntity,
fromCountry`); the difference is the **subject types**: `TFrom` uses non-client
`TAccount`/`TPerson`/`TEntity`, while `TFromMyClient` uses the strict client variants. Exactly one of
account/person/entity must be set (enforced by the validator).

**`TTo` / `TToMyClient`** — mirror of the From pair (`toFundsCode, toFundsComment, toForeignCurrency,
toAccount, toPerson, toEntity, toCountry`).

**`TParty`** (`t_party`) — a multi-party subject with a role. Holds **all six** subject variants
(person/account/entity × plain/my-client); exactly one must be set. propOrder: `role, person,
personMyClient, account, accountMyClient, entity, entityMyClient, fundsCode, fundsComment,
foreignCurrency, country, significance, comments`. `role` is a string (Buyer/Seller/…), `significance`
is an `Integer`.

**`TForeignCurrency`** (`t_foreign_currency`) — propOrder: `foreignCurrencyCode, foreignAmount,
foreignExchangeRate`. For transactions denominated in a non-local currency.

### Activity — `domain/activity/`

**`Activity`** (`activity`) — propOrder: `reportParties, goodsServices`. `reportParties` is a wrapped
list (`report_parties`→`report_party`); `goodsServices` is un-wrapped repeating `goods_services`.

**`ReportParty`** (`report_party_type`) — propOrder: `person, personMyClient, significance, reason,
comments`. ⚠️ **Currently models only the two person variants** (`person`/`person_my_client`);
account/entity subjects are deferred — so unlike `TParty`, a report party can only be a person today.

### Common — `domain/common/`

**`ReportingPerson`** (`t_person_registration_in_report`) — the MLRO/officer filing the report. Same
lead fields as a person minus the deceased/tax/source-of-wealth tail. propOrder: `gender, title,
firstName, middleName, prefix, lastName, birthdate, birthPlace, mothersName, alias, ssn, passportNumber,
passportCountry, idNumber, phones, addresses, nationality1, nationality2, nationality3, residence,
email, occupation, employerName, identifications, comments`.

**`GoodsServices`** (`goods_services`) — central to **DPMSR** (the gold/diamond line item). propOrder:
`itemType, itemMake, description, previouslyRegisteredTo, presentlyRegisteredTo, estimatedValue,
statusCode, statusComments, disposedValue, currencyCode, size, sizeUom, address, registrationDate,
registrationNumber, identificationNumber, comments`. `estimatedValue`/`disposedValue`/`size` are
`BigDecimal`; `address` is a `TAddress`.

**`ReportIndicator`** (`<indicator>`) — a single `@XmlValue String` (the text content of the element),
because indicators come from FIU lookup tables (e.g. `DPMSR_CASH_THRESHOLD`, `STRUCTURING_SUSPECTED`,
`PEP_REVIEW`).

---

## 8. Why so many fields are `String`, not enums

goAML defines large code lists (countries, currencies, transmission modes, funds types, ID types, item
types…) that the **FIU controls and refreshes** (downloadable from the B2B `OdataLookups` endpoint). If
these were Java enums, every FIU code-list change would force a code release. So they're modeled as
**plain Strings** and validated at runtime against loaded lookup sets (see
[05 — The Engine](05-engine.md) §6). Only the two truly-fixed lists — `ReportCode` and `SubmissionCode`
— are enums.

---

## 9. The two element-name traps (memorize these)

The model is uniformly snake_case **except**:
1. `TPhone` fields serialize with a **`tph_`** prefix (`tph_number`, `tph_country_prefix`, …).
2. `Transaction.transactionNumber` serializes as **`transactionnumber`** (lowercase, no underscore).

`@XmlElementWrapper` is also used **inconsistently by design** (to match the schema): `phones`,
`addresses`, `report_parties`, and `report_indicators` are wrapped; but `identification`, `t_party`,
`goods_services`, and `report`→`transaction` are flat repeating elements.

---

**Next:** [05 — The Engine](05-engine.md) — building, validating, marshalling, and packaging these objects.
