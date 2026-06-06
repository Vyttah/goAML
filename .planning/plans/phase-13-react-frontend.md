# Phase 13 — React frontend (`frontend/`) + the REST enablers it needs

> **Status: 🔲 PROPOSED (2026-06-06) — awaiting your approval before implementation.**
> Roadmap **Phase 13**. The standalone product's **UI**: a React + TypeScript SPA (Ant Design) that drives
> the whole goAML lifecycle over the existing REST API — login → dashboard → build (DPMSR) → validate →
> detail/submit/track → attachments → import → notifications → lookups → admin. Greenfield `frontend/`
> (no UI exists yet). Includes the **small backend additions the UI requires** (lookup endpoints, an admin
> API, CORS + SPA serving). After this, the product is fully usable by a human; Phase 14 packages/deploys
> it and Phase 12 (plugin/MCP/CLI) is last.

---

## 1. What this phase is, and why

Today goAML is a headless REST API. A compliance officer can't *use* it without curling JSON. Phase 13
adds the **React SPA** — the primary human surface — so an MLRO/analyst logs in, sees their reports, builds
a DPMSR from a guided form (with backend-matched validation), submits (MLRO-gated), watches FIU status,
imports files, and reads notifications. It also fills the **three REST gaps** the UI exposes (lookups, an
admin API, CORS/SPA-serving) so the SPA is data-driven and same-origin-served by the jar.

**Scope (your decisions this session):**
- **Full SPA — all areas** (auth, dashboard, builder, detail/track, attachments, import, notifications,
  lookups browser, admin).
- **Lookups via new read-only REST endpoints** (data-driven dropdowns that always match backend validation).
- **Vitest + React Testing Library**, gating the logic layers (typed API client, Zod schemas, hooks, key
  flow components) + `tsc` typecheck + ESLint; pragmatic on purely presentational components.

**Dictated by the backend (not a choice):** the report **builder is DPMSR-only** — the create endpoint
takes `DpmsrCreateRequest` and only DPMSR is wired. Other report types are a later phase (engine has
builders; no REST/UI yet).

## 2. Stack & conventions (per docs/00)

- **React 18 + TypeScript + Vite**, **Ant Design** (components), **React Router** (routing/guards),
  **TanStack Query** (server state/caching), **Zod** (client validation mirroring backend DTOs),
  **MSW** (API mocking in tests), **Vitest + React Testing Library**, **ESLint + Prettier**.
