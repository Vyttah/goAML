# Phase 7 — Persistence + Service + Web REST (wire the engine + b2b to HTTP)

> **Status: 🔲 PROPOSED (2026-06-05) — awaiting your approval before implementation.**
> Roadmap **Phase 7**. The first phase that makes the **report lifecycle manually testable via the API**
> (curl/Postman). Builds on the engine (Phases 4–5 + XSD-first) and the b2b client (Phase 6).

---

## 1. What this phase is, and why

Everything to build, validate, package, and submit a DPMSR exists as **libraries** (engine + b2b) but
nothing is reachable over HTTP and nothing is persisted. Phase 7 adds the **persistence + service + web**
layers so a user (or the future UI / RabbitMQ consumer) can:

1. **Create** a DPMSR report from JSON → the engine **builds + validates** it (rules + XSD) → it's
   **stored** with a status.
2. **Read / list** their reports and the validation results.
3. **Submit** a validated report (MLRO only) → packaged ZIP → goAML `postReport` using *that tenant's*
   credentials → store the returned `reportkey`.
4. **Refresh status** on-demand (the async poller is Phase 9).

Scope (your decisions): **DPMSR-only** API; report content persisted as **JSONB input + XML snapshot +
metadata** (no normalized report tree).

## 2. Decisions

| # | Decision | Choice |
|---|----------|--------|
| D1 | Report types | **DPMSR only** (extensible; other 16 types need their own input contracts later). |
| D2 | Persistence shape | **`report`**: JSONB `input` (the `DpmsrReportInput`), `report_xml` (built XML snapshot), + metadata (`entity_reference` UNIQUE, `report_code`, `status`, `validation_errors` JSONB, `rentity_id`); **`submission`**: per submit attempt (`report_id`, `reportkey`, `status`, `errors`, timestamps). |
| D3 | Idempotency | DB **UNIQUE(entity_reference)** in the tenant schema (the validator already hard-requires it). Re-create with the same ref → 409. |
| D4 | Submit | **Synchronous** in Phase 7: MLRO submit → package → `postReport` inline → persist `reportkey`. Async poll/retry = Phase 9. |
| D5 | Status refresh | **On-demand** `GET …/status` calls `GoamlB2bClient.getReportStatus`; no scheduler yet. |
| D6 | Attachments | **None** in Phase 7 (S3 is Phase 8) — ZIP carries the report XML only. |
| D7 | Tenant B2B config | New **`TenantGoamlConfig`** shared JPA entity + repo over the existing `tenant_goaml_config` table → build `B2bTenantConfig` (baseUrl, secretsPath, authMode) + `rentity_id` for the report header. |
| D8 | RBAC | create/validate/read → ANALYST or MLRO; **submit → MLRO only** (`@PreAuthorize`); list/read tenant-scoped. |
| D9 | Status model | `report.status ∈ {DRAFT, VALID, INVALID, SUBMITTED, ACCEPTED, REJECTED, FAILED}`; `submission.status ∈ {SUBMITTED, ACCEPTED, REJECTED, FAILED}`. Plain Strings (project convention), documented in SQL comments. |

## 3. Step breakdown (one commit per step, each green; per-step doc in `steps/`)

### Step 7.1 — Persistence
- **Tenant migration** `db/migration/tenant/V2__reports.sql`: `report` + `submission` tables (per D2/D3),
  indexes. (Runs via the existing programmatic tenant Flyway + on provisioning.)
- **Shared:** no new table — add JPA **`TenantGoamlConfig`** entity (`model/entity/goamlconfig/`) + repo
  (`repository/goamlconfig/`) over the existing `tenant_goaml_config`.
- **Entities** (`model/entity/report/Report`, `…/submission/Submission`, no `Entity` suffix) + repos.
  ⚠️ Name clash note: the JPA `Report` ≠ the generated JAXB `Report` — the entity lives in
  `model.entity.report`, referenced by FQN/import alias where both appear (the service layer).
- **Tests:** Testcontainers — provision a tenant, assert `report`/`submission` exist in the tenant schema
  and round-trip a row (JSONB input + status); `TenantGoamlConfigRepository` reads a seeded row.

### Step 7.2 — Service (orchestration)
- **`ReportService`** (interface + `Default*`): `create(DpmsrCreateRequest)` → map to `DpmsrReportInput` →
  `DpmsrReportBuilder.buildAndValidate` → persist (`VALID`/`INVALID` + errors + XML) → return result;
  `get(id)`, `list()`.
- **`SubmissionService`**: `submit(reportId)` → guard status `VALID` → load `TenantGoamlConfig` → build
  `B2bTenantConfig` → `ReportZipPackager.zip(xml, …, UAE_DEFAULT)` → `GoamlB2bClient.postReport` → persist
  `submission` + set report `SUBMITTED`; map `B2bValidationException`/`B2bAuthException`/`B2bTransportException`
  → typed service outcomes; `refreshStatus(reportId)` → `getReportStatus` → update.
- **Audit:** record `REPORT.CREATE` / `REPORT.SUBMIT` via the existing `AuditService`.
- **Tests:** unit (Mockito for the b2b client + repos) covering valid/invalid/submit/duplicate-ref/
  b2b-error paths; WireMock-backed submit path.

### Step 7.3 — Web (REST) + DTOs + mappers
- **`controller/report/ReportController`** (thin): `POST /api/v1/reports`, `GET /api/v1/reports`,
  `GET /api/v1/reports/{id}`, `POST /api/v1/reports/{id}/submit` (MLRO), `GET /api/v1/reports/{id}/status`.
- **DTOs** (`model/dto/report/…`): `DpmsrCreateRequest`, `ReportResponse`, `ValidationResponse`,
  `SubmissionResponse`; **MapStruct** mappers where useful.
