# Step 6 — DPMSR convenience builder

> **Status: ✅ DONE (2026-06-04) — builder + record/fluent forms + full-coverage test; full suite green. See "Outcome".**
> Part of [../plans/xsd-first-foundation.md](../plans/xsd-first-foundation.md) (step X.6).
> Predecessors: Steps 1–5 ✅ (engine on the generated model; all 7 goldens XSD-valid; lookups reconciled).
> Conventions: builder is product-core engine code → lives in `engine/build/` (see `docs/CONVENTIONS.md` §1).

---

## 1. What this step is, and why

Building a goAML report against the raw generated model is **verbose and easy to get wrong** — `SampleReports`
proved it: you must hand-wire JAXB wrapper classes (`ActivityType.ReportParties`, `GoodsServices`,
`TEntity.Phones/Addresses`, `TPersonMyClient.Identifications`, …) and remember non-obvious XSD-mandatory fields
(`birthdate`, a `phones` wrapper, `tax_reg_number`, `identifications` with `issue_date`, etc.). That's fine for
test fixtures but unacceptable as the **production way** to assemble a DPMSR.

Step 6 adds a **`DpmsrReportBuilder`** that takes compact, domain-meaningful input and emits a **valid**
generated `Report` (DPMSR-shaped: header + `reportActivity` with `report_parties` + `goods_services`), hiding
the wrapper/mandatory-field tax. This is the seam that **Phase 1.5** (accounting txn → auto-create DPMSR draft)
and the **Phase 12 plugin** ("build DPMSR" tool) both need — they pass structured data, not hand-built JAXB.

