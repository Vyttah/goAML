# 04 — Domain Model

> The data model: **xjc-generated JAXB types** built from the authoritative goAML XSD, that serialize
> to/from the goAML `<report>` XML. Generated package: `com.vyttah.goaml.domain.generated`.

> **As of the XSD-first migration (2026-06):** the domain is **no longer hand-modeled**. It is generated
> at build time by **xjc** from the vendored, authoritative **goAML 5.0.2** schema
> (`src/main/resources/xsd/goaml/5.0.2/goAMLSchema.xsd`). Element names, ordering, types, and code-list
> enums are now the **schema's** responsibility, not ours. See
> [`.planning/plans/xsd-first-foundation.md`](../.planning/plans/xsd-first-foundation.md) for the migration.

---

## 1. What this layer is (and isn't)

The model is the set of Java classes xjc emits from `goAMLSchema.xsd`. JAXB (Jakarta `jakarta.xml.bind`)
turns a `Report` object graph into XML and back. The schema is the single source of truth: if the XSD
says a field exists, is mandatory, or has a fixed code list, that's reflected in the generated type — we
don't restate it.

What the generated types are: plain data holders with JAXB annotations (`@XmlType`, `@XmlElement`,
`propOrder`, enums for closed code lists) that exactly match the schema's wire shape.

What they are **not**: they carry **no business validation**. The `*MyClient` "stricter" variants are
structurally close to their base types — the extra mandatory-field / conditional rules live in the
**engine's validator** ([05 — The Engine](05-engine.md)), not here. Generated POJOs shape the XML; the
engine enforces correctness; the **XSD gate** enforces structural conformance.

---

## 2. How the model is generated (xjc)

Codegen is wired in `build.gradle` via the **`com.github.bjornvester.xjc` plugin (1.9.0)**:

```groovy
xjc {
    xsdDir.set(layout.projectDirectory.dir("src/main/resources/xsd/goaml/5.0.2"))
    bindingFiles.setFrom(layout.projectDirectory.file("src/main/jaxb/goaml-bindings.xjb"))
    defaultPackage.set("com.vyttah.goaml.domain.generated")
    useJakarta.set(true)          // jakarta.xml.bind, not legacy javax
    xjcVersion.set("4.0.5")       // pinned to match jaxb-runtime 4.0.5 (see note below)
    options.set(["-extension"])   // required for the .xjb javaType adapter binding
}
```

Key facts:
- **Output is generated, never committed.** It lands in `build/generated/sources/xjc` and is regenerated
  on every build. There is **no committed `domain/generated/*.java`** — don't look for it in `src/`.
  (`.gitignore` is anchored to `/build/` so this generated tree stays out of git but a *source* package
  named `build` elsewhere would not be ignored.)
