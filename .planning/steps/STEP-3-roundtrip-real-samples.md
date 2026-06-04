# Step 3 — Round-trip the real DPMSR samples through the generated model

> **Status: ✅ DONE (2026-06-04) — implemented, both real samples round-trip green. See "Outcome".**
> Part of [../plans/xsd-first-foundation.md](../plans/xsd-first-foundation.md) (step X.3).
> Predecessors: Step 1 ✅ (XSD validation gate) · Step 2 ✅ (xjc codegen → 46 generated classes).

---

## 1. What this step is, and why

Step 2 *generated* a JAXB model from the authoritative `goAMLSchema.xsd` (5.0.2) and proved it **compiles**.
That is necessary but not sufficient — a model that compiles can still fail to faithfully **read and write
real FIU reports** (wrong element order, a date that won't parse, a `JAXBElement`-wrapped field that drops on
re-marshal, a quirky element name the schema uses that the codegen mapped differently).

**Step 3 is the proof.** We take the **two real DPMSR reports** downloaded from the live UAE goAML portal and
run them through a full round-trip against the generated model:

```
real XML  ──unmarshal──▶  generated Report  ──marshal──▶  XML′  ──XSD-validate──▶  isValid()
                                  │
                                  └──▶ assert key business values survived (rentity, item, entity, currency)
```

If both samples survive that loop **and** the re-marshalled XML still passes the Step-1 XSD gate, we have
hard evidence the generated model is a faithful, conformant replacement for the hand-modeled `domain/*`.
**Only then** is it safe to re-point the engine onto it (Step 4) and delete the hand-modeled POJOs.

This step writes **no production code and changes no engine code** — it is a **test-only** verification.

## 2. Concept primer

- **Round-trip = the canonical JAXB correctness test.** Unmarshal (XML → objects) then marshal (objects →
  XML) and check you got an equivalent document back. It exercises *both* directions of every binding at once.
- **Two levels of "equivalent":**
  1. **Structural validity** — does the re-marshalled XML still satisfy `goAMLSchema.xsd`? (Re-uses the
     Step-1 `XsdSchemaValidator` — closing the loop between our two earlier steps.)
  2. **Value fidelity** — did the *meaningful* data survive the trip? We don't require byte-identical XML
     (JAXB legitimately reformats whitespace, attribute order, namespace decls); we assert a handful of
     **business-critical values** read back correctly.
- **Why not assert byte-identical XML?** Marshalling is not guaranteed to reproduce the exact input bytes
  (indentation, optional xsi attributes, element-vs-empty-element). Demanding that produces brittle,
  false-failing tests. Structural-validity + key-value assertions is the honest, robust bar.
- **`JAXBElement` wrappers (the Step-2 nuance):** xjc wraps some optional/nillable header elements (e.g.
  `report_code`, `submission_date`) as `JAXBElement<…>` rather than plain fields. The round-trip will tell us
  exactly which getters return a wrapper vs a plain value — information Step 4's builders need. This is a
  feature of the test, not a problem: it surfaces the real shape of the model.

## 3. The two real samples (what we're round-tripping)

Vendored at `src/test/resources/samples/` (the same files the Step-1 gate already validates as *input*):

| File | Report | Key facts we'll assert survive the round-trip |
|------|--------|-----------------------------------------------|
| `TR.2079.200000309.xml` | DPMSR | `rentity_id` **3177**, `report_code` **DPMSR**, gold item, reporting entity name |
| `TR.2079.200000310.xml`  | DPMSR | second real DPMSR (companion sample) |

> The exact assertion values (currency `AED`, item `GOLD` / 15 KG, entity `Example Jewellery LLC`, etc.) are
> read from the actual sample XML at implementation time and pinned in the test — I'll confirm each against
> the file rather than hard-coding from memory. The *intent* is fixed: prove the headline business data
> (who reported, what report type, what goods, what currency/quantity) reads back correctly.

⚠️ **PII note (unchanged):** these samples contain real names from the live portal. They remain local-only
and must be anonymized before any push/share (already tracked in STATE.md). This step does not change that.

## 4. What I'll build / change

