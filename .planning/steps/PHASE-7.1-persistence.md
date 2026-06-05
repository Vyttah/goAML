# Phase 7.1 — Persistence (report + submission + tenant goAML config)

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-7-persistence-service-web.md](../plans/phase-7-persistence-service-web.md).

---

## 1. Goal & why

Phase 7 wires the engine + b2b to HTTP. Step 7.1 lays the **storage foundation**: the per-tenant `report`
and `submission` tables, and a shared `TenantGoamlConfig` JPA entity over the existing `tenant_goaml_config`
table (so the service can resolve a tenant's `B2bTenantConfig` + `rentity_id`). No service/web yet.

## 2. What gets built

- **`db/migration/tenant/V2__reports.sql`** — `report` + `submission` tables in the tenant schema (run by
  the existing programmatic tenant Flyway + on provisioning). Report content is **JSONB input + XML snapshot
  + metadata** (per the phase decision), `entity_reference` UNIQUE (idempotency).
- **`model/entity/report/Report`** + **`repository/report/ReportRepository`** (tenant-scoped, no `@Table`
  schema). JSONB columns (`input`, `validation_errors`) mapped as `String` via `@JdbcTypeCode(SqlTypes.JSON)`.
- **`model/entity/submission/Submission`** + **`repository/submission/SubmissionRepository`** (tenant-scoped).
- **`model/entity/goamlconfig/TenantGoamlConfig`** + **`repository/goamlconfig/TenantGoamlConfigRepository`**
  (shared, `@Table(schema="public", name="tenant_goaml_config")`, read-mostly; `findByTenantId`).

## 3. Schema (tenant)

`report`: `id`, `entity_reference` (UNIQUE), `report_code`, `rentity_id`, `status`
(`DRAFT|VALID|INVALID|SUBMITTED|ACCEPTED|REJECTED|FAILED`), `input jsonb`, `report_xml text`,
`validation_errors jsonb`, `created_by`, `created_at`, `updated_at`. Indexes on `status`, `created_at`.

`submission`: `id`, `report_id` → `report(id)` CASCADE, `reportkey`, `status`
(`SUBMITTED|ACCEPTED|REJECTED|FAILED`), `errors text`, `submitted_at`, `updated_at`. Index on `report_id`.

## 4. Verification

Testcontainers: provision a tenant → bind `TenantContext` → save a `Report` (with JSONB input + errors)
and a `Submission`, read them back (JSONB round-trips, statuses persist, FK holds); a duplicate
`entity_reference` violates the unique constraint. `TenantGoamlConfigRepository.findByTenantId` reads a
seeded `public.tenant_goaml_config` row. `./gradlew test` green.

## 5. Out of scope (later steps)
Service orchestration (7.2), REST/DTOs/RBAC (7.3), docs (7.4). No JAXB `Report` reference here.

---

## Outcome

Built the storage foundation:
- **`db/migration/tenant/V2__reports.sql`** — `report` (JSONB `input` + `report_xml` + metadata,
  `entity_reference` UNIQUE) + `submission` (FK → report, reportkey, status) in the tenant schema.
- **`model/entity/report/Report`** + `repository/report/ReportRepository` (`findByEntityReference`,
  `existsByEntityReference`); JSONB columns mapped as `String` via `@JdbcTypeCode(SqlTypes.JSON)`.
- **`model/entity/submission/Submission`** + `repository/submission/SubmissionRepository`.
- **`model/entity/goamlconfig/TenantGoamlConfig`** + `repository/.../TenantGoamlConfigRepository`
  (`findByTenantId`) over the existing shared `tenant_goaml_config`.

**Verified:** `ReportPersistenceTest` (Testcontainers) — report+submission round-trip in a provisioned
tenant schema (JSONB input survives, FK holds, statuses persist), duplicate `entity_reference` →
`DataIntegrityViolationException`, and `tenant_goaml_config` reads back. Full
`./gradlew test jacocoTestCoverageVerification` green.

**Note:** the JPA `Report` is the persistence aggregate — distinct from the JAXB
`domain.generated.Report`; the service (7.2) will avoid importing the JAXB type by reading the marshalled
XML off an enhanced `ValidatedReport`. Coverage gate extends to the new `service`/`controller` packages in
7.2/7.3 (entities/repos here are exercised by the Testcontainers test).