- Layout: `frontend/src/{api/ (typed client + TanStack hooks), types/ (mirror backend DTOs), auth/, components/, features/<area>/, routes/, lib/}`.
- **Auth:** Bearer JWT (15-min, no refresh) held in memory + `localStorage`; an axios/fetch interceptor
  attaches it and on **401 → redirect to login** (re-login flow, since there's no refresh token).
- **Served same-origin:** Vite **dev proxy** to `:8080` in dev (no CORS needed); in prod the built `dist/`
  is wired into `src/main/resources/static/` and served by Spring with an SPA fallback. A narrow CORS bean
  (env-gated origins) is added for the case where the dev SPA hits a remote backend.

## 3. Decisions

| # | Decision | Choice |
|---|----------|--------|
| D1 | Builder scope | **DPMSR only** (backend reality). Form mirrors `DpmsrCreateRequest` (header + `reportingPerson` + `parties[]` person/entity incl. directors/identifications + `goods[]`). |
| D2 | Lookups | **New read-only `LookupController`** (13.1): `GET /api/v1/lookups/jurisdictions`, `GET /api/v1/lookups/{jurisdiction}/{set}` over `JurisdictionRegistry` + `LookupService` (authenticated, ANALYST+). UI dropdowns + Zod enums fetch from these. |
| D3 | Admin API gap | **There is no admin REST API today** (`AdminController` = `/ping` only). The admin UI needs new endpoints (13.2): tenant provision/list (SUPER_ADMIN), user create/list per tenant (TENANT_ADMIN), `tenant_goaml_config` get/set (TENANT_ADMIN). Built over the existing `TenantProvisioningService` + repos. **(Largest backend addition — carve out at review if you'd rather defer admin.)** |
| D4 | Auth/session | In-memory + `localStorage` JWT; interceptor adds Bearer; **401 → login** (no refresh token exists). Role-aware routing/guards from the `/me` roles. |
| D5 | Serving | Dev: Vite proxy → `:8080`. Prod: `frontend/dist` → `static/` via a **Gradle node task**; Spring serves `index.html` with an SPA fallback for non-API/non-asset routes; `SecurityConfig` permits static assets + the SPA shell; `/api/**` stays authenticated. (The Dockerfile multi-stage build is **Phase 14**.) |
| D6 | Validation | Client-side **Zod** schemas mirror `DpmsrCreateRequest`; on submit-to-create the server `ValidationResult` (errors/warnings by path/code) is rendered inline against the form fields. Single source of truth stays the backend. |
| D7 | Testing | **Vitest + RTL + MSW**; gate api client + Zod schemas + hooks + key flow components; `tsc --noEmit` + ESLint as CI gates. No hard global % on presentational markup. |
| D8 | Attachments | Upload (multipart) + list + remove via existing endpoints. **No download endpoint exists** → list shows metadata only (download is a small future backend add, out of scope). |
| D9 | Backend JaCoCo | New Java (lookup + admin controllers/services) held to the existing **≥90%/≥80%** gate where logic lands. |

## 4. The three REST gaps this phase closes (backend, small)

1. **Lookups/jurisdictions** (D2) — read-only; the builder can't populate valid UAE code dropdowns without it.
2. **Admin API** (D3) — tenant/user/goaml-config CRUD; the admin area has no endpoints today.
3. **CORS + SPA serving** (D5) — so the SPA runs in dev (proxy) and is served same-origin by the jar in prod.

## 5. Step breakdown (one commit per step, each green; per-step doc in `steps/`)

### Backend enablers (Java; existing test + JaCoCo conventions)
- **13.1 — Lookup API + CORS + SPA serving.** `controller/lookup/LookupController` (jurisdictions + a
  lookup set; authenticated) + DTOs; an env-gated CORS bean; a `WebMvcConfigurer`/controller SPA fallback +
  `SecurityConfig` permits for static assets; Gradle node-build wiring (`frontend/dist` → `static/`).
  MockMvc tests for the lookup endpoints + a static-serving/permit smoke test.
- **13.2 — Admin API** (D3). REST over `TenantProvisioningService` + repos: tenant provision/list, user
  create/list (per tenant), `tenant_goaml_config` get/set — each role-gated (SUPER_ADMIN / TENANT_ADMIN) +
  audited. MockMvc + RBAC + Testcontainers tests. *(Carve out at review to defer admin.)*

### Frontend (`frontend/`; Vitest + RTL + tsc + ESLint)
- **13.3 — Scaffold + tooling + api/auth core.** Vite+React+TS+AntD project; ESLint/Prettier; Vitest+RTL+MSW;
  the typed **`api/` client** + **`types/`** mirroring every DTO; auth token store + interceptor (401→login);
  TanStack Query provider; app shell + router + **role guards**. Tests: api client, interceptor, guard.
- **13.4 — Auth + shell.** Login page (email/password → token), `/me` bootstrap, role-aware nav/menu, logout.
- **13.5 — Dashboard.** Report list with status chips (DRAFT/VALID/INVALID/SUBMITTED/ACCEPTED/REJECTED/
  FAILED), filters, FIU-error surfacing, row → detail.
- **13.6 — DPMSR report builder.** Dynamic nested form (header + reportingPerson + parties[] person/entity +
  directors/identifications + goods[]) driven by lookups; **Zod** mirror of `DpmsrCreateRequest`; create →
  render server `ValidationResult` inline. (The largest FE step.)
- **13.7 — Report detail + lifecycle.** XML preview, validation panel, **submit** (MLRO, confirmation
  dialog), submission timeline (reportkey, status, FIU errors via `/status`), attachments (upload/list/remove).
- **13.8 — File import UI.** Upload goAML XML / DPMSR CSV → render `ImportJobView` with the per-row results
  table (status + errors); import history (`GET /imports`).
- **13.9 — Notifications + lookups browser.** Notifications center (bell + unread count, list, mark-read);
  read-only lookups/jurisdictions browser.
- **13.10 — Admin UI.** Tenant management (SUPER_ADMIN), user/role management (TENANT_ADMIN), goAML-config
  editor — over the 13.2 endpoints; role-gated screens.
- **13.11 — Docs/planning sync + README.** `frontend/README`, `docs/02` (frontend ✅) + a new frontend doc;
  `docs/09`/`ROADMAP`/`STATE`/`CLAUDE.md` (Phase 13 ✅, Phase 14 next, 12/14); fill outcome + per-step docs.

## 6. JaCoCo / frontend coverage
- **Backend:** new `controller/lookup/**`, `controller/admin/**` (+ any new admin service) at the existing
  ≥90%/≥80% bar.
- **Frontend:** Vitest coverage focused on `api/`, Zod schemas, hooks, and key flow components; `tsc
  --noEmit` + ESLint must pass. No hard global % on presentational components (D7).

## 7. What this phase does NOT do
Non-DPMSR report builders (backend not wired); attachment **download** (no endpoint); a refresh-token flow
(backend has none — re-login on expiry); the Dockerfile multi-stage + Helm + CI/CD (**Phase 14**); the
plugin/MCP/CLI (**Phase 12, last**); suite integration (**Phase 1.5, deferred**); i18n/theming beyond Ant
Design defaults; mobile-first design (desktop-first compliance tool).

## 8. Verification
- Backend: `./gradlew test jacocoTestCoverageVerification` green (lookup + admin endpoints, RBAC, SPA-serving smoke).
- Frontend: `npm run test` (Vitest), `npm run typecheck` (tsc), `npm run lint` green; `npm run build` produces `dist/`.
- End-to-end (manual, dev proxy): log in → build a DPMSR → see inline validation → (MLRO) submit → watch
  status → import a CSV → see notifications → (admin) provision a tenant/user.
- Prod-serving smoke: `./gradlew bootJar` serves the SPA at `/` with API under `/api/v1`.
- `git status` scoped to `frontend/`, the new backend controllers, and the 13.11 docs.

## 9. Notes / to confirm in-step
- **Admin is the one area with a backend dependency** (13.2). If you'd rather ship the report-lifecycle UI
  first, carve 13.2 + 13.10 into a Phase 13.x follow-up — the rest of the SPA needs no new backend.
- **DPMSR-only builder** is a backend constraint, not a UI limitation — surface it clearly in the UI
  (single report type) so it's not mistaken for missing functionality.
- **No refresh token** — keep the 401→login UX clean (preserve the in-progress form where feasible; warn
  before the 15-min expiry on long edits).
- **Zod ↔ backend drift** — generate/derive `types/` from the documented DTOs and keep one place to update;
  the server `ValidationResult` remains authoritative (client Zod is UX, not the gate).
- **Gradle node task** must not break `./gradlew test` on a machine without Node — gate the FE build behind
  a property/profile so backend-only contributors aren't forced to build the SPA.

---

## Outcome — ✅ DONE (2026-06-07)

Shipped on branch `phase-13/frontend` in 11 gated steps (`8e76a40`…13.11), each with a per-step doc
(`steps/PHASE-13.1..13.11`) and all gates green (`tsc` + ESLint + `vite build`; backend JaCoCo on the new
controllers/services). **58 Vitest specs.**

- **Backend enablers (13.1–13.2):** lookup API + CORS + SPA-serving; admin API (tenants / users /
  goAML-config) over `TenantProvisioningService` + repos.
- **SPA (13.3–13.10):** scaffold + api/auth core → login + role-aware shell → dashboard → **full nested
  DPMSR builder** (Zod mirror + lookup dropdowns + inline server validation; D1 = full structure, chosen
  this session) → report detail (MLRO submit + FIU status + attachments) → import (XML/CSV + per-row
  results) → notifications (bell + center) + reference browser → admin.
- **Docs/merge (13.11):** `frontend/README.md`, `docs/12-frontend.md`, ROADMAP/STATE/CLAUDE/discussion-log
  synced; merge to `main`.
- **Plus** a gated `config/dev/DevDataSeeder` (`goaml.dev.seed.enabled`) so the SPA is reviewable locally.

**Deviations from the plan, recorded:** three panels the plan listed (report **XML preview**, a **validation
panel** for an existing report, **attachment download**) have **no backend endpoint** — surfaced in the UI
as future backend adds rather than faked. Lookups are placeholder **code-only** seeds, so only
country/currency are dropdowns. The **Gradle node task** (dist → static) is deferred to **Phase 14**
(packaging), per the plan's §9 note about not breaking `./gradlew test` on a Node-less machine.

**Next:** Phase 14 (infra) — bundle `frontend/dist` into the jar, Dockerfile, Helm, CI/CD.
