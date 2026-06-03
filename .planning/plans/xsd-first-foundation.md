# Plan / Decision — XSD-First Foundation (build over the latest goAML XSD)

> **Status:** DECIDED + **UNBLOCKED (2026-06-03)** — the authoritative XSDs were obtained from the live
> UAE goAML portal and vendored into the repo. Ready to execute.
> **Direction:** stop hand-modeling the goAML schema; build the domain + validation over the
> authoritative **goAML 5.0.2** schema.
> **Impact:** reworks the already-built `domain/` (Phase 3) and the `engine/` built on it (Phases 4–5).

> ## ✅ Files in hand (vendored 2026-06-03)
> - **`src/main/resources/xsd/goaml/5.0.2/goAMLSchema.xsd`** — **THE authoritative schema** (the one to
>   generate from + validate against). `goaml:schemaVersion="5.0.2"`, no targetNamespace (report XML is
>   **no-namespace**), full header + activity + `goods_services/item` + `report_party/entity/director_id`.
> - `src/main/resources/xsd/goaml/5.0.2/goAMLReportDataSchema.xsd` — a no-header internal/export schema;
>   **not** used for submission. Kept for reference only.
> - **`src/test/resources/samples/TR.2079.200000309.xml` & `…310.xml`** — two **real DPMSR reports** from
>   the live portal → use as **golden/validation anchors** (must validate against the XSD and round-trip).
>
> ## ✅ Reality is simpler than feared
> - **No XSD 1.1 `<assert>` / `openContent` / `alternative` / `override`** anywhere. The only 1.1-ish
>   token is a single `vc:minVersion="1.1"` attribute + `goaml:supportedIn` annotation markers (142×),
>   which **xjc and JAXP ignore.** → **Standard xjc codegen + standard JDK JAXP validation work.
>   No Saxon / Xerces-EE needed.** (Gotcha #2 below is largely moot; verify the lone `vc:` attr doesn't
>   trip the 1.0 SchemaFactory — strip it if it does.)
> - Schema size: **39 complexTypes + 45 simpleTypes** — very manageable for xjc.
> - **17 report types** in `report_type`: AIF, AIFT, CIR, CNMRA, DPMSR, ECDD, ECDDT, HRC, HRCA, IRR, ITR,
>   PNMRA, PSTR, REAR, SAR, SIR, STR (the repo currently models only 7).
> - Dates use a `sql_date` type; real samples show `yyyy-MM-dd'T'HH:mm:ss` (e.g. `2026-06-02T12:00:00`) —
>   our existing `GoamlDateTimeAdapter` already matches; bind `sql_date` → `OffsetDateTime` via `.xjb`.

---

## 1. What "latest XSD" is (research findings, June 2026)

- The **latest published goAML schema is 5.0.x** (5.0.1 / 5.0.2 / 5.0.3 lineage; introduced ~2021–2022).
  The current repo targets the older **v4.0** and hand-models a representative subset.
- goAML **5.0 uses XSD 1.1** and adds **`<xs:assert>` rules** ("ASSERT-restrictions") that encode many
  business constraints directly in the schema — designed to improve report quality at validation time.
- The schema is **not freely downloadable from the open web.** It ships through login-gated portals:
  - **UNODC goAML portal** (`unite.un.org/goaml`) — registered FIUs/entities.
  - **The specific FIU's goAML instance.** For us that's the **UAE FIU portal**
    (`services.uaefiu.gov.ae`), where a registered reporting entity downloads "the XML goAML schema
    (XSD) with the requirements against which your XML file is assessed."
- **The UAE FIU instance is authoritative for this product**, and it defines **more report types than we
  currently model.** UAE goAML guides reference, beyond STR/SAR/AIF/AIFT/ECDD/ECDDT/DPMSR:
  **REAR** (Real Estate Activity Report), **PNMR**, and **FFR** (Funds-Freeze/related). The exact set +
  fields come from the UAE XSD itself.

> ⚠️ **Open question to confirm:** which goAML version is the **UAE FIU production** instance currently
> on (4.x or 5.x)? "Latest" must mean *the latest the UAE FIU actually accepts*. Export the XSD from the
> UAE FIU portal and read its version header — that file is the single source of truth.

