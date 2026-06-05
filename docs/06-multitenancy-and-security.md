# 06 — Multi-Tenancy & Security

> How one running app serves many isolated client REs, and how users authenticate & are authorized.
> Packages: `com.vyttah.goaml.config.tenant`, `.security`, `.service.{auth,tenant,audit}`, `.controller`,
> `.exception`.

---

## 1. The core idea: schema-per-tenant

Each client Reporting Entity (RE) is a **tenant**. Every tenant gets its **own PostgreSQL schema**
named `tenant_<uuid-hex>` (the tenant's UUID with dashes stripped). Tenant-private data lives only in
that schema. Platform-wide data (tenants, users, roles, config) lives in the shared `public` schema.

Hibernate runs in **`SCHEMA` multi-tenancy** mode: on every DB connection, the app issues
`SET search_path TO "<tenant_schema>"`, so a tenant physically cannot see another tenant's tables. One
shared connection pool, switched per request.

```
  HTTP request with JWT
        │
        ▼
  JwtAuthFilter ── reads "schema" claim ──► TenantContext.set(schema)   [ThreadLocal]
        │
        ▼
  ... controller → service → repository (JPA query) ...
        │
        ▼
  Hibernate asks TenantIdentifierResolver → returns TenantContext.get()
        │
        ▼
  SchemaMultiTenantConnectionProvider.getConnection(schema)
        │   └─ SET search_path TO "<schema>"   (then hands the connection to Hibernate)
        ▼
  query runs against the tenant's schema only
        │
  (request ends) JwtAuthFilter finally{} → TenantContext.clear()  + reset search_path on release
```

---

## 2. The four tenant-plumbing classes (`config/tenant/`)

**`TenantContext`** — a `ThreadLocal<String>` holding the current schema name. Static `set/get/clear`.
Set by the auth filter after JWT validation; **cleared in a `finally`** at request end so pooled Tomcat
threads don't leak one tenant's identity into the next request.

**`TenantIdentifierResolver`** (implements Hibernate `CurrentTenantIdentifierResolver`) —
`DEFAULT_TENANT = "public"`. Returns `TenantContext.get()`, or `"public"` if none is set. So untenanted
threads (startup, provisioning, super-admin) route to `public`.

**`SchemaMultiTenantConnectionProvider`** (implements `MultiTenantConnectionProvider`) — wraps the
shared `DataSource`:
- `getConnection(tenant)` → pool connection + `SET search_path TO "<tenant>"` (quoted/escaped).
- `releaseConnection(tenant, conn)` → `SET search_path TO public` **then** close (resets before
  returning to the pool).

**`MultiTenancyHibernateConfig`** (implements `HibernatePropertiesCustomizer`) — injects the two beans
above into Hibernate. Setting `hibernate.multiTenancy=SCHEMA` in YAML alone is **not enough**; Hibernate
needs the actual bean instances, and this customizer is the supported hook.

**How tables resolve:** shared entities are `@Table(schema = "public")` so they always hit `public`
regardless of search_path. Tenant entities (e.g. `AuditLog` — entities carry **no `Entity` suffix**) use
`@Table(name = "audit_log")` with **no schema**, so they resolve via the current search_path. ⚠️ Querying a
tenant entity with **no tenant bound** routes to `public.audit_log`, which doesn't exist → it errors
*by design*.

---

## 3. Tenant provisioning (`service/tenant/TenantProvisioningService`)

Creating a new tenant is a multi-step, partly-non-transactional operation. Input is
`TenantProvisioningRequest` (record): `slug, name (displayName), jurisdictionCode, adminEmail,
adminPassword, adminFirstName, adminLastName`.

`provision(request)` steps:
1. **Validate** — slug non-blank; jurisdiction exists; slug not already used; admin email not already
   used. (Throws `IllegalArgumentException` / `IllegalStateException` otherwise.)
2. Generate `UUID id`; compute `schemaName = "tenant_" + id.hex` (dashes stripped).
3. **`CREATE SCHEMA "<schemaName>"`** (raw JDBC).
4. **Run tenant Flyway** programmatically against that schema (`locations: db/migration/tenant`,
   `.schemas(schemaName)`) → creates `audit_log` + indexes inside it.
5. **Persist** (one `REQUIRES_NEW` transaction): save the `tenant` row (status `ACTIVE`); look up the
   `TENANT_ADMIN` role; create the admin `app_user` with a **BCrypt-hashed** password and that role.
6. **On failure after `CREATE SCHEMA`:** best-effort `DROP SCHEMA IF EXISTS ... CASCADE`, then rethrow.

> ⚠️ **Gotcha:** `CREATE SCHEMA` and Flyway `migrate()` run **outside** the Spring transaction (separate
> DDL/connections), so there's no automatic rollback of the schema itself — the manual
> `dropSchemaSafely` compensation is what cleans up an orphaned schema. The two inserts share one
> transaction and roll back together.

---

## 4. Authentication (JWT)

### Login
`POST /api/v1/auth/login` — a **thin** `controller/auth/AuthController` that delegates to
`service/auth/AuthService` (`DefaultAuthService`); controllers never inject repositories directly.
- **Request** (`LoginRequest` record): `{ "email": "...", "password": "..." }` (bean-validated).
- **Flow** (in `DefaultAuthService`): `AuthenticationManager.authenticate(...)` → on any failure, rethrow as a uniform
  `BadCredentialsException("Invalid email or password")` (avoids user enumeration) → load the user →
  resolve their schema (null tenantId → `"public"`; else the tenant's `schema_name`) →
  `jwtService.issueAccessToken(user, schema)` → write a `USER.LOGIN` audit row → return the token.
- **Response** (`LoginResponse` record): `{ "accessToken": "<jwt>", "tokenType": "Bearer",
  "expiresInSeconds": <ttl> }`.

### The JWT (`security/JwtService`)
- **Algorithm:** HS256 (HMAC-SHA256), key from `goaml.jwt.secret` (≥ 32 bytes enforced).
- **Claims:** `iss` (issuer), `sub` (user UUID), `iat`, `exp`, `email`, **`tenant`** (tenant UUID, or
  null for SUPER_ADMIN), **`schema`** (the Postgres schema — drives tenant routing), **`roles`**
  (`List<String>`).
  > Note the claim is named **`tenant`** (not `tenant_id`). The **`schema`** claim is what actually
  > routes the DB.
- **Validation** (`parse`): verifies the HS256 signature **and** requires the `iss` claim to match the
  configured issuer; throws `JwtException` on any failure (expired, bad signature, wrong issuer).
- **TTL:** `goaml.jwt.access-token-ttl-minutes` (default 15). Bound via `JwtProperties`
  (`@ConfigurationProperties("goaml.jwt")`).

### The filter (`security/JwtAuthFilter`, extends `OncePerRequestFilter`)
- Reads `Authorization: Bearer <token>`; absent → continue anonymous.
- `jwtService.parse(token)`; on `JwtException`, leave the context empty (→ downstream 401).
- Builds a `UserPrincipal` (no DB hit on authenticated requests — password hash is `""`, active=true).
- Sets the `SecurityContext` (Spring Security 6 pattern: create empty context, set auth, set context).
- **Sets `TenantContext`** from the `schema` claim (or `"public"`).
- **`finally`:** `TenantContext.clear()` + `SecurityContextHolder.clearContext()`.

---

## 5. Authorization (RBAC)

### The four roles (seeded in `public.role`)
| Role | Who | Can |
|------|-----|-----|
| `SUPER_ADMIN` | Vyttah platform admin (cross-tenant; `tenant_id` is null) | everything, incl. tenant management |
| `TENANT_ADMIN` | admin within one tenant | manage that tenant's users + config |
| `MLRO` | Money Laundering Reporting Officer | build/validate **and submit** reports |
| `ANALYST` | analyst | build/validate; **not** submit |

Roles travel in the JWT `roles` claim. `UserPrincipal` maps each role name to a Spring authority
**`ROLE_<name>`** (e.g. `ROLE_SUPER_ADMIN`). `@EnableMethodSecurity` is on, so `@PreAuthorize` works.

> Role-gated endpoints (via `@PreAuthorize`): `/api/v1/admin/ping` → `SUPER_ADMIN`; the **report API**
> (Phase 7) → create/read for `ANALYST`/`MLRO`(/`TENANT_ADMIN` for read), and **submit is `MLRO`-only**.

### `SecurityConfig` (`security/SecurityConfig`)
- CSRF disabled (stateless JWT API). Session policy **STATELESS**.
- **Public paths:** `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`, `/api/v1/auth/**`.
  Everything else: `authenticated()`.
- **401 vs 403:** missing/invalid auth → **401** via `HttpStatusEntryPoint(UNAUTHORIZED)`; authenticated
  but wrong role → **403**.
- `JwtAuthFilter` is added **before** `UsernamePasswordAuthenticationFilter`.
- ⚠️ **Essential gotcha:** there's a `FilterRegistrationBean<JwtAuthFilter>` with `setEnabled(false)`.
  Because `JwtAuthFilter` is a `@Component`, Spring Boot would otherwise auto-register it at the servlet
  level too — running it twice and letting `SecurityContextHolderFilter` blank the auth. Disabling the
  servlet-level registration prevents double-filtering. **Don't remove it.**

---

## 6. The current REST endpoints

| Method | Path | Body | Returns | Auth |
|--------|------|------|---------|------|
| POST | `/api/v1/auth/login` | `LoginRequest{email,password}` | `LoginResponse{accessToken, tokenType, expiresInSeconds}` | public |
| GET | `/api/v1/me` | — | `{userId, tenantId, tenantSchema, email, roles[]}` | any valid JWT |
| GET | `/api/v1/admin/ping` | — | `{ok:true, role:"SUPER_ADMIN"}` | `SUPER_ADMIN` |
| POST | `/api/v1/reports` | `DpmsrCreateRequest` | `201 {reportId, status, validationMessages[]}` | `ANALYST`/`MLRO` |
| GET | `/api/v1/reports` · `/{id}` | — | report view(s) | `ANALYST`/`MLRO`/`TENANT_ADMIN` |
| POST | `/api/v1/reports/{id}/submit` | — | `{submissionId, reportKey, status}` | **`MLRO`** |
| GET | `/api/v1/reports/{id}/status` | — | `{reportKey, status, errors}` | `ANALYST`/`MLRO`/`TENANT_ADMIN` |
| POST | `/api/v1/reports/{id}/attachments` | multipart `file` | `201 AttachmentView` | `ANALYST`/`MLRO` |
| GET | `/api/v1/reports/{id}/attachments` | — | `AttachmentView[]` | `ANALYST`/`MLRO`/`TENANT_ADMIN` |
| DELETE | `/api/v1/reports/{id}/attachments/{attachmentId}` | — | `204` | `ANALYST`/`MLRO` |

> `GET …/status` refreshes on demand; **Phase 9** also refreshes proactively — a `@Scheduled`
> `SubmissionStatusPoller` (`scheduler/`) sweeps every `SUBMITTED` report across ACTIVE tenants and applies
> the same `refreshStatus` transition (ACCEPTED/REJECTED), so a report's outcome lands without a manual poll.
>
> Attachments (Phase 8) are **proxied through the API** (multipart → validated → stored in S3 under a
> per-tenant/per-report key prefix); at **submit** the bytes are pulled from S3 into the ZIP (within
> `PackagingLimits.UAE_DEFAULT`). Add/remove are **frozen once the report is submitted** (→ 409).
>
> The report/attachment API maps service exceptions via `GlobalExceptionHandler`: 404 not-found
> (report/attachment), 409 duplicate-reference / not-submittable / config-missing / report-not-editable,
> 422 FIU-rejection (+ `fiuError`) / packaging-too-large, 502 auth/transport, 400 bad input / rejected
> upload (disallowed extension, oversize).

- **`MeController`** uses `@AuthenticationPrincipal UserPrincipal`, strips the `ROLE_` prefix off roles,
  and returns `TenantContext.get()` as `tenantSchema` (proving routing works).
- **`AdminController.ping`** is a Phase-2 RBAC smoke stub; later phases add real tenant/user/credential
  management here.

### Error mapping (`exception/GlobalExceptionHandler`, `@RestControllerAdvice`)
- `AccessDeniedException` → **403** with body `{status:403, error:"Forbidden", message:...}`.
- `AuthenticationException` is intentionally **not** handled here — left to Spring Security's 401 entry
  point (a JSON 401 body trips some retry-on-401 HTTP clients). **The nuance:** 403s get a JSON body;
  401s do not.

---

## 7. Audit logging (`service/audit/AuditService`)

- Writes immutable rows to the **tenant's** `audit_log` table.
- Uses **programmatic transactions** (`TransactionTemplate`), not `@Transactional` — because
  `TenantContext` must be set *before* Hibernate acquires the connection (when the search_path is set);
  a method-level `@Transactional` would open the connection too early.
- `recordLogin(userId, email, tenantSchema)` → records action `USER.LOGIN`.
- ⚠️ If the schema is null or `"public"` (i.e. a SUPER_ADMIN/platform action), it **skips** writing —
  there's no shared platform-audit table yet (planned). So platform-level actions aren't audited today.
- Sets `id`, `action`, `summary`, `actorUserId`, `actorEmail` (+ `occurredAt` via `@PrePersist`). The
  `entityType` / `entityId` / `correlationId` columns exist but aren't populated yet.

---

## 8. User loading (`security/AppUserDetailsService`, `UserPrincipal`)

- `AppUserDetailsService.loadUserByUsername(email)` loads from `public.app_user` by email (the only
  DB-backed auth path — used at login). Unknown → `UsernameNotFoundException`.
- `UserPrincipal` (implements `UserDetails`): holds `userId`, `tenantId`, `email`, `passwordHash`,
  `active`, authorities. `getUsername()` returns the **email**. All four account-status booleans return
  the single `active` flag (`active` = `"ACTIVE".equals(status)`).
- **Two construction paths:** login (via `AppUserDetailsService`, real hash, DB hit) and per-request
  (via `JwtAuthFilter`, hash `""`, no DB hit).

---

## 9. The gotchas, collected (so they're in one place)

1. **`FilterRegistrationBean.setEnabled(false)` for `JwtAuthFilter`** — prevents double-filtering. Don't
   remove.
2. **`TenantContext` must be cleared** in a `finally` (the filter does this) or threads leak tenant
   identity.
3. **Tenant entities have no `@Table(schema=...)`** — they need a tenant bound or they hit `public` and
   fail.
4. **Schema creation isn't transactional** — provisioning compensates by dropping the schema on failure.
5. **401s have no JSON body; 403s do** — by design, for client compatibility.
6. **The `schema` JWT claim (not `tenant`) drives DB routing.**
7. **Platform/SUPER_ADMIN actions aren't audited yet** (no shared audit table).

---

**Next:** [07 — Persistence & Migrations](07-persistence-and-migrations.md) — the actual tables.
