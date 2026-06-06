# 12 — Frontend (the React SPA)

> Built in **Phase 13**. The platform's primary human surface: a single-page app that drives the whole
> goAML lifecycle over the existing REST API. Lives in [`../frontend/`](../frontend) — see
> [`../frontend/README.md`](../frontend/README.md) for the hands-on dev guide; this doc is the architectural
> overview.

## What it is

A **React 18 + TypeScript + Vite + Ant Design** SPA. It consumes the REST API only — no business logic
lives in the client; the backend stays authoritative for validation and RBAC. Server state is cached with
**TanStack Query**; client-side form validation mirrors the backend with **Zod** (UX only).

## The areas (one `features/<area>/` each)

| Area | Route | What |
|------|-------|------|
| Auth | `/login` | email/password → JWT; identity from token claims |
| Dashboard | `/dashboard` | report list, status chips, filter/search, row → detail |
| Builder | `/reports/new` | full nested DPMSR form (ANALYST/MLRO) → create + inline server validation |
| Detail | `/reports/:id` | summary, **MLRO submit** (VALID-only, confirmed), on-demand FIU status, attachments |
| Import | `/imports` | upload goAML XML / DPMSR CSV → per-row results + history |
| Notifications | bell + `/notifications` | unread badge, list, mark-read, open report |
| Reference | `/reference` | read-only jurisdiction + lookup-code browser |
| Admin | `/admin` | tenants (SUPER_ADMIN) · users + goAML config (TENANT_ADMIN) |

## How it talks to the backend

- **Identity without `/me`** — the only auth endpoint is `POST /api/v1/auth/login`, returning a JWT. The SPA
  decodes the token's claims (`sub/email/tenant/schema/roles`) for identity; there is no profile endpoint
  and no refresh token, so a **401 clears the token and redirects to `/login`**. A request interceptor
  attaches `Authorization: Bearer …`.
- **RBAC is UX + server** — `RequireAuth`/`RequireRole` guards shape navigation; the backend enforces RBAC
  independently on every request. Roles in the token are bare names (`SUPER_ADMIN`/`TENANT_ADMIN`/`MLRO`/
  `ANALYST`).
- **Serving** — dev uses Vite's proxy (`/api`, `/actuator` → `:8080`, same-origin, no CORS). In prod the
  built `dist/` is served by the Spring jar with an SPA fallback (`SpaWebConfig`); a narrow env-gated CORS
  bean (`goaml.web.allowed-origins`) covers the dev-SPA-→-remote-backend case. The Gradle node task that
  bundles `dist/` into the jar's `static/` is **Phase 14**.

## REST enablers added for the SPA (backend, Phase 13)

- `controller/lookup/` — jurisdictions + lookup sets (authenticated reference data).
- `controller/admin/` + `service/admin/` — tenant provision/list (SUPER_ADMIN); user + goAML-config
  (TENANT_ADMIN, scoped to the caller's tenant). Held to the existing JaCoCo gate.
- `config/web/` — CORS bean + SPA-serving `WebMvcConfigurer`; `SecurityConfig` permits the SPA shell/assets
  while keeping `/api/**` authenticated.

## Local run

```bash
GOAML_DEV_SEED=true ./gradlew bootRun     # seeds a demo tenant + logins (password Passw0rd!)
cd frontend && npm install && npm run dev  # http://localhost:5173
```

`config/dev/DevDataSeeder` (gated by `goaml.dev.seed.enabled`, off by default — **never enable in prod**)
seeds `superadmin@goaml.local`, `admin@demo.local`, `mlro@demo.local`, `analyst@demo.local`.

## Testing

**Vitest + React Testing Library + MSW**, gating the logic + key flows (api client/interceptor, JWT +
guards, the DPMSR Zod schema + payload builder, and each feature's primary flow) rather than presentational
markup; `tsc --noEmit` + ESLint are also gates. 58 specs as of Phase 13 close.

## ⚠️ Known backend gaps (surfaced in the UI, not faked)

No endpoint exists yet for: **report XML preview**, **re-fetching an existing report's validation result**
(messages return only at create time), and **attachment download**. These are flagged in the UI as small
future backend adds. Also: the builder is **DPMSR-only** (only that type is wired server-side), and lookups
are **placeholder code-only seeds** (only country/currency are dropdowns).
