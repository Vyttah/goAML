# 17 — Suite connections, admin management & local-run guide

> **Audience:** operators and developers wiring the AML cockpit (and later the accounting app) to goAML
> via federated SSO, and managing tenants/users from goAML's admin UI.
> **Companion docs:** [14 — Suite integration & federated auth](14-suite-integration.md) (the consumer
> contract), [`.planning/plans/go-live-integration-runbook.md`](../.planning/plans/go-live-integration-runbook.md)
> (production wiring), [06 — Multitenancy & security](06-multitenancy-and-security.md).
>
> This doc is both a **developer reference** (how it works, files, endpoints) and a **step-by-step user
> guide** (how to connect a client and manage users locally). Built 2026-06-12.

---

## 1. The connection model (no second login)

When a user logged into a sibling app (the AML cockpit, later accounting) opens goAML's transaction
feature, they are connected to goAML **without a second login**:

```
AML cockpit user ─┐  (1) customer-service mints a short-lived RS256 "service assertion"
                  │      signed with its PRIVATE key (the user never sees it)
                  ▼
   POST /api/v1/auth/federated/token  { sourceSystem, assertion }   →  goAML
                  │      (2) goAML verifies the assertion against the registered PUBLIC key
                  │      (3) resolves the company → tenant, JIT-provisions the user
                  ▼
        goAML user JWT  →  browser calls goAML directly (lookups, create, submit…)
```

**goAML is the system-of-record for the connection.** Three layers:

| Layer | What | Where it lives | Cadence |
|---|---|---|---|
| **App trust** | RSA keypair per source system | private key in the sibling app's secret store; public key in goAML `public.trusted_service` (`SCREENING`, later `ACCOUNTING`) | once per app |
| **Company → tenant** | which suite company = which goAML tenant | `public.tenant_external_ref` (`source_system, external_org_ref → tenant_id`) | once per onboarded client |
| **User** | which suite user = which goAML user | **automatic** — JIT-provisioned on first exchange (role = `trusted_service.default_role`, null → ANALYST), or pre-mapped in `external_identity` | never configured by hand |

Adding the accounting app later is purely additive: one more `trusted_service` row + its own company links.

---

## 2. The two contract requirements (and the bugs they exposed)

Both surfaced only when the apps were wired live (each repo had passed its own tests in isolation):

1. **The user's role must be in the JWT.** The cross-service auth reads the caller's role from a `roles`
   claim. `user-service`'s `JwtService.generateToken` originally emitted only `userId`+`companyId`, so
   `customer-service` saw `roles = null` and the goAML filing gate (`requireGoamlFilingRole`, audit A5)
   rejected **every** user with 403. **Fix:** `user-service` now adds `roles` + `principalType` to the JWT
   (users must re-login to pick it up).
2. **The service assertion must carry a `jti`.** goAML's `ServiceCredentialValidator` requires a unique id
   for replay protection. `customer-service`'s `GoamlAssertionService.mint` didn't set one → goAML returned
   401 *"Service assertion must carry a unique id (jti)"*. **Fix:** `mint()` now sets
   `.setId(UUID.randomUUID())`.

**Lesson:** the XSD/auth gates guarantee *validity*, not cross-service *contract completeness* — verify the
wired chain end-to-end, not just each side.

---

## 3. Admin surfaces in the goAML SPA

`frontend/src/features/admin/` (REST under `/api/v1/admin/*`, role-gated by `@PreAuthorize`).

### 3.1 SUPER_ADMIN (platform operator)
| Panel | Manages | Endpoints |
|---|---|---|
| **Tenants** | provision a tenant (schema + initial TENANT_ADMIN) | `POST/GET /admin/tenants` |
| **Tenant Users** | pick any tenant → create/edit/disable/delete users + **reset password** | `GET/POST /admin/tenants/{tenantId}/users`, `PUT/DELETE /…/{userId}`, `POST /…/{userId}/reset-password` |
| **Suite Connections** | register a sibling app's public key (trusted service); map company → tenant | `…/admin/trusted-services` (GET/POST/PUT/DELETE), `…/admin/tenant-external-refs` (GET/POST/DELETE) |

### 3.2 TENANT_ADMIN (client admin, scoped to their own tenant)
Users (`/admin/users`), goAML B2B config (`/admin/goaml-config`), reporting persons (`/admin/goaml-persons`).

> **Password note:** federated/JIT users are created with a deliberately **unusable random password**
> (`DefaultFederatedAuthService`), so they can't log into the goAML SPA directly until a SUPER_ADMIN (Tenant
> Users → Reset password) or their TENANT_ADMIN sets one. The cockpit flow never needs it (SSO).

### 3.3 "My goAML connection" (read-only, any role) — surfaced in the sibling app
`GET /api/v1/me/connection` (authenticated, **any tenant role** — no `@PreAuthorize`) returns the caller's
**linked goAML tenant** (id/slug/name/jurisdiction/status), the **reporting-entity id** (`rentity_id`) +
`fiuConfigured` flag, and the **active goAML reporting person** (name/occupation/email/nationality/ID) — or
`null` if none is set. It deliberately excludes secrets (no base URL / secrets path). The **AML cockpit**
calls it with its federated JWT and shows it on a **goAML Connection** settings page
(`components/GoAMLConnectionComponent/`, route `/goaml-connection`, sidebar entry) so a cockpit user can see
which goAML tenant they're filing into and who the active reporting person is — pure display; the connection
itself is still managed in goAML's admin (§3.1). Backed by `controller/connection/ConnectionController` +
`service/connection/DefaultConnectionService` + `model/dto/connection/ConnectionViews`.