**4a. (test helper) marshal/unmarshal against the generated model.** Either a tiny test-only helper or inline
in the test:
```java
JAXBContext ctx = JAXBContext.newInstance(com.vyttah.goaml.domain.generated.ObjectFactory.class);
Report report = (Report) ctx.createUnmarshaller().unmarshal(new ByteArrayInputStream(realXmlBytes));
// ...assert key values on `report`...
var out = new ByteArrayOutputStream();
ctx.createMarshaller().marshal(report, out);          // (or marshal the JAXBElement root if required)
byte[] remarshalled = out.toByteArray();
```
> Whether the marshaller needs the raw `Report` or `objectFactory.createReport(report)` (a `JAXBElement` root)
> depends on how xjc bound the root element — the round-trip will make that explicit and the helper will do
> whichever the generated model requires. The existing `engine/marshal/ReportMarshaller` is the *pattern*
> reference (it builds the `JAXBContext` once), but it targets the **hand-modeled** domain, so Step 3 uses a
> generated-model context. We do **not** modify `ReportMarshaller` here (that's Step 4).

**4b. NEW test `src/test/java/com/vyttah/goaml/domain/generated/GeneratedModelRoundTripTest.java`** — a
parameterized test over the two samples that asserts, for each:
1. **Unmarshal succeeds** → a non-null generated `Report`.
2. **Key business values are present and correct** (rentity_id, report_code/type = DPMSR, currency, the gold
   `item` quantity/type, the reporting-entity name) — value fidelity.
3. **Re-marshal succeeds** → non-empty XML.
4. **Re-marshalled XML passes the Step-1 XSD gate** — `new XsdSchemaValidator().validate(remarshalled).isValid()`
   is `true` (structural validity, closing the loop with Step 1).

**4c. (nothing else.)** No engine changes, no `domain/*` deletion, no `build.gradle` change, no new
production class. Pure test addition.

## 5. Files touched

| File | Change |
|------|--------|
| `src/test/java/com/vyttah/goaml/domain/generated/GeneratedModelRoundTripTest.java` | **new** — the round-trip + value-fidelity test |
| *(optional)* a tiny test-only generated-model marshaller helper | **new**, only if it keeps the test readable |
| *(unchanged)* `engine/*`, hand-modeled `domain/*`, `build.gradle`, `application.yml`, the samples, the XSD | untouched |

## 6. How it's verified

```bash
./gradlew test --tests '*GeneratedModelRoundTripTest'
```
**Expected:** both samples unmarshal, re-marshal, and the re-marshalled XML validates against
`goAMLSchema.xsd`; key-value assertions pass. **No Docker needed** (no DB). The 6 Testcontainers DB tests
remain unrelated and still require Docker.

## 7. Risks / caveats (honest)

1. **`JAXBElement`-wrapped getters.** Some header fields come back as `JAXBElement<ReportType>` rather than
   `ReportType`. The test will unwrap them (`.getValue()`); if the *marshal* direction needs the root wrapped
   via `ObjectFactory.createReport(...)`, the helper does that. **Outcome of this risk = documented knowledge
   for Step 4 builders**, not a blocker.
2. **Date round-trip via `GoamlDateTimeAdapter`.** The samples' date/datetime fields must parse with the
   adapter's pattern (`yyyy-MM-dd'T'HH:mm:ss`) and marshal back to a schema-valid lexical form. If a real
   sample uses a date *format the adapter doesn't expect* (e.g. an offset, or date-only), this surfaces here —
   exactly where we want to catch it. Fix (if needed) is a small, isolated adapter tweak, re-verified by the
   same test.
3. **Re-marshalled XML failing the XSD gate.** Possible if the generated model drops or reorders something on
   marshal. This is the *whole point* of the test — a real failure here would be a genuine Step-2 defect we'd
   fix before proceeding to Step 4 (better now than after re-pointing the engine).
4. **Whitespace / formatting differences are NOT failures.** We deliberately assert structural validity +
   key values, not byte equality, so benign reformatting doesn't false-fail. (Documented so a future reader
   doesn't "tighten" this into a brittle diff.)
5. **PII in fixtures.** The assertions reference real values from the samples; the test file therefore embeds
   a little real data. Acceptable locally; part of the same pre-push anonymization task already tracked.

## 8. Done criteria

- `./gradlew test --tests '*GeneratedModelRoundTripTest'` is **green**: both real DPMSR samples unmarshal into
  the generated `Report`, re-marshal, and the re-marshalled XML **passes the Step-1 XSD gate**.
- Key business values (rentity_id, DPMSR report code, currency, gold item, reporting-entity name) are asserted
  and survive the round-trip.
- The `JAXBElement`-wrapping reality of the generated model is confirmed and noted for Step 4.
- No engine/`domain`/`build.gradle` changes; full non-DB suite still green.

## 9. What this step does NOT do

No engine re-point, no marshaller/validator/builder changes (Step 4), no deletion of the hand-modeled
`domain/*` (Step 4), no golden regeneration (Step 5), no DPMSR builder (Step 6). It **only proves** the
generated model can faithfully read and write the FIU's real reports — the green light for Step 4.

---

## Outcome (2026-06-04) — ✅ done, green

**Built:** one test-only file — `src/test/java/com/vyttah/goaml/domain/generated/GeneratedModelRoundTripTest.java`
(no helper class needed; the marshal/unmarshal is small enough to live in the test). No production, engine,
`domain/*`, or `build.gradle` change.

**Result:** `./gradlew test --tests '*GeneratedModelRoundTripTest'` → **both** real DPMSR samples
(`TR.2079.200000309.xml`, `…310.xml`) **PASS**. For each: unmarshal → generated `Report`, key values verified,
re-marshal → XML, and the re-marshalled XML **re-validates clean against `goAMLSchema.xsd`** via the Step-1
`XsdSchemaValidator`. Re-ran with `*generated*` + `*validation*` together — all green.

**What the round-trip proved:**
- **Value fidelity** holds: `rentity_id` = 3177, `report_code` = `ReportType.DPMSR`, `currency_code_local` =
  `CurrencyType.AED`, gold item `GOLD` / size `15` / uom `KG` / currency `AED`, reporting entity
  `Example Jewellery LLC` — all read back correctly through the generated model.
- **Structural validity** holds both directions: the model reads the FIU XML *and* writes XML that still
  satisfies the authoritative schema — Step 1's gate now validates Step 2's *output*, closing the loop.
- **The `OffsetDateTime` adapter round-trips:** the samples' `submission_date` / `birthdate` /
  `registration_date` (`2026-06-02T12:00:00` form) unmarshal via `GoamlDateTimeAdapter` and re-marshal to a
  schema-valid lexical form (the re-validation would have failed otherwise). Confirms the Step-2 `sql_date →
  OffsetDateTime` binding works on real data.

**Key structural finding for Step 4 (important):** the generated `Report` root does **not** expose typed
header getters (`getRentityId()`, `getReportCode()`, …). Instead the entire report body is a single
**catch-all `List<JAXBElement<?>>`** — `Report.getContent()` — annotated with `@XmlElementRefs`. xjc generated
this because the schema **reuses the name "Activity"** for two different declarations (goAMLSchema.xsd lines
308 & 310), so it couldn't emit distinct typed properties. Consequences:
- **Reading** a header field = find the `JAXBElement` whose `getName().getLocalPart()` matches (e.g.
  `"rentity_id"`, `"report_code"`, `"currency_code_local"`, `"activity"`) and take `.getValue()`. The test's
  `content(report, localName)` helper does exactly this.
- **Order is preserved** automatically — `getContent()` keeps the unmarshal order, so a re-marshal reproduces
  the schema's element sequence without us managing `propOrder`.
- **Step-4 builders** must therefore populate `report.getContent()` with `ObjectFactory.createReportRentityId(…)`,
  `createReportReportCode(…)`, `createReportActivity(…)`, etc. (the `ObjectFactory` has a `createReportXxx`
  `JAXBElement` factory per header element) — rather than calling typed setters. This is the single most
  important thing Step 4 must account for. (Optional future cleanup: a `<jaxb:property>` rename on one of the
  two "Activity" declarations would let xjc emit typed getters — deferred; not needed for correctness.)
- `report_code` deserializes straight to the **`ReportType`** enum and `currency_code_local` /
  `item.currencyCode` to the **`CurrencyType`** enum — type-safe, as intended.
- `Report` is `@XmlRootElement(name = "report")`, so marshal/unmarshal use `Report` directly (no
  `ObjectFactory.createReport(...)` wrapping for the root).

**No deviations** from the approved spec. PII caveat unchanged (real values asserted in-test; anonymize before
push — already tracked in STATE.md).

**Verify:** `./gradlew test --tests '*GeneratedModelRoundTripTest'` (no Docker needed).
