# 07 — Persistence & Migrations

> Every database table, how Flyway manages them, and the JPA entities that map to them.
> Entities live in `com.vyttah.goaml.model.entity.<feature>/` and repositories in
> `com.vyttah.goaml.repository.<feature>/` (split per feature, **no `Entity` suffix** — see
> [`CONVENTIONS.md`](CONVENTIONS.md)); migrations under `src/main/resources/db/migration/`.

---

## 1. Two locations, two lifecycles

| Location | Schema | When it runs |
|----------|--------|--------------|
| `db/migration/shared/` | `public` | At **app startup** (Spring Boot auto-Flyway, `spring.flyway.locations=classpath:db/migration/shared`, `schemas=public`). |
| `db/migration/tenant/` | `tenant_<uuid>` | **Programmatically**, during tenant provisioning (and on app upgrade, per tenant). Never by the startup Flyway. |

Because the tenant migrations run with Flyway's `.schemas(schemaName)` set, the tenant SQL uses **no
schema qualifier** — Flyway treats the tenant schema as the default and keeps its own
`flyway_schema_history` inside it.

---

## 2. Shared schema (`public`)

### `V1__baseline.sql` — Phase 1 smoke migration
A throwaway table proving the Flyway pipeline works. **No JPA entity** maps to it.

**`schema_baseline`** — `id SERIAL PK`, `phase VARCHAR(32) NOT NULL`, `note TEXT NOT NULL`,
`created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`. Seeds one `phase-1` row.

### `V2__shared_core.sql` — Phase 2 real platform schema (7 tables)

**`jurisdiction`** — one row per FIU target.
| Column | Type | Notes |
|--------|------|-------|
| `code` | `VARCHAR(8)` PK | e.g. `AE` |
| `name` | `VARCHAR(255)` NOT NULL | |
| `currency_code` | `VARCHAR(3)` NOT NULL | |
| `created_at` | `TIMESTAMPTZ` DEFAULT NOW() | |

Seeds `('AE', 'United Arab Emirates', 'AED')`.

**`tenant`** — a client Reporting Entity.
| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` PK | |
| `slug` | `VARCHAR(64)` NOT NULL UNIQUE | |
| `name` | `VARCHAR(255)` NOT NULL | |
| `jurisdiction_code` | `VARCHAR(8)` NOT NULL → `jurisdiction(code)` | |
| `schema_name` | `VARCHAR(80)` NOT NULL UNIQUE | the routing key (`tenant_<hex>`) |
| `status` | `VARCHAR(32)` NOT NULL DEFAULT `'ACTIVE'` | ACTIVE / SUSPENDED / DELETED |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | |

Index `idx_tenant_status`.

**`role`** — fixed RBAC roles.
| Column | Type | Notes |
|--------|------|-------|
| `id` | `SMALLSERIAL` PK | |
| `name` | `VARCHAR(32)` NOT NULL UNIQUE | |
| `description` | `VARCHAR(255)` NOT NULL | |

Seeds `SUPER_ADMIN`, `TENANT_ADMIN`, `MLRO`, `ANALYST`.

**`app_user`** — platform users.
| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` PK | |
| `tenant_id` | `UUID` NULL → `tenant(id)` | NULL for SUPER_ADMIN |
| `email` | `VARCHAR(255)` NOT NULL UNIQUE | globally unique |
| `password_hash` | `VARCHAR(255)` NOT NULL | BCrypt |
| `first_name`, `last_name` | `VARCHAR(100)` NOT NULL | |
| `status` | `VARCHAR(32)` DEFAULT `'ACTIVE'` | ACTIVE / DISABLED |
| `created_at`, `updated_at`, `last_login_at` | `TIMESTAMPTZ` | |

Index `idx_app_user_tenant`.

**`user_role`** — m:n bridge.
`user_id UUID → app_user(id) ON DELETE CASCADE`, `role_id SMALLINT → role(id)`,
PK `(user_id, role_id)`.