- **RBAC** via `@PreAuthorize` (D8); errors via the existing `GlobalExceptionHandler` (add 404/409/422
  mappings: not-found, duplicate `entity_reference`, validation-failed).
- **Tests:** `@SpringBootTest` + Testcontainers + WireMock — full flow: login (MLRO) → create DPMSR →
  validation result → submit → `reportkey`; ANALYST submit → 403; duplicate ref → 409; tenant isolation
  (tenant A can't see B's reports).

### Step 7.4 — Docs + planning sync
- `docs/07` (new tables + entities), `docs/02` (`controller/service/repository/report` ✅), `docs/06`
  (new endpoints + RBAC), `docs/09`/`ROADMAP`/`STATE` (Phase 7 ✅, Phase 8 next), `docs/11` (terms).
- A **`docs/13`-style API quickref** or a short "how to test the report flow" snippet (curl examples).

## 4. JaCoCo / coverage
Extend the coverage gate to the new packages (`service.report`, `service.submission`, `controller.report`)
at the same ≥90% instruction / ≥80% branch bar; unit tests (Mockito/WireMock) carry it, integration tests
(Testcontainers) prove the wiring. Both layers kept.

## 5. What this phase does NOT do
S3 attachments (Phase 8), the async status poller + retry (Phase 9), notifications (Phase 10), non-DPMSR
report types, the React UI (Phase 13). No real FIU calls in tests (WireMock); live submit needs a real
`tenant_goaml_config` row + secret + reachable goAML endpoint.

## 6. How you'll test it manually (after this phase)
With `docker compose up -d postgres localstack redis` and the app running: log in (seeded admin / an MLRO),
`POST /api/v1/reports` with a DPMSR JSON body → see the validation result; `POST …/{id}/submit`. **Submit**
needs a `tenant_goaml_config` row + a Secrets Manager secret (LocalStack) + a goAML endpoint — for local
testing we can point `base_url` at a stub, or I can add a short seed/fixture. (Create/validate/read work
with no FIU at all.)

## 7. Verification
`docker compose up -d postgres localstack redis` → `./gradlew test jacocoTestCoverageVerification` green;
the E2E integration test drives login→create→validate→submit→reportkey; RBAC + isolation + idempotency
asserted; `git status` scoped to Phase 7 dirs + the 7.4 docs.

## 8. Decisions on the two flagged points (resolved)
- **`Report` name clash → eliminated, not worked-around.** `ValidatedReport` is enhanced to also carry the
  **marshalled XML** (computed once in `buildAndValidate`, reused for the XSD gate + persistence). The
  service then persists `report_xml` from `ValidatedReport.xml()` and **never imports the JAXB `Report`** —
  so the JPA entity is plainly named `Report` (table `report`) with no clash anywhere. (Engine tweak lands
  in 7.2.)
- **Manual submit setup → documented local seed, no prod-irrelevant auto-seeder.** Create/validate/read need
  no FIU. Submit is fully covered by WireMock integration tests; for hands-on local submit, 7.4 ships a
  copy-paste **local seed** (a `tenant_goaml_config` row + a LocalStack `aws secretsmanager create-secret` +
  a stub `base_url`). No `@Profile("local")` auto-seeder (keeps prod paths clean).

## 9. Other notes / to confirm in-step
- **Tenant-scoped persistence within a request** is fine — `JwtAuthFilter` binds `TenantContext` before the
  service runs (unlike the audit path which sometimes runs unbound).
- **JSONB mapping:** the `input` + `validation_errors` columns are `jsonb`; entities map them as `String`
  via Hibernate `@JdbcTypeCode(SqlTypes.JSON)` (the service (de)serializes with Jackson). This keeps
  persistence decoupled from both the generated types and the request DTO. Covered by the 7.1 round-trip test.

---

## Outcome — ✅ COMPLETE (2026-06-05)

Delivered across `154a2f5` (7.1) · `fc96046` (7.2) · `82af99f` (7.3) · this commit (7.4):

- **7.1 persistence** — `V2__reports.sql` (`report` JSONB-input + XML snapshot + metadata, `entity_reference`
  UNIQUE; `submission`); `Report`/`Submission`/`TenantGoamlConfig` entities + repos.
- **7.2 service** — `ValidatedReport` carries the marshalled XML (so the service never touches the JAXB
  `Report`); `DpmsrCreateRequest` + `DpmsrRequestMapper`; `ReportService` (create/validate/persist) +
  `SubmissionService` (package → B2B submit → reportkey; status refresh); typed exceptions.
- **7.3 web** — `ReportController` (`/api/v1/reports` create/list/get/submit/status, MLRO-gated submit) +
  response DTOs + `GlobalExceptionHandler` mappings; Testcontainers + WireMock E2E.
- **7.4 docs/planning sync** — `docs/{02,03,06,07,09}`, `CLAUDE.md`, `.planning/{ROADMAP,STATE}`, + the
  local-seed snippet for hands-on submit (docs/03).

**Decisions realised:** DPMSR-only; JSONB input + XML snapshot (no normalized tree); the `Report` name
clash eliminated via `ValidatedReport.xml()`; no prod auto-seeder (documented local seed instead).

**Coverage:** gated packages (b2b + aws + report/submission service + mapper + controller) **98.5% instr /
84.5% branch** — gate passes. The report flow is **manually testable via the API**; live submit needs a
`tenant_goaml_config` row + a Secrets Manager secret + a reachable goAML endpoint.

**Not done (later phases):** S3 attachments (8), async poller + retry (9), notifications (10), non-DPMSR
report types, React UI (13).
