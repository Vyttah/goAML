# Step 1 — XSD validation gate + prove the real samples

> **Status: ✅ DONE (2026-06-04) — implemented, tests green. See "Outcome" at the bottom.**
> Part of [../plans/xsd-first-foundation.md](../plans/xsd-first-foundation.md) (the safe, no-codegen first
> checkpoint).

---

## 1. What this step is, and why it's first

Before we generate any Java from the schema or touch the existing engine, we prove one thing:
**the authoritative `goAMLSchema.xsd` (5.0.2) and your two real DPMSR sample reports actually agree.**

We do that by building a small **XSD validation gate** — a component that checks an XML document against
the official schema — and pointing it at your two real samples. If they validate clean, we have a
**ground-truth anchor**: from here on, anything our engine produces must also pass this same gate.

It's first because it's **zero-risk**: no code generation, no changes to the hand-modeled `domain/` or the
engine. Just a new class + a test. If it's green, the foundation is sound and we proceed with confidence.

## 2. Concept primer (so the choice is clear)

- **XSD validation** = checking that an XML file obeys a schema (right elements, order, types, cardinality).
  Java does this with built-in **JAXP**: `SchemaFactory` compiles the `.xsd` into a `Schema`; a `Validator`
  then checks an XML `Source` and reports any violations.
- **Why standard JDK JAXP is enough here:** goAML 5.0 *can* use XSD 1.1 `<assert>` rules (which the JDK
  validator can't enforce), **but the schema you provided has none** (we verified: zero
  `assert`/`openContent`/`alternative`). So the default JDK `SchemaFactory` (XSD 1.0) validates it fully —
  **no Saxon, no Xerces-EE.**
- **Self-contained schema:** `goAMLSchema.xsd` has **no `xs:include`/`xs:import`** (verified), so we load
  exactly one file — no resolver or catalog needed.
- **No XML namespace:** the report XML and the schema are no-namespace, so validation is a straight match.

## 3. What I'll build

**3a. `engine/validation/XsdSchemaValidator.java`** (new, a Spring `@Component`)
- On construction, **compile the schema once** from the classpath resource
  `xsd/goaml/5.0.2/goAMLSchema.xsd` (compiling is expensive; a `Schema` is thread-safe and reused).
- Method **`ValidationResult validate(byte[] reportXml)`** (reuses the existing
  `engine/validation/ValidationResult` / `ValidationMessage` / `Severity` model for consistency):
  - Create a `Validator` from the cached `Schema` (a `Validator` is *not* thread-safe → one per call).
  - Register an `ErrorHandler` that **collects** errors/fatals as `Severity.ERROR` messages (code `XSD`,
    message = the SAX message incl. line/column) and warnings as `Severity.WARNING` — instead of throwing
    on the first error, so we get the *full* list.
  - Validate the XML `Source`; return the populated `ValidationResult` (`isValid()` = no ERRORs).
- A small `xsdDeclaration()`/version accessor for diagnostics is optional; not required.

> Design note: this is the structural gate. The existing `ReportValidator` (business rules) stays separate;
> later (Step 6) a report runs through **both** — XSD gate *and* business rules.

**3b. `engine/validation/GoamlXsdValidationTest.java`** (new test)
- Load both real samples from `src/test/resources/samples/` (`TR.2079.200000309.xml`, `…310.xml`).
- Run each through `XsdSchemaValidator.validate(...)`.
- **Assert `result.isValid()` is true** (zero ERROR messages) for both — i.e. the real reports conform to
  the official schema. On failure, dump the collected messages so we see exactly what diverged.
- (Optional sanity check: feed a deliberately-broken XML and assert it reports an ERROR — proves the gate
  actually rejects bad input.)

## 4. Files touched

| File | Change |
|------|--------|
| `src/main/java/com/vyttah/goaml/engine/validation/XsdSchemaValidator.java` | **new** |
| `src/test/java/com/vyttah/goaml/engine/validation/GoamlXsdValidationTest.java` | **new** |
| *(none else)* | no `build.gradle`, no `domain/`, no other engine changes |

The XSD is already on the classpath at `src/main/resources/xsd/goaml/5.0.2/goAMLSchema.xsd` (vendored).

## 5. How it's verified

```bash
./gradlew test --tests '*GoamlXsdValidationTest'
```
**Expected:** green — both real DPMSR samples validate against `goAMLSchema.xsd`. The full suite
(`./gradlew test`) also stays green (nothing else changed).

## 6. Risks / caveats (honest)

- **The lone `vc:minVersion="1.1"` attribute** on the schema root: the JDK `SchemaFactory` *should* ignore
  it (it's in the XML-Schema-versioning namespace, designed to be skipped). **If** it instead rejects the
  schema, the fix is small and local — either enable the versioning-namespace handling on the factory, or
  strip that one attribute when loading. I'll handle it in this step if it surfaces; it doesn't change the
  approach.
- **PII:** the two samples contain real customer data. They're used **as-is** as local test fixtures per
  your decision; remember to scrub before any `git push`.
- `goAMLReportDataSchema.xsd` is **not** used here (it's the no-header internal/export schema).

## 7. Done criteria

- `XsdSchemaValidator` compiles the schema once and validates a report `byte[]`, returning structured
  results.
- Both real samples validate clean (green test).
- `./gradlew test` fully green. No codegen, no `domain/` or engine changes.

## 8. What this step does NOT do

No xjc/codegen (Step 2), no round-trip through generated types (Step 3), no engine re-point (Step 4), no
DPMSR builder (Step 6). It only stands up the validation gate and proves the schema↔samples agreement.

---

## Outcome (2026-06-04) — ✅ done, green

**Built:** `engine/validation/XsdSchemaValidator.java` (JDK JAXP, schema compiled once, XXE-hardened,
collects findings into `ValidationResult`) + `engine/validation/GoamlXsdValidationTest.java`.

**Result:** all 3 tests pass — **both real DPMSR samples validate against `goAMLSchema.xsd`**, and a
structurally-invalid report is correctly rejected. (Full suite: the 6 Testcontainers DB tests fail only
because Docker wasn't running locally — unrelated to this change; all non-DB tests pass.)

**⚠️ Schema patches applied (Option A, approved) — semantics-preserving, FIU original kept at
`assets/goAMLSchema.xsd`; re-apply on any future re-export:**
1. **`email_address` pattern:** literal `-` moved to end of two char classes (`[_a-zA-Z0-9-+]` →
   `[_a-zA-Z0-9+-]`) — `9-+` was read by strict XSD as an invalid character range (blocked schema
   compilation entirely).
2. **name pattern (×26):** stripped the `^`/`$` — in XSD regex these are **literal characters** (patterns
   are already implicitly whole-string anchored), so `^...$` rejected real names like `Sara`.
   (`^[a-zA-Z0-9 .'-]*$` → `[a-zA-Z0-9 .'-]*`)

Both align strict W3C XSD with the FIU's lenient validator (the samples came *from* the portal). The URL
pattern was already commented-out by the FIU — untouched. A consolidated patch-log comment sits at the top
of `goAMLSchema.xsd`.

**Root cause to remember:** the goAML schema uses .NET/PCRE regex conventions that strict XSD-1.0 (JDK
Xerces) interprets differently. Future schema re-exports will reintroduce these — re-apply the two patches.
