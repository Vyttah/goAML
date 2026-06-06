# Phase 13.10 — Admin UI

> **Status: ✅ DONE (2026-06-07).**
> Part of [../plans/phase-13-react-frontend.md](../plans/phase-13-react-frontend.md). Tenth step of Phase 13
> — the last feature step. The admin area over the 13.2 admin API.

---

## 1. Goal & why
Make the platform administrable from the UI: SUPER_ADMIN provisions/lists tenants; TENANT_ADMIN manages
users and the tenant's goAML B2B config — each scoped exactly as the backend enforces.

## 2. What was built (`frontend/`)

| File | Role |
|---|---|
| `types/index.ts` | +`TenantView`, `TenantProvisioningRequest`, `CreateUserRequest`, `UserView`, `GoamlConfigRequest`, `GoamlConfigView`. |
| `api/admin.ts` | `listTenants`/`provisionTenant`; `listUsers`/`createUser`; `getGoamlConfig` (404 → null) / `upsertGoamlConfig`. |
| `features/admin/useAdmin.ts` | Queries + mutations with cache invalidation per area. |
| `features/admin/TenantsPanel.tsx` | SUPER_ADMIN: tenant table + provision modal (slug/name/jurisdiction + initial admin). |
| `features/admin/UsersPanel.tsx` | TENANT_ADMIN: user table (roles/status) + add-user modal (role ∈ ANALYST/MLRO/TENANT_ADMIN). |
| `features/admin/GoamlConfigPanel.tsx` | TENANT_ADMIN: view/edit the single goAML B2B config row (jurisdiction, rentityId, baseUrl, secretsPath, authMode). |
| `features/admin/AdminPage.tsx` | Role-branched: SUPER_ADMIN → tenants; TENANT_ADMIN → users + config. (Replaces the 13.3 placeholder.) |

## 3. Key understanding / decisions
- **Role-branched, not tab-gated** — `AdminPage` renders `TenantsPanel` for SUPER_ADMIN and
  `UsersPanel` + `GoamlConfigPanel` for TENANT_ADMIN (the route is already `RequireRole` for those two).
  A SUPER_ADMIN has no tenant, so user/config panels (which act on `principal.tenantId`) simply don't apply
  to them — matching the backend split exactly.
- **Tenant scoping is implicit** — user/config endpoints derive the tenant from the JWT principal
  server-side, so the UI sends no tenant id; a tenant admin can only ever see/touch their own.
- **`getGoamlConfig` 404 → null** — a tenant with no config yet isn't an error; the panel shows an info
  banner + an empty form (defaults jurisdiction AE / authMode TOKEN) and the PUT upserts.
- **secretsPath is a reference, not a secret** — labelled/tooltipped as the AWS Secrets Manager path; the
  actual FIU credentials never touch the UI (backend never returns them).
- **Assignable roles gated** to ANALYST/MLRO/TENANT_ADMIN (never SUPER_ADMIN), mirroring `AdminService`.

## 4. Tests (Vitest + RTL + MSW)
`AdminPage.test.tsx` (4): SUPER_ADMIN lists tenants + provisions one (stateful list update); TENANT_ADMIN
lists users + sees "no config yet"; creates a user (captured POST asserts `email`+`role`; AntD Select
driven via `fireEvent.mouseDown` + option click); saves the goAML config (PUT → "Configuration saved").
Suite now **58 passing** (17 files).

## 5. Verification
`npm run typecheck`, `npm run lint` (0 warnings), `npm test` (**58 passing**), `npm run build` (emits
`dist/`) — all green on Node 18.16. `git status` scoped to `frontend/`.

---

## Outcome
✅ Admin is live: tenant provisioning (SUPER_ADMIN), user + goAML-config management (TENANT_ADMIN). **All
feature areas of the SPA are now built.** Next: **13.11** — docs/planning sync (frontend README, docs,
ROADMAP/STATE/CLAUDE), then merge `phase-13/frontend` → main.
