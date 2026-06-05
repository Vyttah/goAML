# Step 2 — Wire xjc codegen (generate JAXB types from the real schema)

> **Status: ✅ DONE (2026-06-04) — implemented, generates + compiles, checkpoint test green. See "Outcome".**
> Part of [../plans/xsd-first-foundation.md](../plans/xsd-first-foundation.md) (step X.2).

---

## 1. What this step is, and why

Stop hand-writing the goAML data model — **generate it from the authoritative `goAMLSchema.xsd` (5.0.2)**.
We wire **xjc** (the JAXB schema compiler) into the Gradle build via the
**`com.github.bjornvester.xjc`** plugin, so every build produces Java classes that exactly mirror the FIU
schema (element names, ordering, types, cardinality) — into `build/` (not committed; regenerated each build,
per your decision). After this step we *have* a conformant model; Step 3 proves it round-trips your real
samples, Step 4 re-points the engine onto it and deletes the hand-modeled `domain/*`.

## 2. Concept primer

- **xjc** reads an XSD and emits annotated JAXB POJOs (`@XmlType(propOrder=…)`, `@XmlElement`, etc.) plus an
  `ObjectFactory`. JAXB then marshals/unmarshals XML against those classes.
- **Why generate, not hand-model:** the schema becomes the single source of truth — no drift, FIU bumps =
  re-run codegen. (The hand-modeled `domain/*` was a good stopgap; it goes away in Step 4.)
- **Generated into `build/`, not committed:** the plugin emits to `build/generated/sources/xjc/...` and adds
  it to the main source set automatically; nothing to review in PRs, can't drift from the XSD.
- **Patterns vs codegen:** JAXB doesn't enforce XSD `pattern`/length facets at runtime — that's the **XSD
  gate's** job (Step 1). So the generated fields are plain typed fields; structural conformance is still
  checked by `XsdSchemaValidator`.

## 3. What I'll build / change

**3a. `build.gradle` — add the plugin + configure xjc** (proposed; exact option names finalized at impl):
```groovy
plugins {
    // ...existing...
    id 'com.github.bjornvester.xjc' version '<pinned recent version>'
}

xjc {
    // Generate from the authoritative schema ONLY (see risk #1 — the two XSDs both declare <report>).
    xsdDir.set(layout.projectDirectory.dir("src/main/resources/xsd/goaml/5.0.2"))
    includes.set(["goAMLSchema.xsd"])
    bindingFiles.from("src/main/jaxb/goaml-bindings.xjb")
    defaultPackage.set("com.vyttah.goaml.domain.generated")
    extension.set(true)        // enable xjc vendor bindings (needed for the date adapter binding)
    useJakarta.set(true)       // Jakarta JAXB — matches the project's jaxb-runtime 4.0.5
}
```

