# 08 — Testing

> The test strategy, what each test proves, and how golden-file XML regression works.
> Tests under `src/test/java/com/vyttah/goaml/`; golden XMLs under `src/test/resources/golden/`.

---

## 1. Philosophy & tooling

- **JUnit 5 (Jupiter)** + **AssertJ** everywhere.
- **No mocking framework.** There is zero Mockito/WireMock in the suite today. Engine/unit tests just
  `new` the classes; integration tests use a **real Postgres via Testcontainers** (`postgres:16-alpine`,
  wired through Spring Boot's `@ServiceConnection` — no manual datasource property juggling).
- **XMLUnit** for semantic XML comparison in the golden tests.

Two broad categories:
1. **Pure unit / engine tests** — no Spring, no DB. Build POJOs, marshal, validate, package, assert.
2. **Integration tests** — `@SpringBootTest` + Testcontainers Postgres. Boot the app, hit real
   endpoints, prove DB isolation.

Run everything: `./gradlew test`. (Confirmed green: `BUILD SUCCESSFUL`.)

---

## 2. The golden-file XML regression (`engine/EngineGoldenTest`)

This is the safety net for the XML output. It's a `@ParameterizedTest @EnumSource(ReportCode.class)`
that, for **every one of the 7 report codes**:
1. Gets the canonical sample via `SampleReports.sampleFor(code)`.
2. Picks `ActivityReportBuilder` or `TransactionReportBuilder` based on `sample.isActivity()`.
3. Marshals to XML via `ReportMarshaller`.
4. Compares against the committed `src/test/resources/golden/<CODE>.xml` using **XMLUnit**
   (`DiffBuilder ... ignoreWhitespace().ignoreComments().checkForSimilar()` — a semantic diff). Any
   difference fails the test and dumps the diff + actual XML.

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
builders. It exposes `Sample(header, activity, transactions)` with `isActivity()`, and
`sampleFor(ReportCode)`. Every sample shares a header skeleton: `rentityId=101`, branch `DXB-MAIN`,
`SubmissionCode.E`, date `2026-05-26T10:00:00`, currency `AED`, a default MLRO (Sara Khan), a default
location (Dubai Gold Souk).

| Code | Shape | Highlights |
|------|-------|-----------|
| STR | transaction | TXN-987654, 120000.00 AED, structuring suspected |
| AIFT | transaction | FIU ref `FIU-REQ-AIFT-2026-009`, 65000.00 |
| ECDDT | transaction | FIU ref `FIU-REQ-ECDDT-2026-022`, PEP review, 220000.00 |
| SAR | activity | unusual frequency, person party only |
| AIF | activity | FIU ref `FIU-REQ-AIF-2026-008` |
| ECDD | activity | high-risk PEP, FIU ref `FIU-REQ-ECDD-2026-021` |
| DPMSR | activity | buyer + **one GoodsServices** GOLD bar, 75000.00 AED, indicator `DPMSR_CASH_THRESHOLD` — the only sample with goods |

Transaction samples use a bi-party cash transfer (Layla Hassan AE → John Smith GB). Activity samples use
a single person report-party with `significance=8` and an `EID` identification.

---

## 4. What each test class proves

| Test | Proves | DB? |
|------|--------|-----|
| `SmokeTest` | App boots vs real Postgres; Flyway baseline runs; `/actuator/health` → 200 UP | Testcontainers |
| `domain/StrTransactionRoundTripTest` | STR POJO round-trips through raw JAXB; transaction element ordering; bi-party invariant (no stray `<t_from>`/`<t_to_my_client>`) | no |
| `domain/DpmsrReportRoundTripTest` | DPMSR (activity) round-trips; header + activity ordering; values re-parse | no |
| `engine/EngineGoldenTest` | All 7 report types marshal to their golden XML (XMLUnit) | no |
| `engine/marshal/ReportMarshallerTest` | UTF-8 declaration, round-trip; malformed XML → `MarshallingException` | no |
| `engine/packaging/ReportZipPackagerTest` | ZIP contains report+attachment; limit breaches throw (bad ext, oversize, too many) | no |
| `engine/validation/ReportValidatorTest` | All samples validate clean for `ae`; 10 negative cases by error code | no |
| `persistence/SharedSchemaTest` | Shared migration seeds: UAE jurisdiction, 4 roles, 7 core tables | Testcontainers |
| `service/tenant/TenantProvisioningServiceTest` | `provision()` creates schema, runs tenant Flyway, inserts tenant + BCrypt admin; dup slug / unknown jurisdiction throw | Testcontainers |
| `security/AuthFlowTest` | 401 when anon; login issues JWT; `/me` returns identity + tenant context; `USER.LOGIN` audited in tenant schema; wrong password → 401 | Testcontainers |
| `security/RbacTest` | `/admin/ping`: TENANT_ADMIN → 403, SUPER_ADMIN → 200 | Testcontainers |
| `tenant/TenantIsolationTest` | Two tenants; rows written under tenant A are invisible to tenant B (count 2 vs 0) | Testcontainers |

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

**STR (transaction shape)** — the transaction body:
```xml
<transaction>
    <transactionnumber>TXN-987654</transactionnumber>
    <internal_ref_number>INT-2026-555</internal_ref_number>
    <transaction_location>Dubai Main Branch</transaction_location>
    <date_transaction>2026-05-24T14:30:00</date_transaction>
    <transmode_code>CASH</transmode_code>
    <amount_local>120000.00</amount_local>
    <t_from_my_client>
        <from_funds_code>CASH</from_funds_code>
        <from_person>
            <first_name>Layla</first_name><last_name>Hassan</last_name>
            <id_number>784198512345671</id_number>
            <phones/><addresses/>
            <nationality1>AE</nationality1><residence>AE</residence>
        </from_person>
        <from_country>AE</from_country>
    </t_from_my_client>
    <t_to>
        <to_funds_code>BANKD</to_funds_code>
        <to_person>
            <first_name>John</first_name><last_name>Smith</last_name>
            <phones/><addresses/>
            <nationality1>GB</nationality1><residence>GB</residence>
        </to_person>
        <to_country>GB</to_country>
    </t_to>
</transaction>
```
(Note: empty collections render self-closing — `<phones/>`, `<addresses/>` — and `transactionnumber`
has no underscore. See the [domain traps](04-domain-model.md#9-the-two-element-name-traps-memorize-these).)

**DPMSR (activity shape)** — the goods line:
```xml
<goods_services>
    <item_type>GOLD</item_type>
    <item_make>Emirates Gold DMCC</item_make>
    <description>1kg gold bullion bar, .9999 fine</description>
    <presently_registered_to>Mohamad Ali Al-Jaber</presently_registered_to>
    <estimated_value>75000.00</estimated_value>
    <status_code>SOLD</status_code>
    <currency_code>AED</currency_code>
    <size>1000</size>
    <size_uom>GRAM</size_uom>
</goods_services>
```

---

## 6. What's NOT tested yet (because it's not built)

No tests exist for B2B submission, AWS integration, the scheduler, notifications, ingestion, MCP/CLI, or
the frontend — those are Phases 6–14. When you build Phase 6, the plan calls for **WireMock** (B2B HTTP)
and **LocalStack** (AWS) — neither dependency is in `build.gradle` yet, so adding them is part of that
phase.

---

**Next:** [09 — Build Order & Roadmap](09-build-order-and-roadmap.md).