---

## 4. Operator user guide — connect a client & manage users

### 4.1 One-time: register the sibling app's trust
goAML SPA → log in as SUPER_ADMIN → **Administration → Suite Connections → Trusted services → Register**:
- Source system `SCREENING` (the AML app) · paste its **public** PEM · JIT on · default role `ANALYST`.
- (Accounting later = a second `ACCOUNTING` row, same screen.)

### 4.2 Per client: map the company to a tenant
**Suite Connections → Company links → Add link**: source `SCREENING`, **company id** = the id the sibling app
sends (the AML `companyId`), **tenant** = the goAML tenant. Effective immediately (read per request).

### 4.3 Create/manage the client's team
**Tenant Users** → pick the tenant → **Add user** (email, password, role ANALYST/MLRO/TENANT_ADMIN). For
cockpit (SSO) clients, users auto-appear as ANALYST on first use — just promote the **MLRO** (FIU submit
authority). **Reset password** gives any user (incl. a federated one) a direct goAML login.

### 4.4 Test the cockpit → goAML flow
1. Map the cockpit user's company (4.2). 2. In the cockpit, **log out and back in** (fresh token with the
`roles` claim — step 2 of §2 fix). 3. Open Create Transaction → the goAML lookups (Item type / Status)
populate over the token; the `token` call is 200; submit files the DPMSR. No second login.

### 4.5 Troubleshooting
| Symptom | Cause | Fix |
|---|---|---|
| `token` 403 *"compliance role required"* | cockpit token predates the user-service roles fix | log out/in for a fresh token; ensure the user's role ∈ `goaml.integration.filing-roles` |
| `token` 500 *"exchange failed"* | company not mapped to a tenant | add the company link (4.2) for the exact `companyId` the token carries |
| goAML 401 *"must carry a unique id (jti)"* | assertion minted without jti | ensure `customer-service` has the `jti` fix deployed |
| empty goAML dropdowns | no goAML token (same as 403/500 above) | fix the token first; they load over it |
| can't log a cockpit user into goAML SPA | federated users have an unusable password | Tenant Users → Reset password |

---

## 5. Local-dev run topology

| Port | Service | Launch | Notes |
|---|---|---|---|
| 8080 | AML user-service | `mvn -pl user-service spring-boot:run` | local Postgres `localhost:5432/VyttahAML` (postgres/password), shared JWT secret |
| 8081 | AML customer-service | `mvn -pl customer-service spring-boot:run` + `GOAML_INTEGRATION_BASE_URL=http://localhost:8090`, `…_SOURCE=SCREENING`, `…_PRIVATE_KEY_PEM=<pkcs8>`, `…_FILING_ROLES=USER,MLRO,…` (USER widened for dev) | hosts the `/api/v1/goaml/token` bridge |
| 8082 | AML admin-service | `mvn -pl admin-service spring-boot:run` | |
| 8090 | goAML | `SERVER_PORT=8090 SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5544/goaml … GOAML_AUTH_MODE=both GOAML_JWT_SECRET=<≥256-bit> GOAML_ALLOWED_ORIGINS=http://localhost:3001 ./gradlew bootRun` | `auth.mode=both` enables the federated endpoint; boot guard rejects the committed default JWT secret |
| 3001 | AML cockpit (Next.js) | `next dev -p 3001` | `NEXT_PUBLIC_API_GOAML_SERVICE_URL=:8090` |
| 5173+ | goAML SPA (Vite) | `VITE_BACKEND_URL=http://localhost:8090 npm run dev` | proxies `/api`+`/actuator` to :8090 (same-origin, no CORS) |

Infra (docker): Postgres `goaml-postgres` host port **5544** (goaml/goaml; goAML DB) and the AML local
Postgres on **5432** (VyttahAML); Redis 6379. goAML AML services run on **config defaults** (no `.env`).
Dev SUPER_ADMIN: `superadmin@goaml.local` / `Passw0rd!`; demo-tenant users `admin@`/`mlro@`/`analyst@demo.local`
/ `Passw0rd!`.

---

## 6. Key files

**goAML backend:** `controller/admin/AdminController`, `service/admin/{AdminService,DefaultAdminService,AdminExceptions}`,
`model/dto/admin/AdminViews`, `model/entity/federated/{TrustedService,TenantExternalRef,SourceSystem}`,
`repository/federated/*`, `service/auth/DefaultFederatedAuthService`, `security/ServiceCredentialValidator`,
`model/entity/appuser/AppUser` (`changePassword`).
**goAML SPA:** `features/admin/{AdminPage,TenantsPanel,TenantUsersPanel,SuiteConnectionsPanel,UsersPanel,useAdmin}`,
`api/admin.ts`, `types/index.ts`.
**AML:** `user-service/security/JwtService` + `service/AuthService` (roles claim),
`customer-service/integration/goaml/{GoamlAssertionService(jti),GoamlScreeningClient}`,
`customer-service/controllers/GoamlTokenController`, `security/{CurrentUserService,JwtAuthenticationFilter}`.