**3b. `src/main/jaxb/goaml-bindings.xjb`** (new, committed — it's source config) — bind the schema's
`sql_date` type (an `xs:dateTime` restriction) to **`java.time.OffsetDateTime`** via the **existing**
`GoamlDateTimeAdapter`, so generated date fields use `OffsetDateTime` (not `XMLGregorianCalendar`):
```xml
<jaxb:bindings version="3.0"
    xmlns:jaxb="https://jakarta.ee/xml/ns/jaxb"
    xmlns:xjc="http://java.sun.com/xml/ns/jaxb/xjc"
    xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <jaxb:globalBindings>
    <xjc:javaType name="java.time.OffsetDateTime" xmlType="sql_date"
                  adapter="com.vyttah.goaml.domain.adapter.GoamlDateTimeAdapter"/>
  </jaxb:globalBindings>
</jaxb:bindings>
```
> The exact JAXB/xjc binding namespaces + the global-binding form are confirmed against Jakarta JAXB 4 at
> implementation; the **intent** is fixed: `sql_date → OffsetDateTime` via `GoamlDateTimeAdapter`.
> Note: `GoamlDateTimeAdapter` (in `domain/adapter/`) is **reused** — it's a generic `XmlAdapter`, so it
> survives the Step 4 deletion of the hand-modeled POJOs.

**3c. (optional) a tiny presence smoke test** — assert the generated `com.vyttah.goaml.domain.generated.Report`
+ `ObjectFactory` exist and instantiate, so Step 2 has its own green checkpoint. (The real proof — round-trip
of the real samples — is Step 3.)

## 4. What the generated model will look like

In `com.vyttah.goaml.domain.generated` (under `build/`):
- A class per XSD complexType (~39): `Report`, `Transaction`, `Activity`, `TPerson`, `TEntity`,
  `GoodsServices` + its `item` (`TTransItem`), `ReportParty`, the `director_id` type, addresses, phones, etc.
  — with `@XmlType(propOrder=…)` matching the schema order, and the `tph_`/`transactionnumber` element-name
  quirks encoded by the schema (no longer our problem to hand-maintain).
- **Java enums** for the enumerated simpleTypes: `report_type` (17 values incl. DPMSR), `submission_type`,
  `funds_type`, `account_type`, etc. (type-safe, replacing the hand-modeled `domain/enums/*`).
- An `ObjectFactory` (and `package-info.java`).

## 5. Files touched

| File | Change |
|------|--------|
| `build.gradle` | **modified** — add `com.github.bjornvester.xjc` plugin + `xjc { }` config |
| `src/main/jaxb/goaml-bindings.xjb` | **new** (committed) — `sql_date → OffsetDateTime` adapter binding |
| `build/generated/sources/xjc/**` | **generated, NOT committed** — the JAXB classes |
| *(optional)* a small generated-model presence test | **new** |
| *(unchanged)* `domain/*`, engine, `application.yml` | hand-modeled model + engine stay as-is (coexist) |

## 6. How it's verified

```bash
./gradlew clean build
```
**Expected:** the `xjc` task runs, generates classes under `build/generated/sources/xjc/`, and the project
**compiles** (generated model + hand-modeled model coexist without conflict — different packages). If the
optional presence test is added: `./gradlew test --tests '*GeneratedModel*'` green. (DB tests still need
Docker, unrelated.)

## 7. Risks / caveats (honest)

1. **Two XSDs, both declare `<report>` (no namespace).** If xjc processes both `goAMLSchema.xsd` and
   `goAMLReportDataSchema.xsd`, classes clash. → We **restrict xjc to `goAMLSchema.xsd` only** (the
   `includes` setting). This is the most important config detail.
2. **`sql_date` binding needs xjc extension mode** (`extension = true`) because `xjc:javaType` is a vendor
   binding. If the binding form fights the plugin, fallback is a `@XmlJavaTypeAdapter` applied post-gen or a
   simpler global `javaType` — the date-handling outcome is the same either way.
3. **Plugin / toolchain compatibility:** pin a recent `com.github.bjornvester.xjc` that supports Gradle
   8.10.2 + Java 21 + Jakarta JAXB 4. First build downloads the plugin (network).
4. **No-namespace → explicit package:** since the schema has no `targetNamespace`, xjc would invent a package
   name; we pin it to `com.vyttah.goaml.domain.generated`.
5. **xjc may warn on the pattern facets** (the email/name regexes) — warnings are fine; xjc generates plain
   `String` fields and leaves pattern enforcement to the XSD gate.
6. **Coexistence:** generated classes sit beside the hand-modeled `domain/*` and are **not referenced yet**,
   so nothing breaks. They get wired in (and the hand-modeled ones deleted) in Step 4.

## 8. Done criteria

- `./gradlew clean build` generates JAXB types from `goAMLSchema.xsd` into `build/` and compiles.
- Generated package is `com.vyttah.goaml.domain.generated`; date fields are `OffsetDateTime`; `report_type`
  etc. are enums.
- `.xjb` + `build.gradle` committed; generated sources **not** committed. Full (non-DB) suite still green.

## 9. What this step does NOT do

No engine changes, no marshaller/validator/builder re-point (Step 4), no round-trip test of the real samples
through the generated model (Step 3), no deletion of the hand-modeled `domain/*` (Step 4). It only stands up
codegen so the conformant model exists.

---

## Outcome (2026-06-04) — ✅ done, green

**Built:** `build.gradle` (xjc plugin `1.9.0`, `useJakarta`, `-extension`, package
`com.vyttah.goaml.domain.generated`) + `src/main/jaxb/goaml-bindings.xjb` (`sql_date → OffsetDateTime`).

**Result:** `./gradlew clean compileJava` generates **46 JAXB classes** into
`build/generated/sources/xjc/...` and compiles. `GeneratedModelTest` (+ the Step 1 XSD test) pass.
Confirmed: dates are **`OffsetDateTime`** (adapter wired into 19 classes), **`ReportType` enum** carries
the UAE codes (DPMSR/STR/SAR/REAR/PNMRA/CNMRA/…), goods item is `TTransItem`, parties are
`TPerson`/`TEntity`/etc. Generated sources are **not committed** (build/).

**Two deviations from the spec (resolved):**
1. The plugin has **no per-file include** — so instead of an `includes` setting, I **removed the
   reference-only `goAMLReportDataSchema.xsd` from `src/main/resources/xsd/goaml/5.0.2/`** (kept in
   `assets/`) so only `goAMLSchema.xsd` is in `xsdDir`. Avoids the two-`<report>` clash.
2. Pinned **`xjcVersion = "4.0.5"`** — the plugin defaults to xjc 4.0.6, which mismatched the
   Spring-BOM-managed codemodel 4.0.5 → `NoSuchMethodError JDocComment.appendXML` during enum generation
   (jaxb-ri #1854). 4.0.5 matches our `jaxb-runtime`.

**Nuance for Steps 3–4:** xjc wraps some optional/nillable header elements (e.g. `report_code`,
`submission_date`) as **`JAXBElement<…>`** rather than plain fields — so the Step 4 builders will set those
via `ObjectFactory.createXxx(...)`. Step 3's round-trip of the real samples will confirm the full model.
