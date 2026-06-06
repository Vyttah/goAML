# Phase 13.1 — Lookup API + CORS + SPA serving (backend enablers)

> **Status: ✅ DONE (2026-06-06).**
> Part of [../plans/phase-13-react-frontend.md](../plans/phase-13-react-frontend.md). First step of Phase 13
> — the backend gaps the SPA needs, before any React code.

---

## 1. Goal & why
The SPA needs three things the API didn't have: **lookup/jurisdiction reference data** (for builder
dropdowns + client validation that matches the backend), **CORS** (for a dev SPA hitting a remote backend),
and **SPA serving** (same-origin static + deep-link fallback). All backend; no frontend yet.

## 2. What was built

| File | Role |
|---|---|
| `controller/lookup/LookupController` | Read-only, `isAuthenticated()`: `GET /api/v1/lookups/jurisdictions`, `/{jurisdiction}` (set names), `/{jurisdiction}/{set}` (codes). |
| `model/dto/lookup/LookupViews` | `JurisdictionView` / `LookupSetsView` / `LookupSetView`. |
| `controller/lookup/LookupExceptions` | `LookupNotFoundException` (404) — unknown jurisdiction/set; wired into `GlobalExceptionHandler`. |
| `engine/lookup/LookupService` | `+ setNames(jurisdiction)` (list a jurisdiction's loaded set names). |
| `config/web/SpaWebConfig` | `WebMvcConfigurer` resource handler over `classpath:/static/` with a **client-side-routing fallback** (real file → served; non-API/actuator unmatched path → `index.html`). |
| `config/web/CorsConfig` | Env-gated `CorsConfigurationSource` for `/api/**` (`goaml.web.allowed-origins`, blank = off). |
| `security/SecurityConfig` | `+ .cors(...)`; authorization restructured → `/api/**` **authenticated**, everything else (SPA shell + assets) **public**. |
| `resources/static/index.html` | Placeholder SPA shell (the Vite build replaces it). |
| `application.yml` | `goaml.web.allowed-origins` (blank default). |
| `build.gradle` | `controller/lookup/**` added to the JaCoCo gate. |

## 3. Key understanding / decisions
- **Lookups are `isAuthenticated()` reference data** (not tenant data) — any role; the controller doesn't
  touch the DB, so it's tenant-independent.
- **Security model flipped from "everything authenticated" to "`/api/**` authenticated, rest public".**
  The SPA shell + assets must load for an unauthenticated visitor (the app then calls
  `/api/v1/auth/login`); the API stays locked. `/actuator/health|info|prometheus` + `/api/v1/auth/**`
  remain explicitly public. **Verified the full suite (auth flow + RBAC + all E2E) still green** — this is
  the high-blast-radius change.
- **SPA fallback via a `PathResourceResolver`** (the canonical Spring Boot SPA pattern): real static files
  served as-is; unmatched non-API/actuator paths return `index.html` so deep links (`/reports/123`) load
  the shell; `api/`+`actuator/` return `null` so they 404 through their own handlers, never the shell.
- **Root `""` special-cased** to `index.html` (else `createRelative("")` resolves the static *directory* →
  empty body).
- **CORS is off by default** (blank origins → no config registered) → prod same-origin is unaffected; only
  a dev SPA on another origin sets `goaml.web.allowed-origins`.
- **The Gradle node build wiring (`frontend/dist` → `static/`) is deferred to 13.3** (when `frontend/`
  exists) — wiring a build for a project that isn't there yet would be empty.

## 4. Tests
`LookupApiTest` (8, MockMvc + Testcontainers, JWT minted via `JwtService` for a synthetic user — no DB user
needed): jurisdictions lists UAE + DPMSR; set names for `ae`; codes for `countries`; unknown set/jurisdiction
→ 404; lookups require auth (401); SPA deep links (`/dashboard`, `/reports/123`) serve the shell publicly;
`/api/v1/me` stays 401 (never falls through to the SPA). (Note: `GET "/"` is asserted status-only — Spring
Boot's welcome-page `forward:index.html` isn't rendered by MockMvc, though it works on a real server.)

## 5. Verification
`./gradlew test jacocoTestCoverageVerification` → **BUILD SUCCESSFUL** (full suite + gate; the security
change regressed nothing). `git status` scoped to Phase 13.1 files.

---

## Outcome
✅ The backend enablers are in place: lookups API, CORS, and same-origin SPA serving with deep-link
fallback. Next: **13.2** — the admin REST API (tenant/user/goaml-config) the admin UI will need.