Sources: [goAML 5.0 FAQ (CRF Lux)](https://faq.goaml.lu/sujets-it/goaml-5-0/) ·
[goAML Schema v5.0.2 description (PDF)](https://aml.iq/wp-content/uploads/2023/03/goAMLSchema-v5.0.2.pdf) ·
[UAE FIU system guides](https://www.uaefiu.gov.ae/en/more/knowledge-centre/system-guides/) ·
[UAE FIU eServices portal](https://services.uaefiu.gov.ae/) ·
[UNODC goAML](https://www.unodc.org/unodc/en/global-it-products/goaml.html).

---

## 2. The decision

1. **Adopt the authoritative UAE FIU goAML XSD (latest — target 5.0.x) as the source of truth** for the
   report data model and structural validation. Drop the hand-modeled v4.0 subset.
2. **Generate the JAXB domain types from the XSD** (xjc) instead of hand-writing POJOs. The schema, not
   our code, defines element names, ordering, types, and cardinality.
3. **Add an authoritative XSD validation gate** — marshalled XML is validated against the real XSD before
   submission. Because it's **XSD 1.1**, use an **XSD-1.1-capable validator** (see §4).
4. **Keep the business-rule validator** (`ReportValidator`) as a *complementary* layer for rules the XSD
   can't express or that we want friendlier messages for — but let the XSD own everything it can.
5. **Vendor the XSD into the repo** once exported (e.g. `src/main/resources/xsd/goaml/<version>/`), so the
   build is reproducible and self-contained.

---

## 3. Why this matters / why now

- **Correctness:** the FIU rejects anything that doesn't match *their* XSD exactly. Hand-modeling a
  subset guarantees drift; generating from the XSD guarantees conformance.
- **Coverage:** the XSD brings the full type tree + the UAE report types we don't model yet
  (REAR/PNMR/FFR) for free.
- **Maintainability:** when the FIU bumps the schema, we re-run codegen + swap the XSD, instead of
  hand-editing dozens of POJOs.
- **Do it before building more on the old types** — every phase after 5 (b2b, persistence, web, mcp)
  consumes the domain types. The longer we wait, the more rework.

---

## 4. The technical gotchas (must be handled)

1. **xjc is XSD 1.0.** The JAXB schema compiler understands XSD 1.0 constructs. It will generate the
   type structure from a 1.1 schema fine, but it **ignores `<xs:assert>` / `<xs:openContent>`** — those
   1.1 rules won't be enforced by the generated classes.
2. **Default Java validation is XSD 1.0.** `SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI)` is 1.0 and
   will **fail or ignore** 1.1 asserts. To enforce the assert rules you need an **XSD-1.1 validator**:
   **Apache Xerces in XSD 1.1 mode** (`SchemaFactory.newInstance("http://www.w3.org/XML/XMLSchema/v1.1")`)
   or **Saxon-EE**. → The `XsdSchemaValidator` gate must use one of these, not the JDK default.
3. **Generated package / naming** will differ from the current hand-modeled `com.vyttah.goaml.domain.*`.
   Decide the generated package (e.g. `com.vyttah.goaml.domain.generated`) and a binding customization
   (`.xjb`) for naming/adapters (e.g. keep the `OffsetDateTime` date adapter via a JAXB binding).
4. **Element-ordering & the snake_case/`tph_` quirks** become the XSD's job — the hand-noted traps in
   [docs/04](../../docs/04-domain-model.md) are encoded in the schema, so they stop being our problem
   (but verify the generated bindings match the golden expectations).
5. **XSD 1.1 + Gradle plugin:** pick a JAXB/xjc Gradle plugin that can drive xjc against the schema; pin
   its version; confirm it tolerates the 1.1 constructs (skipping asserts with at most a warning).

---

## 5. Migration plan (what changes, in order)

This supersedes the "swap to XSD-generated JAXB later" open item. Each step is testable.

| Step | Work | Done criteria |
|------|------|---------------|
| X.1 | ✅ **DONE** — authoritative `goAMLSchema.xsd` (5.0.2) + 2 real DPMSR samples vendored into `src/main/resources/xsd/goaml/5.0.2/` and `src/test/resources/samples/`. | done |
| X.2 | **Wire xjc codegen** into Gradle (JAXB plugin + `.xjb` bindings: target package, `OffsetDateTime` adapter, any renames). | `./gradlew build` generates JAXB types from the XSD; compiles |
| X.3 | **Build the XSD-1.1 validation gate** (`engine/validation/XsdSchemaValidator`) using Xerces 1.1 (or Saxon). Validate the 7 (and new) report types' marshalled XML against the real XSD. | Each report type validates against the official XSD; assert rules enforced |
| X.4 | **Re-point the engine** — builders, marshaller, `ReportValidator`, `ReportHeader`, samples — to the generated types. Keep the business-rule layer; delete/retire the hand-modeled `domain/*` POJOs. | Engine compiles against generated types; business-rule tests pass |
| X.5 | **Regenerate goldens** against the 5.0.x schema (`-Dgoaml.golden.regenerate=true`); review the diff carefully (version bump changes the XML). | Golden tests green vs new schema; diffs reviewed |
| X.6 | **Expand report-type coverage — phased.** **First: `DPMSR`** (precious-metals dealers — matches the real samples), then `STR`/`SAR` + sanctions `PNMRA`/`CNMRA`; **next phase: the remaining of the 17** codes. Each with builders + validation + goldens. | DPMSR builds, validates (XSD + rules), and round-trips against the real samples; others follow |
| X.7 | **Update docs** — [docs/01 §8](../../docs/01-business-context.md), [docs/04](../../docs/04-domain-model.md), [docs/05 §3](../../docs/05-engine.md), [docs/09 open items](../../docs/09-build-order-and-roadmap.md) — to reflect 5.0.x XSD-generated reality. | Docs match the new model |

---

## 6. Honest impact assessment

- **Phase 3 (`domain/`)** — largely **replaced** by generated types. The hand-modeled POJOs were a good
  stopgap and validated our understanding, but they go away.
- **Phases 4–5 (`engine/`)** — **reworked**: builders/marshaller/validator re-point to generated types;
  the *business rules* (DPMS threshold, bi-party shape, lookups, conditional-mandatory) are mostly
  portable but their field references change; goldens regenerate for 5.0.x.
- **The investment isn't wasted** — the multi-tenancy/security/persistence foundation (Phases 1–2) is
  untouched, and the engine's *rule logic* and test patterns carry over.
- **Net:** a foundational reset of the data layer that pays for itself before we build b2b/web/mcp on it.

---

## 7. The one blocking dependency

**The authoritative UAE FIU goAML XSD must be exported from the FIU portal and dropped into the repo
(step X.1).** I cannot fetch it from the open internet (it's login-gated). Everything else proceeds once
that file is in `src/main/resources/xsd/goaml/<version>/`. Vyttah is a registered reporting entity, so
this is an export action, not a blocker on capability.

**If the XSD can't be obtained immediately:** we can scaffold X.2–X.3 against a *generic* UNODC goAML
5.0.x reference XSD (from the UNODC/Lux portal) to build the codegen + validation pipeline, then swap in
the UAE-authoritative file when available — but the UAE file is what we ship against.
