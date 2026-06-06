# Phase 13.2 — Admin REST API (tenant / user / goAML-config)

> **Status: ✅ DONE (2026-06-06).**
> Part of [../plans/phase-13-react-frontend.md](../plans/phase-13-react-frontend.md). Second step of Phase 13
> — the admin backend the admin UI (13.10) needs. Research found there was **no admin CRUD API** before this
> (`AdminController` = `/ping` only).

---

## 1. Goal & why
The full SPA includes an admin area, but the backend exposed no tenant/user/config management over REST.
13.2 adds it: platform-level tenant provisioning/listing (SUPER_ADMIN) and tenant-scoped user management +
goAML-config (TENANT_ADMIN, over the caller's own tenant).

## 2. What was built

| File | Role |
|---|---|
| `service/admin/AdminService` + `DefaultAdminService` | provisionTenant (reuses `TenantProvisioningService`) + listTenants; createUser (role-validated, password-encoded) + listUsers; getGoamlConfig + upsertGoamlConfig. Writes audited. |
| `service/admin/AdminExceptions` | `UserEmailExistsException` (409), `GoamlConfigNotFoundException` (404). |
| `model/dto/admin/AdminViews` | `TenantView`, `CreateUserRequest`, `UserView`, `GoamlConfigRequest`, `GoamlConfigView` (+ `from(...)`). |
| `controller/admin/AdminController` | `POST/GET /api/v1/admin/tenants` (SUPER_ADMIN); `POST/GET /api/v1/admin/users` + `GET/PUT /api/v1/admin/goaml-config` (TENANT_ADMIN, scoped to `principal.tenantId`). Kept `/ping`. |
| `model/entity/.../TenantGoamlConfig` | Made **writable**: public ctor `(id, tenantId)` + `@Setter` on config fields + `@PrePersist/@PreUpdate` (was read-only). |
| `repository/appuser/AppUserRepository` | `+ findByTenantId(UUID)` (list a tenant's users). |
| `exception/GlobalExceptionHandler` | `UserEmailExists` → 409, `GoamlConfigNotFound` → 404. |
| `build.gradle` | `service/admin/**` + `controller/admin/**` added to the JaCoCo gate. |

## 3. Key understanding / decisions
- **Role split matches the model:** tenant lifecycle = **SUPER_ADMIN**; user + config = **TENANT_ADMIN**
  over **their own tenant only** (`principal.getTenantId()`), so a tenant admin can never touch another
  tenant's users/config. (Cross-tenant user admin by SUPER_ADMIN is out of scope — the provisioned tenant
  gets its first TENANT_ADMIN, who then self-manages.)
- **Reuse, don't fork:** `provisionTenant` delegates to the existing `TenantProvisioningService` (schema +
  initial admin + per-tenant Flyway).
- **Assignable roles are gated** to `{ANALYST, MLRO, TENANT_ADMIN}` — never `SUPER_ADMIN` (platform role);
  unknown/SUPER_ADMIN → 400.
- **`secretsPath`/`baseUrl` are references, not credentials** (the secret stays in Secrets Manager), so the
  config view safely echoes them.
- **`TenantGoamlConfig` became writable** — previously read-only (rows were SQL-seeded in tests); the upsert
  needs a ctor + setters + timestamp callbacks. Verified the submit path (which reads it) still green.

## 4. Tests
- **`DefaultAdminServiceTest`** (6, mocked): createUser encodes + assigns role (case-insensitive); duplicate
  email → 409; SUPER_ADMIN/unknown role → 400 (no save); getGoamlConfig absent → 404; upsert updates the
  existing row / creates when absent.
- **`AdminApiE2ETest`** (MockMvc + Testcontainers): SUPER_ADMIN provisions + lists a tenant; the provisioned
  TENANT_ADMIN creates + lists users, dup email → 409, upserts + reads goAML config; an ANALYST is 403 on
  both SUPER_ADMIN and TENANT_ADMIN endpoints.

## 5. Verification
`./gradlew test jacocoTestCoverageVerification` → **BUILD SUCCESSFUL** (full suite + gate). `git status`
scoped to Phase 13.2 files.

---

## Outcome
✅ The admin API is in place — every SPA area now has a backend. Next: **13.3** — the React scaffold +
tooling + typed API client + auth core (the frontend toolchain begins).