**`tenant_goaml_config`** — per-tenant goAML B2B config (credentials themselves live in Secrets Manager).
| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` PK | |
| `tenant_id` | `UUID` NOT NULL UNIQUE → `tenant(id)` CASCADE | 1:1 with tenant |
| `jurisdiction_code` | `VARCHAR(8)` NOT NULL → `jurisdiction(code)` | |
| `rentity_id` | `INTEGER` NOT NULL | FIU-assigned RE id |
| `base_url` | `VARCHAR(512)` NOT NULL | this tenant's goAML endpoint |
| `secrets_path` | `VARCHAR(512)` NOT NULL | Secrets Manager path to creds |
| `auth_mode` | `VARCHAR(16)` DEFAULT `'TOKEN'` | TOKEN / BASIC |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | |

**`refresh_token`** — refresh tokens stored hashed.
| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` PK | |
| `user_id` | `UUID` NOT NULL → `app_user(id)` CASCADE | |
| `token_hash` | `VARCHAR(255)` NOT NULL UNIQUE | only the hash is stored |
| `issued_at`, `expires_at` | `TIMESTAMPTZ` | |
| `revoked_at` | `TIMESTAMPTZ` NULL | |

Index `idx_refresh_token_user`.

> ⚠️ **Doc-accuracy note:** the V2 file's header comment mentions `lookup` and `audit_log` as shared
> tables, but **`lookup` is not created anywhere** and **`audit_log` is a tenant-schema table** (below).
> The comment is slightly stale.

---

## 3. Tenant schema (`tenant_<uuid>`)

### `V1__tenant_init.sql` — one table today

**`audit_log`** — per-tenant immutable event log.
| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` PK | |
| `actor_user_id` | `UUID` NULL | nullable for system actions |
| `actor_email` | `VARCHAR(255)` NULL | |
| `action` | `VARCHAR(64)` NOT NULL | e.g. `USER.LOGIN`, `REPORT.SUBMIT` |
| `entity_type` | `VARCHAR(64)` NULL | |
| `entity_id` | `VARCHAR(64)` NULL | |
| `correlation_id` | `VARCHAR(64)` NULL | request/trace correlation |
| `summary` | `TEXT` NULL | before/after or freeform |
| `occurred_at` | `TIMESTAMPTZ` NOT NULL DEFAULT NOW() | |

Indexes `idx_audit_log_action`, `idx_audit_log_occurred_at`.

### `V2__reports.sql` (Phase 7) — `report` + `submission`

**`report`** — a stored DPMSR report. Content is the structured **`input` (JSONB)** + the marshalled goAML
**`report_xml`** snapshot + metadata — the XSD-generated model is the structure authority, so there is no
normalized report tree.
| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` PK | |
| `entity_reference` | `VARCHAR(255)` NOT NULL **UNIQUE** | per-tenant idempotency key |
| `report_code` | `VARCHAR(16)` NOT NULL | `DPMSR` (others later) |
| `rentity_id` | `INTEGER` NOT NULL | from `tenant_goaml_config` |
| `status` | `VARCHAR(16)` NOT NULL DEFAULT `'DRAFT'` | DRAFT/VALID/INVALID/SUBMITTED/ACCEPTED/REJECTED/FAILED |
| `input` | `JSONB` NOT NULL | the create request, persisted verbatim |
| `report_xml` | `TEXT` NULL | marshalled goAML XML snapshot |
| `validation_errors` | `JSONB` NULL | `[{severity,path,code,message}]` |
| `created_by` | `UUID` NULL | author `app_user.id` |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | |

Indexes `idx_report_status`, `idx_report_created_at`.

**`submission`** — one row per submission attempt to the FIU.
| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` PK | |
| `report_id` | `UUID` NOT NULL → `report(id)` CASCADE | |
| `reportkey` | `VARCHAR(128)` NULL | the FIU's handle (poll status with it) |
| `status` | `VARCHAR(16)` NOT NULL DEFAULT `'SUBMITTED'` | SUBMITTED/ACCEPTED/REJECTED/FAILED |
| `errors` | `TEXT` NULL | FIU error body on rejection/failure |
| `submitted_at`, `updated_at` | `TIMESTAMPTZ` | |

Index `idx_submission_report`.

### `V3__attachments.sql` (Phase 8) — `attachment`

**`attachment`** — one supporting document on a report. **Metadata only** — the bytes live in S3
(`goaml.aws.s3.bucket`) under a per-tenant/per-report key prefix; this row records where + what.
| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` PK | also the `{id}` segment of the S3 key |
| `report_id` | `UUID` NOT NULL → `report(id)` **CASCADE** | delete a report → its attachments go too |
| `filename` | `VARCHAR(255)` NOT NULL | original upload name (also the ZIP entry name) |
| `content_type` | `VARCHAR(255)` NULL | declared MIME type |
| `size_bytes` | `BIGINT` NOT NULL | ≤ 5 MB/file (`PackagingLimits.UAE_DEFAULT`) |
| `s3_key` | `VARCHAR(1024)` NOT NULL | `tenants/{tenantId}/reports/{reportId}/{id}-{filename}` |
| `uploaded_by` | `UUID` NULL | uploader `app_user.id` |
| `created_at` | `TIMESTAMPTZ` | |

