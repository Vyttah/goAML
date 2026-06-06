# Phase 11.2 — GoamlXmlImporter (unmarshal → validate → persist)

> **Status: ✅ DONE (2026-06-06).**
> Part of [../plans/phase-11-ingestion.md](../plans/phase-11-ingestion.md). Second step of Phase 11.

---

## 1. Goal & why
The first importer: turn an uploaded **goAML XML** file into a persisted, validated, re-submittable report
— reusing the existing `unmarshal` + XSD/rules validators rather than any new parsing. Single file = one
report. No REST or job-orchestration yet (11.4).

## 2. What was built

| File | Role |
|---|---|
| `service/ingestion/ImportRowResult` | Per-row outcome record shared by both importers: `(row, entityReference, status, reportId, errors)`. `status` = `VALID`/`INVALID` (report created) or `FAILED` (no report); `created(...)`/`failed(...)` factories; `reportCreated()`. |
| `service/ingestion/GoamlXmlImporter` | `importXml(bytes, filename, tenantId, actor)`: unmarshal → re-marshal canonical → XSD + rules validate → persist a `report` row exactly like `DefaultReportService`. Returns one `ImportRowResult`; never throws for bad input. |

## 3. Key understanding / decisions
- **Reuses the engine wholesale** — `ReportMarshaller.unmarshal`/`marshal`, `XsdSchemaValidator`,
  `ReportValidator`. The stored `report_xml` is the **re-marshalled canonical** form (not the raw upload),
  so imports are normalized identically to engine-built reports.
- **The XML tree is authoritative for `rentity_id`/`report_code`/`entity_reference`** — read from the
  unmarshalled `domain.generated.Report` (`getRentityId()`, `getReportCode().value()`,
  `getEntityReference()`), not from tenant config (an import carries its own header).
- **Persisted like the JSON path, INVALID included.** An XML that fails validation is still created as an
  `INVALID` report (viewable/fixable), mirroring `DefaultReportService` + `POST /reports`. The row's
  `status` reflects `VALID`/`INVALID`; validation messages go into `errors`. Only *unreadable/missing-ref/
  duplicate* are `FAILED` (no report).
- **`input` JSONB = a source marker** (`{"source":"GOAML_XML_IMPORT","filename":…}`) since there's no
  `DpmsrCreateRequest` behind an import — the canonical XML in `report_xml` is the real content.
- **Never throws for bad input** — `MarshallingException` (unparseable), blank `entity_reference`, and
  duplicate all become `FAILED` rows. This is what lets the (11.3) CSV path isolate per-row failures and
  the (11.4) job never 500.
- **`com.vyttah.goaml.domain.generated.Report` vs the JPA `report.Report`** — disambiguated by importing the
  domain type and fully-qualifying the entity at the persist site (same clash pattern as the engine
  `Attachment` record).

## 4. Tests
- **`GoamlXmlImporterTest`** (mocked marshaller/validators/repos, real `ObjectMapper`, real domain tree):
  valid → `VALID` report saved (captured: ref/code/rentity/status/xml/source-marker); XSD error →
  `INVALID` report still saved with the message; unparseable (`MarshallingException`) → `FAILED`, no save;
  missing `entity_reference` → `FAILED`, no save; duplicate → `FAILED`, no save.
- **`GoamlXmlImporterIntegrationTest`** (Testcontainers, real beans): the engine's **golden `DPMSR.xml`**
  imports to a single `VALID` report (`DPMSR-2026-001`, canonical XML, source marker); a **re-import** of the
  same file is `FAILED` ("already exists") with no second report — proving round-trip fidelity + idempotency.

## 5. Verification
`./gradlew test --tests GoamlXmlImporterTest --tests GoamlXmlImporterIntegrationTest` → **BUILD SUCCESSFUL**;
6 tests pass. `git status` scoped to Phase 11.2 files.

---

## Outcome
✅ goAML XML import works end-to-end (validated, persisted, idempotent, re-submittable). Next: **11.3** —
the `CsvImporter` (row → `DpmsrCreateRequest` → `ReportService.create`, per-row isolation).