- **xjc version is pinned to 4.0.5** to match the project's `jaxb-runtime` and the BOM-managed codemodel.
  The plugin defaults to 4.0.6, which mismatches codemodel 4.0.5 → `NoSuchMethodError`
  `JDocComment.appendXML` (jaxb-ri #1854). Keeping tool + codemodel on 4.0.5 avoids it.
- Only `goAMLSchema.xsd` lives in `xsdDir`, so xjc compiles exactly that one schema. It is self-contained
  (no `xs:include`/`xs:import`).

### The only hand-written file in `domain/`
**`domain/adapter/GoamlDateTimeAdapter`** — an `XmlAdapter<String, OffsetDateTime>` referenced by the
binding file (below). Everything else under `domain.generated` is machine-generated.

---

## 3. The binding file: `src/main/jaxb/goaml-bindings.xjb`

Two customizations make the generated model usable:

### 3a. Dates → `OffsetDateTime`
A `globalBindings` `javaType` maps the schema's `sql_date` type (an `xs:dateTime` restriction) to
`java.time.OffsetDateTime` via `GoamlDateTimeAdapter`, instead of the default `XMLGregorianCalendar`. The
adapter's wire format is **`yyyy-MM-dd'T'HH:mm:ss`** — second precision, **no timezone suffix**, normalized
to UTC on marshal. There is **no date-only type**: birthdates, ID issue/expiry, incorporation dates all
serialize through this datetime adapter (e.g. an Emirates-ID issue date → `2020-01-15T00:00:00`).

### 3b. The `Report` choice-slot rename (the one non-obvious binding)
The `<report>` complexType ends with an `<xs:choice>` whose **two branches both declare an element named
`activity`**:
- branch 1 = `<sequence>(transaction+, activity?)` — the **transaction-shaped** report;
- branch 2 = `activity` — the **activity-shaped** report (DPMSR / SAR / AIF / ECDD).

That name clash makes xjc give up on typed properties and dump the **whole report body** (including all
header fields) into a catch-all `List<JAXBElement<?>> content`. The binding renames **branch 1's** nested
`activity` to the property **`reportActivity`**, breaking the clash so xjc regenerates `Report` with typed
getters (`getReportCode()`, `getRentityId()`, …).

**The critical accessor fact:** JAXB's generated unmarshaller **greedily routes any `<activity>` element
to branch 1** even when no `<transaction>` precedes it. So **`getReportActivity()` is the slot actually
populated for *both* report shapes** — it is THE activity accessor the engine uses. Branch 2's
`getActivity()` is **vestigial** (never populated by the JAXB RI), but must keep a distinct property name
to break the catch-all. Marshalling `getReportActivity()` with no transactions emits a bare `<activity>`,
which is XSD-valid via branch 2 (confirmed by round-trip XSD re-validation).

> If you ever see `getActivity()` return null on an activity report, that's expected — use
> `getReportActivity()`.

---

## 4. The shape of the generated tree

The root is **`Report`** (`@XmlRootElement(name="report")`), and an **`ObjectFactory`** is generated
alongside it (the marshaller builds its `JAXBContext` from `ObjectFactory.class`, not `Report.class`, so
the whole generated package is in context). Notable generated types the engine touches:

| Generated type | Role |
|---|---|
| `Report` | root; typed header getters + the choice slot (`getReportActivity()`) + `getTransaction()` (a `List<Report.Transaction>`) + `getReportIndicators()` (wrapper → `getIndicator()`) |
| `Report.Transaction` | a transaction (money movement); bi-party (`getTFrom()/getTFromMyClient()` + `getTTo()/getTToMyClient()`) or multi-party (`getInvolvedParties().getParty()`) |
| `ActivityType` | the activity body; `getReportParties().getReportParty()` + `getGoodsServices().getItem()` |
| `ReportPartyType` | a report party: holds all **six** subject kinds (person/account/entity × plain/my-client) + `reason`/`comments`/`role`/`significance`/`isSuspected` |
| `TTransItem` | a goods/services line item (the gold/diamond line) — central to DPMSR |
| `TParty` | a multi-party transaction subject (six subject kinds + `role`) |
| `TPerson` / `TPersonMyClient` | natural-person party (base / RE's-own-client variant) |
| `TEntity` / `TEntityMyClient` | company/organization party; `TEntity.DirectorId` (extends `TPerson`, adds `role` of `EntityPersonRoleType`) is the director used by real DPMSR entity parties |
| `TAccount` / `TAccountMyClient` | account party |
| `TPersonRegistrationInReport` | the MLRO/officer filing the report (`reporting_person`) |
| `TAddress`, `TPhone`, `TPersonIdentification` | reused leaf types |

### Closed code lists become enums; open ones stay `String`
Where the XSD declares a **closed enumeration**, xjc generates a Java **enum** — e.g.:
- **`ReportType`** — every schema report-type code. We functionally model **7** today (STR, SAR, AIF,
  AIFT, ECDD, ECDDT, DPMSR); the other ~10 are valid enum values deferred to later phases.
- **`CurrencyType`** — currency codes (`CurrencyType.AED`); `.value()` gives the wire string.
- **`FundsType`**, **`EntityPersonRoleType`** (e.g. `ATR`), and others.

Where a code list is **very large** (xjc has an enum-member cap), the type stays a **plain `String`** even
though the schema constrains it. The two the engine cares about:
- **`item_type`** (`TTransItem.getItemType()`) — `String` (the broad `trans_item_type` codelist).
- **`transmode_code` / conduction type** (`Report.Transaction.getTransmodeCode()`) — `String`.

These String-typed-but-enumerated fields are exactly why the **lookup ⊆ XSD-enum invariant** exists (see
[05 §7](05-engine.md)) — the validator checks them against a lookup set that must be a subset of the XSD
enumeration, so a validator-clean report can't fail the schema.

### Money
All amounts and quantities are **`BigDecimal`** (`TTransItem.estimatedValue/disposedValue/size`,
`Report.Transaction.amountLocal`, FX amounts/rates). No `double`/`float` anywhere.

### Wrapper-per-owner
JAXB generates a **distinct wrapper class per owner** for repeating elements — e.g. `TEntity.Phones` is a
different type from `TPersonMyClient.Phones`, each with its own `getPhone()`. This "wrapper-per-owner tax"
is why `GoamlWrappers.wrap(...)` ([05 §2](05-engine.md)) exists: a single generic helper instead of one
method per wrapper.

---

## 5. Element ordering & naming — now the schema's job

The schema uses `<sequence>`, so element order is significant — but with the model generated, **xjc emits
`@XmlType(propOrder=…)` straight from the XSD**, so ordering is correct by construction. You no longer
hand-maintain `propOrder`. Likewise the historical naming quirks (`tph_`-prefixed phone fields,
`transactionnumber` with no underscore, inconsistent element wrappers) are all reproduced automatically
from the schema — they're still there in the XML, but you read them off the generated getters
(`getTransactionnumber()`, `TPhone.getTphNumber()`, …) rather than memorizing them.

JAXB emits **unqualified, no-namespace XML**, matching the goAML convention.

---

## 6. Why validation isn't here

The generated model guarantees *structural* shape; the **`XsdSchemaValidator`** ([05 §3](05-engine.md))
guarantees structural *conformance* against the same XSD at runtime; and the **`ReportValidator`**
enforces the conditional **business** rules (which field is mandatory for which report code, the AED
threshold, lookup membership). Three distinct gates — keep them separate in your head.

---

**Next:** [05 — The Engine](05-engine.md) — building, validating (rules **and** XSD), marshalling, and packaging these objects.
