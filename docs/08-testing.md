# 08 — Testing

> The test strategy, what each test proves, and how golden-file XML regression works.
> Tests under `src/test/java/com/vyttah/goaml/`; golden XMLs under `src/test/resources/golden/`.

---

## 1. Philosophy & tooling

- **JUnit 5 (Jupiter)** + **AssertJ** everywhere.
- **Mocking is used where it earns its keep.** Engine/unit tests still just `new` the classes (no Spring, no
  DB) — that's the bulk of the suite. But from Phase 6 onward the suite **does** use **Mockito** (unit tests
  for the AWS/B2B clients, services) and **WireMock** (the goAML B2B HTTP client, up to ZIP-submission E2E);
  integration tests use a **real Postgres via Testcontainers** (`postgres:16-alpine`, wired through Spring
  Boot's `@ServiceConnection`), plus **LocalStack** (S3/Secrets/SES) and **Redis** for the Phase-6 ITs
  (these `assumeTrue`-skip if the compose services aren't up). *(The earlier "zero Mockito/WireMock" claim was
  true only through Phase 5 — it is no longer accurate.)*
- **XMLUnit** for semantic XML comparison in the golden tests.

Two broad categories:
1. **Pure unit / engine tests** — no Spring, no DB. Build POJOs, marshal, validate, package, assert.
2. **Integration tests** — `@SpringBootTest` + Testcontainers Postgres. Boot the app, hit real
   endpoints, prove DB isolation.

Run everything: `./gradlew test`. (Confirmed green: `BUILD SUCCESSFUL`.)

---

## 2. The golden-file XML regression (`engine/EngineGoldenTest`)

This is the safety net for the XML output. It's a `@ParameterizedTest` over **`ReportType`** (filtered to
the 7 modeled codes — `DPMSR, SAR, AIF, ECDD, STR, AIFT, ECDDT`; 4 activity + 3 transaction) that, for
each:
1. Gets the canonical sample via `SampleReports.sampleFor(code)`.
2. Picks `ActivityReportBuilder` or `TransactionReportBuilder` based on `sample.isActivity()`.
3. Marshals to XML via `ReportMarshaller`.
4. **Validates the output against the authoritative `goAMLSchema.xsd`** via `XsdSchemaValidator` —
   **always**, even during regeneration, so a malformed sample can never be blessed as a golden.
5. Compares against the committed `src/test/resources/golden/<CODE>.xml` using **XMLUnit**
   (`DiffBuilder ... ignoreWhitespace().ignoreComments().checkForSimilar()` — a semantic diff). Any
   difference fails the test and dumps the diff + actual XML.

> The 3 transaction goldens were **restored during the XSD-first migration** once `transmode.json` was
> reconciled with the XSD `conduction_type` enum (Step 5). The other ~10 schema report types are deferred
> to their functional phases.

**Regenerating goldens** after an *intentional* change:
```bash
./gradlew test -Dgoaml.golden.regenerate=true
```
This overwrites the golden files with current engine output instead of asserting. **Always review the
diff before committing** — that's the whole point of having goldens. (The system property is forwarded
from Gradle to the test JVM only when set; see `build.gradle`.)

---

## 3. `SampleReports` — the canonical "how to build each report" fixture

Not a test — a fixture helper that is the **best reference for constructing each report type** via the
builders, **built on the xjc-generated model**. It exposes `Sample(header, activity, transactions)` with
`isActivity()`, and `sampleFor(ReportType)`. Every sample shares a header skeleton: `rentityId=101`, branch
`DXB-MAIN`, submission code `"E"`, date `2026-05-26T10:00:00`, currency `CurrencyType.AED`, a default MLRO
(Sara Khan), a default location (Dubai Gold Souk). The activity body is set via `setReportActivity(...)`.

