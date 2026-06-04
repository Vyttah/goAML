# Step 4 — Re-point the engine onto the generated model & retire hand-modeled `domain/*`

> **Status: 🔲 PROPOSED (2026-06-04) — awaiting your approval before implementation.**
> Part of [../plans/xsd-first-foundation.md](../plans/xsd-first-foundation.md) (step X.4).
> Predecessors: Step 1 ✅ (XSD gate) · Step 2 ✅ (xjc codegen) · Step 3 ✅ (real samples round-trip).
> ⚠️ **This is the substantive/destructive step** STATE.md flagged — it deletes the hand-modeled `domain/*`
> and rewires every engine class onto the generated model. Bigger and riskier than Steps 1–3. Proposed as
> **four reviewable sub-commits (4a–4d)**; you can approve the whole plan and I'll still commit each slice
> separately for a clean history (and easy rollback).

---

## 1. What this step is, and why

Today the engine (`build/`, `marshal/`, `validation/`, `jurisdiction/`) is written against the **hand-modeled
`domain/*` POJOs** — a clean, typed model we wrote by hand in Phase 3. Steps 2–3 generated a **schema-faithful
JAXB model** from the authoritative `goAMLSchema.xsd` and proved it round-trips the FIU's real reports. Step 4
**makes the generated model the single source of truth**: re-point every engine class onto it, then **delete
the hand-modeled `domain/*`** so there is no drift and no second model to maintain.

After this step: one model, generated from the schema; the engine builds/validates/marshals against it; FIU
schema bumps = re-run codegen + adjust, nothing hand-maintained.

## 2. The crux — why this isn't a find-and-replace (read this first)

Step 3 surfaced the key obstacle: the generated `Report` root has **no typed getters**. Its entire body is a
single catch-all `List<JAXBElement<?>>` — `Report.getContent()` (`@XmlElementRefs`). The hand-modeled `Report`,
by contrast, has `getReportCode()`, `getRentityId()`, `getActivity()`, `getTransactions()`, … which the
validator and builders call **everywhere** (e.g. `ReportValidator` alone makes ~40 typed-getter calls).

**Root cause (confirmed in the XSD):** the `<report>` complexType ends with an `<xs:choice>` (line 88) whose
two branches **both declare an `activity` element** (lines 308 & 310) — branch 1 = "transactions + optional
trailing activity", branch 2 = "activity only". xjc can't form a deterministic set of properties from that, so
it falls back to the catch-all `content` list — and crucially, **once any part of the content model is
non-deterministic, xjc dumps the *whole* report body into `content`**, including all the leading header fields
(`rentity_id`, `report_code`, `submission_date`, …) that are *not* themselves part of the choice.

So the real lever is: **resolve that one `activity` name clash and xjc will regenerate `Report` with proper
typed getters for everything.** That turns Step 4 from "rewrite the engine against an awkward `JAXBElement`
list" into "mostly-mechanical re-point with type swaps."

## 3. Approach — fix the codegen first (recommended), shim only if needed