Index `idx_attachment_report`.

> Later phases add `notification` (10), `import_job` (11).

---

## 4. JPA entities

Entities live in `model/entity/<feature>/` and carry **no `Entity` suffix**; Lombok `@Getter` (etc.)
replaces hand-written accessors. Key mapping facts:

| Entity (class) | Package | `@Table` | Id | Notable |
|--------|---------|----------|-----|---------|
| `AppUser` | `model/entity/appuser/` | `app_user`, **schema=public** | `UUID` (assigned) | `@ManyToMany(EAGER)` to `Role` via `user_role`; `@PrePersist/@PreUpdate` timestamps; `addRole(...)` helper |
| `Jurisdiction` | `model/entity/jurisdiction/` | `jurisdiction`, schema=public | `String code` | read-only |
| `Role` | `model/entity/role/` | `role`, schema=public | `Short id` | read-only; `findByName` |
| `Tenant` | `model/entity/tenant/` | `tenant`, schema=public | `UUID` | `schemaName` is the routing key |
| `AuditLog` | `model/entity/audit/` | `audit_log`, **no schema** | `UUID` | resolves via search_path (tenant-scoped) |
| `TenantGoamlConfig` | `model/entity/goamlconfig/` | `tenant_goaml_config`, schema=public | `UUID` | read-mostly; `findByTenantId`; resolves the tenant's B2B config |
| `Report` | `model/entity/report/` | `report`, **no schema** | `UUID` | tenant-scoped; JSONB `input`/`validation_errors` via `@JdbcTypeCode(SqlTypes.JSON)`; **distinct from the JAXB `domain.generated.Report`** |
| `Submission` | `model/entity/submission/` | `submission`, **no schema** | `UUID` | tenant-scoped; FK → `report` |
| `Attachment` | `model/entity/attachment/` | `attachment`, **no schema** | `UUID` | tenant-scoped; metadata + `s3_key` only (bytes in S3); **distinct from the engine value record `engine.packaging.Attachment`** |

**No JPA enums** — `status`, role names, `auth_mode` are all plain `String`s (allowed values documented
in SQL comments only). Money/dates follow the project convention (`BigDecimal` / `OffsetDateTime`) where
they appear.

### Repositories (Spring Data JPA, in `repository/<feature>/`)
- `AppUserRepository` — `findByEmail`, `existsByEmail`.
- `JurisdictionRepository` — (no custom methods).
- `RoleRepository` — `findByName`.
- `TenantRepository` — `findBySlug`, `existsBySlug`.
- `AuditLogRepository` — (no custom methods). ⚠️ Every call needs a tenant bound to `TenantContext` or
  it routes to `public` and fails.
- `ReportRepository` — `findByEntityReference`, `existsByEntityReference` (tenant-scoped).
- `SubmissionRepository` — `findByReportIdOrderBySubmittedAtDesc` (tenant-scoped).
- `AttachmentRepository` — `findByReportIdOrderByCreatedAt`, `findByIdAndReportId` (scopes a lookup to its
  parent report); tenant-scoped.
- `TenantGoamlConfigRepository` — `findByTenantId` (shared `public` schema).

> Repositories are accessed only through services (`service/<feature>/`), never injected straight into
> controllers. Entity↔DTO conversion uses MapStruct mappers in `model/mapper/<feature>/` (today:
> `TenantMapper`).

---

## 5. Why `ddl-auto: none`

Hibernate schema validation is **off** on purpose. A startup `validate` would check entities against the
`public` schema — but tenant entities (`AuditLog`) have **no** table in `public` (they live in
`tenant_<id>` schemas), so validation would fail. Flyway is the single source of truth for schema; JPA
never creates or validates it.

---

**Next:** [08 — Testing](08-testing.md).