| Code | Shape | Highlights |
|------|-------|-----------|
| STR | transaction | TXN-987654, 120000.00 AED, transmode `ELCFT`, structuring suspected |
| AIFT | transaction | FIU ref `FIU-REQ-AIFT-2026-009` |
| ECDDT | transaction | FIU ref `FIU-REQ-ECDDT-2026-022`, PEP review |
| SAR | activity | unusual frequency, single report party |
| AIF | activity | FIU ref `FIU-REQ-AIF-2026-008` |
| ECDD | activity | high-risk PEP, FIU ref `FIU-REQ-ECDD-2026-021` |
| DPMSR | activity | buyer party + **one goods `<item>`** (GOLD bar, 75000.00 AED), indicator `DPMSR_CASH_THRESHOLD` |

Samples cover the schema-mandatory fields per type (so they pass the XSD gate): the sender carries
birthdate, phones, `tax_reg_number` (`Y`), and an `EID` identification with `issue_date`; transactions
carry the mandatory single-char `transmode_comment`.

---

## 4. What each test class proves

The test tree **mirrors the main packages** (`config/tenant`, `repository`, `service/tenant`,
`model/mapper/tenant`, `engine/*`, `domain/generated`, `security`).

| Test | Proves | DB? |
|------|--------|-----|
| `SmokeTest` | App boots vs real Postgres; Flyway baseline runs; `/actuator/health` → 200 UP | Testcontainers |
| `engine/validation/GoamlXsdValidationTest` | The 2 **real DPMSR samples** validate clean against `goAMLSchema.xsd`; a bogus element is rejected (`XSD` code) — the XSD-first anchor test | no |
| `domain/generated/GeneratedModelRoundTripTest` | Real DPMSR samples round-trip through the **generated** model and re-marshal | no |
| `domain/generated/GeneratedModelTest` | Generated-model shape sanity (e.g. the `getReportActivity()` choice slot) | no |
| `engine/EngineGoldenTest` | Each of the 7 modeled types is **XSD-valid** *and* matches its golden XML (XMLUnit) | no |
| `engine/build/DpmsrReportBuilderTest` | `DpmsrReportBuilder` builds a maximal (entity party + directors + 2 goods) and a minimal DPMSR that are **both rules-clean and XSD-valid**, via the record **and** fluent forms | no |
| `engine/lookup/LookupXsdConsistencyTest` | `transmode ⊆ conduction_type` and `currencies ⊆ currency_type` (parses the XSD) — guards lookup⊆XSD drift | no |
| `engine/marshal/ReportMarshallerTest` | UTF-8 declaration, round-trip; malformed XML → `MarshallingException` | no |
| `engine/packaging/ReportZipPackagerTest` | ZIP contains report+attachment; limit breaches throw (bad ext, oversize, too many) | no |
| `engine/validation/ReportValidatorTest` | Samples validate clean for `ae`; negative cases by error code | no |
| `model/mapper/tenant/TenantMapperTest` | The MapStruct `TenantMapper` maps entity↔DTO | no |
| `repository/SharedSchemaTest` | Shared migration seeds: UAE jurisdiction, 4 roles, 7 core tables | Testcontainers |
| `service/tenant/TenantProvisioningServiceTest` | `provision()` creates schema, runs tenant Flyway, inserts tenant + BCrypt admin; dup slug / unknown jurisdiction throw | Testcontainers |
| `security/AuthFlowTest` | 401 when anon; login issues JWT; `/me` returns identity + tenant context; `USER.LOGIN` audited in tenant schema; wrong password → 401 | Testcontainers |
| `security/RbacTest` | `/admin/ping`: TENANT_ADMIN → 403, SUPER_ADMIN → 200 | Testcontainers |
| `config/tenant/TenantIsolationTest` | Two tenants; rows written under tenant A are invisible to tenant B (count 2 vs 0) | Testcontainers |

### "Round-trip" defined
Build a `Report` → marshal to XML → assert on the XML (element presence + ordering via `indexOf`, and
negative invariants) → unmarshal back → assert the re-parsed getters equal the originals. It proves the
JAXB bindings preserve structure **and** values both directions.

