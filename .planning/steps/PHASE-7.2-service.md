# Phase 7.2 — Service layer (report + submission orchestration)

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-7-persistence-service-web.md](../plans/phase-7-persistence-service-web.md).

---

## 1. Goal & why

Turn the engine + b2b libraries into a usable **report lifecycle**: create+validate+persist a DPMSR, and
submit a validated one to the FIU — all tenant-scoped, with the typed outcomes the web layer (7.3) maps to
HTTP. No controllers yet.

## 2. What was built

- **`ValidatedReport` enhanced** — now carries the marshalled **XML** (computed once in `buildAndValidate`,
  reused for the XSD gate + persistence). So the service persists `report_xml` **without importing the JAXB
  `Report`** → the JPA `Report` name is unambiguous. (The "name clash" decision, realised.)
- **`model/dto/report/DpmsrCreateRequest`** — curated, Jackson-clean DPMSR JSON contract (entity/person
  parties, directors, goods, MLRO, location, indicators) persisted verbatim as the report `input`.
- **`model/mapper/report/DpmsrRequestMapper`** — hand-written map onto `DpmsrReportInput` (builds the
  generated JAXB leaf types via `GoamlParties`/`GoamlWrappers`; set-when-present).
- **`service/report/ReportService` + `DefaultReportService`** — `create` (resolve `rentity_id`/jurisdiction
  from `tenant_goaml_config`; build+validate; persist input JSON + XML + status `VALID`/`INVALID` + the
  merged validation messages; audit `REPORT.CREATE`), `get`, `list`. Duplicate `entity_reference` → typed
  exception (pre-empts the DB unique).
- **`service/submission/SubmissionService` + `DefaultSubmissionService`** — `submit` (guard `VALID` → resolve
  `B2bTenantConfig` → `ReportZipPackager.zip` → `GoamlB2bClient.postReport` → persist `submission` + set
  report `SUBMITTED` + audit; FIU 400 → `SubmissionRejected` (report stays editable); auth/transport →
  `SubmissionTransport`), `refreshStatus` (poll `getReportStatus`, map FIU status → our vocabulary, persist).
- **Typed exceptions** (`ReportExceptions`, `SubmissionExceptions`) for the 7.3 HTTP mapping
  (404/409/422/502).

## 3. Key decisions / understanding

- **No-config = INVALID, not error:** a report created before `tenant_goaml_config` exists builds with
  `rentity_id=0` → fails validation with a clear `MANDATORY rentity_id` message (a 200 with INVALID status),
  rather than a hard failure. Submit, which needs the config, throws `TenantConfigMissing`.
- **Tenant + actor** come from the authenticated principal (passed in by the controller in 7.3), not parsed
  from `TenantContext` — `tenantId` keys both `tenant_goaml_config` and the Redis token cache.
- **Audit is recorded last** (the audit service sets/clears its own `TenantContext`), so no repo call follows
  it within a request.
- **FIU status mapping** is deliberately lenient (`contains "accept"/"reject"`), isolated in one method to
  adjust against the real environment.

## 4. Verification

Unit tests (Mockito repos/b2b + real engine/packager, no DB): `DefaultReportServiceTest` (valid→VALID,
below-threshold→INVALID, duplicate→409-exception, no-config→INVALID w/ mandatory rentity_id, get-missing),
`DefaultSubmissionServiceTest` (submit success, not-VALID, not-found, no-config, FIU-reject→FAILED+editable,
transport→failed, refreshStatus accepted/rejected/unknown/no-submission), `DpmsrRequestMapperTest`
(entity+director+goods, person-as-myclient+identifications, neither→throws).

JaCoCo gate extended to `service.report` + `service.submission` + `model.mapper.report`; gated packages
**98.1% instr / 84.5% branch** (≥90%/≥80% — passes). Full `./gradlew test jacocoTestCoverageVerification` green.

## 5. Out of scope (7.3)
REST controllers, request validation wiring, RBAC `@PreAuthorize`, HTTP error mapping in
`GlobalExceptionHandler`, the E2E `@SpringBootTest` flow.

---

## Outcome
✅ Done as above. Next: 7.3 (web/REST + DTOs + RBAC + E2E).