**3a. (Step 4a, prerequisite) Eliminate the catch-all via a `.xjb` property rename.**
Add a targeted binding in `goaml-bindings.xjb` that renames **one** of the two clashing `activity` properties
(e.g. rename the transaction-branch's optional `activity` at XSD line 308 → property `transactionActivity`),
leaving the activity-shaped branch's `activity` (line 310) as `activity`. With distinct names, xjc can emit
typed properties for the entire `report` content model.

- **First action at implementation = a ~15-minute codegen spike**: apply the rename, run `./gradlew compileJava`,
  and confirm the generated `Report` now exposes `getReportCode()`, `getRentityId()`, `getSubmissionDate()`,
  `getActivity()`, `getTransaction()` (or list), `getReportIndicators()`, etc. — i.e. the `getContent()`
  catch-all is gone. **Re-run `GeneratedModelRoundTripTest`** (with the by-local-name reads updated to typed
  getters) to confirm the real samples still round-trip after the rename.
- **If the rename does NOT yield typed getters** (xjc choice handling can be stubborn): fall back to **3a′ — a
  thin read/write accessor** (`engine/model/ReportAccess` helper) that hides the `getContent()` list behind
  `reportCode(report)`, `activity(report)`, `setHeader(report, …)` using `ObjectFactory.createReportXxx(...)`.
  The engine then talks to the accessor, not the raw list. Less elegant but fully workable; same end behaviour.
- Either way, Step 4a is **codegen/plumbing only** — no engine logic change yet — and ends green
  (`GeneratedModelRoundTripTest` + `GeneratedModelTest`).

> Why fix codegen rather than shim by default: the generated nested types we already use are **pleasant and
> typed** (`ActivityType.getGoodsServices().getItem()`, `TTransItem.getItemType()`, `ReportPartyType.getEntity()`,
> `TEntity.getName()` — all confirmed in Step 3). The *only* ugly spot is the `Report` root. Fixing it at the
> source keeps the entire engine clean and readable, matching the surrounding code.

## 4. The type-mapping (hand-modeled → generated)

| Hand-modeled (`domain/*`) | Generated (`domain.generated.*`) | Re-point note |
|---|---|---|
| `domain.Report` | `Report` (`@XmlRootElement "report"`) | typed getters after 4a; root marshals directly |
| `domain.enums.ReportCode` | `ReportType` enum | same constants (DPMSR/STR/SAR/AIF/ECDD/…); swap type |
| `domain.enums.SubmissionCode` | `SubmissionType` enum | swap type |
| `currency_code_local : String` | `CurrencyType` enum | compare via `.value()` to jurisdiction's `"AED"` |
| `domain.activity.Activity` | `ActivityType` | `getReportParties()` / `getGoodsServices()` typed |
| `domain.activity.ReportParty` | `ReportPartyType` | `getEntity()`/`getPerson()` typed |
| `domain.common.GoodsServices` | `TTransItem` (goods item) | `getEstimatedValue()` now `BigDecimal`, `getCurrencyCode()` now `CurrencyType` |
| `domain.common.ReportingPerson` | `TPersonRegistrationInReport` | reporting_person type |
| `domain.party.TEntity/TPerson/TAddress/TPhone/...` | generated `TEntity/TPerson/TAddress/TPhone/...` | same names, generated package |
| `domain.transaction.Transaction/TParty/TFrom/...` | `Report.Transaction` (+ generated party types) | transaction-shape re-point (lower priority — DPMSR is activity-shaped) |
| `domain.adapter.GoamlDateTimeAdapter` | **kept** (reused by generated code) | survives the deletion |

## 5. Sub-steps (each its own commit)

**4a — Codegen: remove the `Report` catch-all** (`goaml-bindings.xjb` rename + spike). Engine untouched;
`GeneratedModelRoundTripTest` updated to typed getters and green. *(If shim fallback: add `ReportAccess`.)*

**4b — Re-point `marshal/` + `build/`.**
- `ReportMarshaller` → `JAXBContext.newInstance(generated.ObjectFactory.class)`, marshal/unmarshal generated
  `Report`. (Mirrors the helper already proven in `GeneratedModelRoundTripTest`.)
- `ActivityReportBuilder`, `TransactionReportBuilder`, `ReportHeader`, `ReportHeaderApplier` → build the
  generated `Report` (set header via typed setters after 4a, or `ObjectFactory.createReportXxx(...)` via the
  accessor). DPMSR/activity path first; transaction path kept compiling.

**4c — Re-point `validation/` + `jurisdiction/`.**
- `ReportValidator` → generated types; enum swaps (`ReportType`/`SubmissionType`), currency via `CurrencyType`,
  goods via `TTransItem` (`BigDecimal` values), activity/parties via generated nested types. **Rule logic and
  messages unchanged** — only the model it reads.
- `JurisdictionConfig`, `JurisdictionRegistry` → `ReportType`.
- `ReportValidatorTest` updated to construct generated objects; **all existing rule assertions must still pass**
  (same codes: MANDATORY, SHAPE_CONFLICT, DPMS_THRESHOLD, CURRENCY_MISMATCH, …).

**4d — Delete hand-modeled `domain/*` + retire its tests.**
- Delete `domain/Report.java`, `domain/activity/*`, `domain/common/*`, `domain/enums/*`, `domain/party/*`,
  `domain/transaction/*`. **Keep** `domain/adapter/GoamlDateTimeAdapter.java`.
- Delete/replace the hand-model round-trip tests `domain/DpmsrReportRoundTripTest.java` +
  `domain/StrTransactionRoundTripTest.java` (the generated round-trip is already covered by
  `GeneratedModelRoundTripTest`).
- Update the test fixture `engine/SampleReports.java` to build generated objects (used by goldens + marshaller
  tests).

## 6. Goldens (the Step 4 / Step 5 boundary)

Re-pointing the marshaller changes the emitted XML (schema-exact element order, formatting), so the 7 golden
files (`src/test/resources/golden/*.xml`) generated from the hand-model **will** differ. To keep the suite
green at every commit, **Step 4d regenerates the existing 7 goldens** using the build's existing
`-Dgoaml.golden.regenerate=true` switch, and each regenerated golden is **XSD-validated** (Step-1 gate) before
being committed — so we don't bless malformed output. **Step 5** then *expands* coverage toward the 17 real
report codes and cross-checks goldens against the real samples. (If you'd rather keep Step 4 purely about the
re-point and let Step 5 own all golden changes, I'll instead mark `EngineGoldenTest` `@Disabled` at 4d with a
TODO pointing to Step 5 — your call; **default = regenerate-and-validate in 4d** so nothing stays red.)

## 7. Files touched

| File | Change |
|---|---|
| `src/main/jaxb/goaml-bindings.xjb` | **modified** (4a) — rename one `activity` property to kill the catch-all |
| `engine/marshal/ReportMarshaller.java` | **modified** (4b) — generated `Report` + `ObjectFactory` context |
| `engine/build/{ActivityReportBuilder,TransactionReportBuilder,ReportHeader,ReportHeaderApplier}.java` | **modified** (4b) |
| `engine/validation/ReportValidator.java` | **modified** (4c) — generated types, same rules |
| `engine/jurisdiction/{JurisdictionConfig,JurisdictionRegistry}.java` | **modified** (4c) — `ReportType` |
| `domain/Report.java`, `domain/{activity,common,enums,party,transaction}/**` | **deleted** (4d) |
| `domain/adapter/GoamlDateTimeAdapter.java` | **kept** |
| `src/test/.../engine/SampleReports.java` | **modified** (4d) — build generated objects |
| `src/test/.../engine/{EngineGoldenTest,marshal/ReportMarshallerTest,validation/ReportValidatorTest}.java` | **modified** |
| `src/test/.../domain/{DpmsrReportRoundTripTest,StrTransactionRoundTripTest}.java` | **deleted** |
| `src/test/resources/golden/*.xml` (7) | **regenerated + XSD-validated** (4d) — see §6 |
| *(optional)* `engine/model/ReportAccess.java` | **new**, only if the 4a shim fallback is needed |

## 8. How it's verified

```bash
docker compose up -d postgres   # only for the full suite; the engine slices don't need it
./gradlew test --tests '*GeneratedModelRoundTripTest' \
               --tests '*ReportValidatorTest' \
               --tests '*ReportMarshallerTest' \
               --tests '*EngineGoldenTest' \
               --tests '*GoamlXsdValidationTest'
```
**Expected at the end of 4d:** engine compiles against the generated model only; the hand-modeled `domain/*`
is gone (except the adapter); every engine test green; the 7 regenerated goldens both match and **pass the XSD
gate**. The 6 Testcontainers DB tests still need Docker (unrelated).

## 9. Risks / caveats (honest)

1. **The `.xjb` rename might not fully remove the catch-all.** xjc `<xs:choice>` handling is finicky. Mitigation:
   the 4a spike confirms before any engine change; fallback is the `ReportAccess` shim (§3a′) — same outcome,
   more boilerplate. **Step 4 does not proceed past 4a until `Report` is workable.**
2. **Biggest blast radius so far.** Touches every engine class + their tests + deletes a package. Mitigation:
   four separate commits (4a→4d), each compiling and green; trivial `git revert` of any slice.
3. **Enum/`BigDecimal` semantic shifts.** `currency_code_local` and goods currency become `CurrencyType`;
   `estimated_value` is `BigDecimal`. The validator's currency compare + threshold math adapt accordingly —
   covered by re-running the **existing** `ReportValidatorTest` assertions (same error codes).
4. **Transaction-shape re-point is heavier than activity-shape.** DPMSR (our first target) is activity-shaped,
   so 4b/4c prioritize that path; the transaction path is re-pointed enough to compile and keep its tests, but
   deep transaction coverage rides with Step 5's report-type expansion.
5. **Goldens change.** Expected and benign (different model, same meaning) — §6 regenerates + XSD-validates them
   rather than hand-editing, so we never bless malformed XML.
6. **PII in fixtures unchanged** — same anonymize-before-push task already tracked in STATE.md.

## 10. Done criteria

- The generated model is the **only** report model: hand-modeled `domain/*` deleted (adapter kept), nothing in
  `src/main` imports `com.vyttah.goaml.domain.{Report,activity,common,enums,party,transaction}`.
- `Report` exposes typed getters (4a) — or a documented `ReportAccess` shim — and `GeneratedModelRoundTripTest`
  stays green through the change.
- `ReportValidator` enforces the **same rules with the same codes** against the generated model;
  `ReportValidatorTest` green.
- `ReportMarshaller` marshals the generated `Report`; the 7 goldens are regenerated, **match**, and **pass the
  XSD gate**.
- Full non-DB suite green; four clean commits 4a–4d.

## 11. What this step does NOT do

No new report types beyond what exists today (Step 5 expands toward the 17 codes), no DPMSR end-to-end builder
convenience API (Step 6), no docs refresh (Step 7), no engine *behaviour* change — the validation rules,
packaging, and lookups keep their current semantics; only the underlying model changes.

---

## Outcome — per sub-step

### 4a — Remove the `Report` catch-all ✅ (2026-06-04)

**Done. No shim needed — the `.xjb` rename worked: `Report` now has fully typed getters.**

**What was built:** a scoped `<jaxb:bindings>` in `goaml-bindings.xjb` (and an `xmlns:xs` + `schemaLocation`
pointing at `../resources/xsd/goaml/5.0.2/goAMLSchema.xsd`) that renames the `<xs:choice>` branch-1 `activity`
element → property **`reportActivity`**. `./gradlew xjc` now regenerates `Report` with typed getters
(`getRentityId():int`, `getReportCode():ReportType`, `getSubmissionDate():OffsetDateTime`,
`getCurrencyCodeLocal():CurrencyType`, `getReportingPerson():TPersonRegistrationInReport`,
`getLocation():TAddress`, `getReason()/getAction():String`, `getTransaction():List<Report.Transaction>`,
`getReportActivity():ActivityType`, `getReportIndicators()`, …) — **`getContent()` / `List<JAXBElement<?>>` is
gone**. `GeneratedModelRoundTripTest` updated to typed getters; both real samples + `GeneratedModelTest` green.

**Two findings that shaped the final binding (both verified empirically):**
1. **xjc only accepts the rename on branch-1's (nested) `activity`.** Renaming branch-2's direct-child
   `activity` instead fails codegen: *"Element activity shows up in more than one properties"* (the XML element
   name collides). Renaming the nested one is the working combination.
2. **JAXB greedily routes `<activity>` to branch 1.** A DPMSR `<activity>` with **no** preceding
   `<transaction>` unmarshals into branch-1's property, not branch-2's — even though branch 1's XSD requires
   ≥1 transaction (the generated unmarshaller doesn't enforce that). So branch-1's property is the slot
   populated for **both** report shapes. We therefore named it `reportActivity` (the real accessor) and left
   branch-2's `activity` (`getActivity()`) as a **vestigial, never-populated** choice member.

**Consequence for 4b–4d (important):** the engine's canonical activity accessor is **`getReportActivity()` /
`setReportActivity(...)`**, NOT `getActivity()`. Builders set `reportActivity`; the validator reads it.
Marshalling `reportActivity` with no transactions emits a bare `<activity>` — XSD-valid via branch 2 (the
round-trip XSD re-validation confirms it). The authoritative XSD was **not** weakened (the choice/XOR stays
intact; we only renamed a Java property).

**Verify:** `./gradlew test --tests '*GeneratedModelRoundTripTest' --tests '*GeneratedModelTest'` → green.

### 4b — Re-point `marshal/` + `build/`

_(Pending — next.)_

### 4c — Re-point `validation/` + `jurisdiction/`

_(Pending.)_

### 4d — Delete hand-modeled `domain/*` + retire its tests + regenerate goldens

_(Pending.)_