### Isolation test, specifically
`TenantIsolationTest` provisions two tenants, writes 2 `audit_log` rows under tenant A's
`TenantContext`, then counts rows bound to A (expects 2) and to B (expects 0). The only variable between
counts is the `TenantContext` value — so passing proves the
`TenantContext → TenantIdentifierResolver → SchemaMultiTenantConnectionProvider` chain routes each
connection to the correct schema.

---

## 5. What the golden XML actually looks like

**STR (transaction shape)** — the transaction body (abbreviated):
```xml
<transaction>
    <transactionnumber>TXN-987654</transactionnumber>
    <internal_ref_number>INT-2026-555</internal_ref_number>
    <transaction_location>Dubai Main Branch</transaction_location>
    <date_transaction>2026-05-24T14:30:00</date_transaction>
    <transmode_code>ELCFT</transmode_code>
    <transmode_comment>E</transmode_comment>
    <amount_local>120000.00</amount_local>
    <t_from_my_client>
        <from_funds_code>CASH</from_funds_code>
        <from_person>
            <gender>F</gender><first_name>Layla</first_name><last_name>Hassan</last_name>
            <birthdate>1989-07-02T00:00:00</birthdate>
            <id_number>784198512345671</id_number>
            <nationality1>AE</nationality1><residence>AE</residence>
            <phones><phone><tph_contact_type>PRIVT</tph_contact_type>…</phone></phones>
            <identifications><identification><type>EID</type>…</identification></identifications>
            <tax_reg_number>Y</tax_reg_number>
        </from_person>
        <from_country>AE</from_country>
    </t_from_my_client>
    <t_to>…<to_funds_code>BANKD</to_funds_code>…</t_to>
</transaction>
```
(Note: `transmode_code` is now a real `conduction_type` value — `ELCFT` — and the mandatory single-char
`transmode_comment` follows it. `transactionnumber` has no underscore; phone fields carry the `tph_`
prefix — these element-name quirks come straight from the XSD via the generated model, see [04](04-domain-model.md).)

**DPMSR (activity shape)** — the goods line is wrapped in `<goods_services><item>…`:
```xml
<goods_services>
    <item>
        <item_type>GOLD</item_type>
        <item_make>Emirates Gold DMCC</item_make>
        <description>1kg gold bullion bar, .9999 fine</description>
        <presently_registered_to>Mohamad Ali Al-Jaber</presently_registered_to>
        <estimated_value>75000.00</estimated_value>
        <status_code>SOLD</status_code>
        <currency_code>AED</currency_code>
        <size>1000</size>
        <size_uom>GRAM</size_uom>
    </item>
</goods_services>
```

---

## 6. Coverage across the later phases (all now built + tested)

This section once listed "not built yet" gaps; **those are all built and tested now**:
- **B2B submission** — WireMock-stubbed unit + E2E up to ZIP submission (Phase 6/7).
- **AWS integration** — `GoamlSecretsClient`/`S3StorageClient`/`SesClient` via Mockito unit tests +
  **LocalStack** ITs (skipped if compose isn't up).
- **Scheduler** — `SubmissionStatusPoller` Testcontainers IT (two tenants transition + isolation).
- **Notifications** — Testcontainers ITs (in-app fan-out + the email-enabled gate).
- **Ingestion** — MockMvc E2E (XML + CSV import, per-row results).
- **Federated auth / integration** — E2E incl. JIT + bad-signature 401; accounting/screening push.
- **MCP / CLI** — built (Phase 12); they delegate to the same services (REST/MCP/CLI parity).
- **Frontend** — `frontend/` SPA has Vitest+RTL specs (run on Node 18), incl. golden `dpmsrPayload` tests.

The `build.gradle` JaCoCo gate is extended per phase as new packages land. Both **WireMock** and
**LocalStack** are in `build.gradle` (added in Phase 6). The total backend suite is ~90+ classes mirroring
the main packages. **Authoritative live status:** [`.planning/STATE.md`](../.planning/STATE.md).

---

**Next:** [09 — Build Order & Roadmap](09-build-order-and-roadmap.md).
