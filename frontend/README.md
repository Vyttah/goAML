# goAML frontend (`frontend/`)

The goAML platform's web UI — a **React + TypeScript + Vite + Ant Design** single-page app that drives the
whole report lifecycle over the existing REST API. Built in **Phase 13** (see
[`../.planning/plans/phase-13-react-frontend.md`](../.planning/plans/phase-13-react-frontend.md) and the
per-step docs `../.planning/steps/PHASE-13.*`).

## Stack

React 18 · TypeScript 5.5 · Vite 5 · Ant Design 5 · TanStack Query 5 · Zod 3 · React Router 6 · axios ·
Vitest 2 + React Testing Library + MSW · ESLint 8 + Prettier. Pinned to **Node 18-compatible** majors
(this repo's toolchain is Node 18.16 via nvm).

## Prerequisites

- Node ≥ 18.16 + npm (the repo uses Node 18.16 / npm 9.5 via nvm).
- The backend running on `:8080` (the dev server proxies `/api` + `/actuator` to it).

## Quick start

```bash
# 1) backend with the dev seeder ON (creates a demo tenant + logins) — from the repo root
docker compose up -d postgres
GOAML_DEV_SEED=true ./gradlew bootRun

# 2) the SPA
cd frontend
npm install
npm run dev          # http://localhost:5173 (proxies /api → :8080)
```

**Local login credentials** (seeded by `config/dev/DevDataSeeder`, all password `Passw0rd!`):

| Email | Role | Can |
|-------|------|-----|
| `mlro@demo.local` | MLRO | build + **submit** reports, attachments, import |
| `analyst@demo.local` | ANALYST | build reports, import |
| `admin@demo.local` | TENANT_ADMIN | users + goAML config, view reports/imports |
| `superadmin@goaml.local` | SUPER_ADMIN | provision tenants (no tenant data) |

> The seeder is **off by default** and never runs unless `goaml.dev.seed.enabled=true`
> (`GOAML_DEV_SEED=true`). Never enable it in a deployed environment.

## Scripts

| Command | What |
|---------|------|
| `npm run dev` | Vite dev server (HMR) + API proxy |
| `npm run build` | `tsc --noEmit` typecheck → `vite build` → `dist/` |
| `npm test` | Vitest (jsdom) — the gated logic/flow specs |
| `npm run typecheck` | `tsc --noEmit` |
| `npm run lint` | ESLint (`--max-warnings=0`) |
| `npm run format` | Prettier write |

## Layout

```
src/
  api/         typed axios client (Bearer + 401→login) + per-area endpoints
  auth/        JWT-claims identity, token store, roles, AuthContext/useAuth
  routes/      RequireAuth + RequireRole guards + the route table
  components/   shared UI: AppShell, StatusTag, ValidationMessages, forms/, lookups/, notifications/
  features/<area>/   one folder per area: dashboard, reports, imports, notifications, lookups, admin
  lib/         config, Zod DPMSR schema, form normalization
  types/       TS mirrors of the backend DTOs
  test/        Vitest setup, MSW server, render helper, token factory
```

## How it talks to the backend

- **Auth:** `POST /api/v1/auth/login` → a JWT held in `localStorage` + memory. There is **no `/me`** —
  identity (`sub/email/tenant/schema/roles`) is decoded from the token's claims. A request interceptor
  attaches `Authorization: Bearer …`; a 401 clears the token and routes to `/login` (no refresh token).
- **Serving:** dev uses Vite's proxy (same-origin, no CORS). In prod the built `dist/` is served by the
  Spring jar with an SPA fallback (the **Gradle node task** that wires `dist/` → `static/` lands in
  **Phase 14**). A narrow env-gated CORS bean (`goaml.web.allowed-origins`) covers the dev-SPA-→-remote case.
- **RBAC:** route guards (`RequireRole`) shape navigation; the backend independently enforces RBAC on every
  request, so guards are UX only.

## Testing

Vitest + RTL + MSW. We gate the **logic + key flows** (api client/interceptor, auth + JWT, route guards,
the DPMSR Zod schema + payload builder, and each feature's primary flow) rather than presentational markup.
`tsc --noEmit` + ESLint are also gates. Run `npm test`.

## Known backend gaps surfaced in the UI (not faked)

These have no endpoint yet and are flagged in the UI as future backend adds:

- **Report XML preview** and **re-fetching an existing report's validation result** (validation messages
  only come back at create time).
- **Attachment download** (upload/list/remove exist; no GET-bytes endpoint).

## Notes

- **DPMSR-only builder** — the create endpoint takes `DpmsrCreateRequest`; other report types aren't wired
  server-side yet. The single report type is surfaced clearly in the UI.
- **Lookups are placeholder code-only seeds** — only country/currency are dropdowns; other coded fields are
  free text/tags until the authoritative UAE lookup exports land.