**Generic & invoice-driven, NOT gold-specific (confirmed 2026-06-04).** The real sample happens to be a gold
business, but the builder is **schema-driven** (it assembles the model generated from the authoritative
`goAMLSchema.xsd`, so it conforms to the FIU template by construction) and works for **any DPMS dealer's
invoice**. Every goods attribute — `item_type`, `estimated_value`, `currency`, `size`/`size_uom`, description —
is a **caller input from the invoice line**, never hardcoded. `item_type` is the goAML `trans_item_type` code
list, which is broad (GOLD, DIMND/diamond, silver, gemstones, jewellery, watches, …) and is generated as a
plain `String` (the enum exceeds xjc's size cap), with validity enforced by the **XSD gate**. At the schema
level the only mandatory goods field is `item_type`; value/currency/size are optional, and the **AED 55,000
threshold is a business rule** (`ReportValidator`) — so the builder does not over-constrain general invoices.
GOLD appears only in the test fixture, never in the builder.

## 2. Shape of a real DPMSR (what the builder targets)

From the real portal sample (`TR.2079.200000309.xml`): a DPMSR carries header fields + a `reporting_person`
(the dealer's MLRO) + `location` + `reason` + `action` + `report_indicators`, then an **activity** with:
- **`report_parties` → one `report_party` → `entity`** = the counterparty business (e.g. *Example Jewellery
  L.L.C*) with `incorporation_number`, phones, and a **`director_id`** (a person with `role` = ATR), and
- **`goods_services` → `item`** = the precious-metal line (`item_type` GOLD, `estimated_value`,
  `currency_code` AED, `size`/`size_uom`, `registration_date`).

So the real-world DPMSR party is **entity-shaped**. (The XSD also permits `person`/`person_my_client` in a
`report_party`; our current `SampleReports` DPMSR uses a person — valid, but not the typical dealer case.)

## 3. API — complete DPMSR coverage over the generated leaf types

**Goal: every field the DPMSR/activity schema supports is reachable — nothing deferred.** The DPMSR field
surface is large (`ReportPartyType` has 6 subject kinds; `TEntity` 28 fields + nested directors / phones /
addresses / identifications / related-parties / sanctions / network-devices; `TPersonMyClient` 45 fields;
`TTransItem` 17; plus accounts). A **parallel input-DTO model would duplicate hundreds of XSD fields** and
inevitably drift from the schema. So the builder is built **over the generated leaf types themselves** — they
are 100% complete from `goAMLSchema.xsd` by construction and can never fall behind it — with an ergonomic
assembly layer that removes the only real pain points: the **JAXB wrapper classes**, the **choice slot**, and
the **header boilerplate**.

**Two entry styles, one assembly path.** The single input contract is the **`DpmsrReportInput` record**; the
fluent builder is just an ergonomic way to construct that record. Both end up calling the same
`DpmsrReportBuilder.build(DpmsrReportInput)`.

The record stays thin by **carrying the generated leaf types** for the rich nested objects (full field access,
no duplication):
```java
public record DpmsrReportInput(
    int rentityId, String rentityBranch, String entityReference, OffsetDateTime submissionDate,
    String fiuRefNumber,                          // optional; present for completeness
    TPersonRegistrationInReport reportingPerson,  // generated leaf type — all its fields
    TAddress location,
    String reason, String action, List<String> indicators,
    List<ReportPartyType> parties,                // built via GoamlParties (all 6 subjects)
    List<TTransItem> goods) {                     // generated — all 17 t_trans_item fields, item_type from invoice
    public static Builder builder() { ... }       // fluent construction (below)
}
```

**(a) Record form — for programmatic callers (RabbitMQ consumer, plugin) that already hold structured data:**
```java
DpmsrReportInput input = new DpmsrReportInput(3177, "DXB-MAIN", "PAY 0001 INV 0001", submissionDate, null,
        mlro, location, reason, action, List.of("DPMSJ"),
        List.of(GoamlParties.entity(jeweller, null, "Seller of gold above AED 55,000", null)),
        List.of(goldItem, diamondItem));
Report report = dpmsrReportBuilder.build(input);                       // or buildAndValidate(input, "ae")
```

**(b) Fluent form — for hand-coding / tests:**
```java
Report report = dpmsrReportBuilder.build(
    DpmsrReportInput.builder()
        .rentityId(3177).entityReference("PAY 0001 INV 0001").submissionDate(submissionDate)
        .reportingPerson(mlro).location(location).reason(reason).action(action).indicators("DPMSJ")
        .party(GoamlParties.entity(jeweller, null, "...", null))      // all 6 subjects supported
        .goods(goldItem, diamondItem)                                 // 1..n lines
        .build());
```

**Ergonomic helpers (the only new "model" code), in `engine/build/`:**
- `GoamlWrappers` — turn plain lists into the JAXB wrapper objects so callers never touch them:
  `phones(TPhone...)`, `addresses(TAddress...)`, `identifications(TPersonIdentification...)`,
  `entityIdentifications(...)`, `directorIds(TEntity.DirectorId...)`, `reportParties(ReportPartyType...)`,
  `goodsServices(TTransItem...)`, `reportIndicators(String...)`, etc. (one per wrapper class in the activity graph).
- `GoamlParties` — wrap any subject into a `ReportPartyType`: `entity(...)`, `entityMyClient(...)`, `person(...)`,
  `personMyClient(...)`, `account(...)`, `accountMyClient(...)` — covering **all 6** `report_party_type` subjects.
- `DpmsrReportBuilder` (fluent) — header + reporting_person + location + reason/action/indicators + parties +
  goods → assembles `ActivityType`, sets it via `setReportActivity(...)` (the Step-4a choice slot), applies the
  DPMSR header (submission_code `E`, report_code `DPMSR`, currency `AED`) by reusing
  `ActivityReportBuilder`/`ReportHeader`, returns the `Report`.

This guarantees **full coverage** (the caller can set any DPMSR field via the generated leaf objects) with a
small, maintenance-free helper layer — versus a giant parallel DTO model that would need updating on every
schema bump. Fluent form is chosen over a single mega-record because no record can ergonomically express the
full optional surface.

## 4. Validation integration

The builder produces a `Report`; it does **not** silently bless it. A companion method makes the contract
explicit:
```java
ValidatedReport buildAndValidate(DpmsrReportInput in, String jurisdiction)  // runs ReportValidator + XSD gate
```
returning the `Report` plus the `ValidationResult` (business rules) and the XSD result, so callers (draft
creation, the plugin) get a clear pass/fail. The unit test asserts a canonical input builds a report that is
**ReportValidator-clean AND XSD-valid** — the same bar Steps 4–5 hold.

## 5. Decisions — resolved by the "cover everything" directive (2026-06-04)

1. **Coverage:** **all** DPMSR/activity fields, nothing deferred — achieved by building over the complete
   generated leaf types (§3).
2. **Party shape:** **all 6** `report_party_type` subjects supported (entity, entity_my_client, person,
   person_my_client, account, account_my_client) via `GoamlParties` — not just the gold-sample entity case.
3. **API form:** **both** a `DpmsrReportInput` record (for programmatic callers) **and** a fluent
   `DpmsrReportInput.builder()` (for hand-coding) — they share one assembly path (`build(DpmsrReportInput)`).
   The record carries the **generated leaf types** for the rich nested objects, so full coverage comes from the
   leaf types and there's no parallel-DTO duplication; the wrapper helpers only remove the JAXB
   wrapper/choice/header boilerplate.
4. **Multiple parties & goods:** both are lists (an invoice spans several goods lines; a report may name
   several parties).

## 6. Files (planned)

| File | Change |
|---|---|
| `engine/build/DpmsrReportBuilder.java` | **new** — `@Component`: `build(DpmsrReportInput)` + `buildAndValidate(...)` |
| `engine/build/DpmsrReportInput.java` | **new** — input record (carries generated leaf types) + fluent inner `Builder` |
| `engine/build/GoamlWrappers.java` | **new** — list → JAXB-wrapper helpers (phones/addresses/identifications/directorIds/reportParties/goodsServices/indicators/…) |
| `engine/build/GoamlParties.java` | **new** — wrap any of the 6 subjects into a `ReportPartyType` |
| `engine/build/ValidatedReport.java` | **new** — `{Report, ValidationResult rules, ValidationResult xsd}` holder |
| `src/test/.../engine/build/DpmsrReportBuilderTest.java` | **new** — see §7 |
| *(reuse)* `ReportHeader`, `ActivityReportBuilder`, `ReportValidator`, `XsdSchemaValidator` | unchanged |

## 7. How it's verified

```bash
./gradlew test --tests '*DpmsrReportBuilderTest'
```
**Expected (proving full coverage, not just the happy path):**
- A **maximal DPMSR** — entity party with ≥2 `director_id`s, phones, addresses, entity identifications, plus a
  **second party** of a different subject kind (e.g. person_my_client), and **multiple goods lines** with a mix
  of `item_type`s — builds a `Report` that is **ReportValidator-clean AND XSD-valid**.
- A **minimal DPMSR** (only the required fields) also builds valid — confirms optional fields are truly optional.
- A spot-check that fields set on the leaf objects survive into the marshalled XML (e.g. `incorporation_number`,
  a director's `role`, a second goods line's `item_type`).
- **Both entry forms exercised:** the same DPMSR built via the **record** constructor and via the **fluent
  `DpmsrReportInput.builder()`** produce an equivalent, valid `Report`.

No Docker needed. Then full `./gradlew test` stays green.

## 8. Risks / caveats (honest)

1. **Full field coverage is the requirement (no deferral).** Because the builder works over the generated leaf
   types, **every field the DPMSR/activity schema defines is reachable today** — including all 6 party subjects,
   directors, identifications, related parties, sanctions, network devices, and every `t_trans_item` attribute.
   The XSD gate is the backstop that what's emitted is valid. (Which fields the UAE FIU treats as
   *business-mandatory* beyond the XSD is still a UAT/field-acquisition item — that tunes `ReportValidator`
   rules, not the builder's reach.)
2. **More XSD-mandatory fields may surface** for the entity/director shape (as they did for person in Steps
   4–5). Handled by the same iterate-until-XSD-valid loop in the test.
3. **Scope:** DPMSR only (the Phase-1 target). Other report types get their own builders with their phases —
   not this step.
4. **`item_type` validity** is enforced by the XSD gate today (the `trans_item_type` enumeration). A UAE
   `item_type` lookup + a `ReportValidator` check (mirroring the Step-5 transmode/currencies pattern, guarded by
   `LookupXsdConsistencyTest`) can be added later if the business wants a friendlier pre-XSD error — additive,
   not required now.

## 9. What this step does NOT do

No other report-type builders, no REST endpoint (Phase 7), no RabbitMQ auto-create wiring (Phase 1.5), no docs
refresh (Step 7), no engine rule changes. It only adds an ergonomic, validated DPMSR assembly API over the
generated model.

---

## Outcome (2026-06-04) — ✅ done, full suite green

**Built** in `engine/build/`: `DpmsrReportInput` (record + fluent inner `Builder`, carrying generated leaf
types), `DpmsrReportBuilder` (`@Component`: `build(input)` + `buildAndValidate(input, jurisdiction)`),
`GoamlParties` (all 6 report_party subjects), `GoamlWrappers` (activity-level wrappers + a generic
`wrap(wrapper, listAccessor, items...)` for any leaf wrapper), `ValidatedReport` (`{report, rules, xsd}`).

**Test** `DpmsrReportBuilderTest` (no Docker): a **maximal** DPMSR — entity party (name, incorporation,
phones, **2 directors** with role `ATR`) + a **second party of a different subject** (`person_my_client`) +
**2 goods lines** (`GOLD` + `DIMND`) — builds **ReportValidator-clean AND XSD-valid**; a **minimal** DPMSR is
valid too; **record and fluent forms marshal to identical XML**; and set leaf fields survive into the XML
(`incorporation_number`, director `role` ATR, second item_type DIMND). 3 tests pass first run.

**Validator fix (real bug surfaced by the entity-party case):** `ReportValidator.validateReportPartySubject`
counted only `person`/`person_my_client`, so it **rejected the entity party the real DPMSR uses**. Fixed to
require exactly one of all **6** subjects (person/account/entity, plain or my_client) — matching the existing
transaction `t_party` check. Existing activity samples (person_my_client) stay green.

**Confirmed generic / invoice-driven, not gold-specific:** `item_type` and all goods fields are inputs (test
uses GOLD *and* DIMND); the only XSD-mandatory goods field is `item_type`; the AED 55,000 trigger stays a
business rule. Full DPMSR field reach via the generated leaf types — nothing deferred.

**Verify:** `./gradlew test --tests '*DpmsrReportBuilderTest'` → green; full `./gradlew test` (Docker up) →
BUILD SUCCESSFUL.
