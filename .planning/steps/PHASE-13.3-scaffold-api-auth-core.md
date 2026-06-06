# Phase 13.3 — Frontend scaffold + tooling + api/auth core

> **Status: ✅ DONE (2026-06-06).**
> Part of [../plans/phase-13-react-frontend.md](../plans/phase-13-react-frontend.md). Third step of Phase 13,
> and the **first frontend step** — greenfield `frontend/`. Sets up the React+TS+Vite+AntD toolchain and the
> foundational layers (typed api client, auth core, router + guards) that every later UI step builds on.

---

## 1. Goal & why
Stand up the SPA project and its non-visual foundations so 13.4–13.10 can each add one UI area against a
stable base: a typed HTTP client with auth wired in, a JWT-derived identity, role-aware routing, server-state
caching, and a test harness that gates the logic layers. **No real screens yet** (placeholders only) —
those are the subsequent steps.

## 2. What was built (`frontend/`)

| Area | Files | Role |
|---|---|---|
| Tooling | `package.json`, `tsconfig*.json`, `vite.config.ts`, `.eslintrc.cjs`, `.prettierrc.json`, `.gitignore`, `index.html`, `.env.development` | Vite 5 + React 18 + TS 5.5 + AntD 5; Vitest 2 (jsdom) + RTL + MSW; ESLint 8 (TS + react-hooks) + Prettier. Node-18-compatible pins. |
| Config | `src/lib/config.ts` | `API_BASE_URL` (empty = same-origin/proxy), `API_PREFIX=/api/v1`, token storage key. |
| Types | `src/types/index.ts` | DTO mirrors — auth (`LoginRequest`/`LoginResponse`) for now; each feature step adds its area's types. |
| API | `src/api/client.ts` | Shared axios instance; request interceptor attaches `Bearer`; response interceptor on **401 → clear token + unauthorized handler**. `errorMessage()` pulls the backend `{message}`. |
| API | `src/api/queryClient.ts`, `src/api/auth.ts` | TanStack Query client (no-retry on mutations); `login()`. |
| Auth | `src/auth/jwt.ts` | **Client-side JWT decode** (no `/me` endpoint exists) → `Identity` from `sub/email/tenant/schema/roles`; `isExpired` w/ skew. |
| Auth | `src/auth/tokenStore.ts` | In-memory + `localStorage` bearer store. |
| Auth | `src/auth/roles.ts` | `ROLES` mirror + `hasAnyRole`. |
| Auth | `src/auth/AuthContext.tsx` | `AuthProvider`/`useAuth`: identity from token, `signIn/signOut/can`; registers the 401 handler → drops identity (→ redirect). |
| Routing | `src/routes/RequireAuth.tsx`, `RequireRole.tsx`, `AppRoutes.tsx` | Auth gate (→ /login) + role gate (→ /forbidden); route table. |
| Shell/pages | `src/components/AppShell.tsx` + `features/{auth,dashboard,admin,misc}/*` | Minimal AntD shell (nav + sign-out) and placeholder pages (filled by 13.4–13.10). |
| Entry | `src/App.tsx`, `src/main.tsx` | Providers: ConfigProvider + AntApp → QueryClient → Auth → Router. |
| Tests | `src/test/{setup,util}.ts`, `src/test/msw/server.ts` + 4 specs | MSW server; `makeToken()`; **14 tests** (jwt, tokenStore, client interceptors, auth+role guards). |

## 3. Key understanding / decisions
- **No `/me` endpoint** — the only auth surface is `POST /api/v1/auth/login` returning
  `{accessToken, tokenType, expiresInSeconds}`. Identity is therefore **decoded from the JWT claims**
  the backend signs (`JwtService`: `sub, email, tenant, schema, roles`, plus `exp`). The client decodes
  only (never verifies — the backend verifies every request); this is UX state, not access control.
- **Roles are bare names in the token** (`SUPER_ADMIN`/`TENANT_ADMIN`/`MLRO`/`ANALYST`) — Spring adds the
  `ROLE_` prefix internally; the SPA matches the bare names.
- **401 = re-login** (no refresh token). The response interceptor clears the token and fires a registered
  handler; `AuthProvider` wires that to drop identity, which flips `RequireAuth` into a /login redirect.
- **Same-origin by default** — `API_BASE_URL` is empty so calls are relative `/api/v1/...`, resolved by the
  Vite dev proxy (→ :8080) in dev and by the serving jar in prod. `VITE_API_BASE_URL` targets a remote
  backend (then the env-gated CORS bean must allow it).
- **Guards are UX, not security** — `RequireAuth`/`RequireRole` shape navigation; the backend independently
  enforces RBAC (mirrored in tests: a missing role → /forbidden client-side, 403 server-side).
- **Node-18 pins** — this machine runs Node 18.16, so the stack is pinned to Node-18-capable majors
  (Vite 5, Vitest 2, ESLint 8, React 18, AntD 5). Some dev deps emit `EBADENGINE` (want 18.18+) but run.
- **Placeholders, not throwaway** — login/dashboard/admin pages are intentional one-liners that the named
  later steps replace; the shell + router + guards around them are real.

## 4. Tests (Vitest + RTL + MSW)
- `jwt.test.ts` (4): decode claims; reject malformed; SUPER_ADMIN (null tenant) valid; expired → null.
- `tokenStore.test.ts` (2): set/get/clear; localStorage persistence.
- `client.test.ts` (4): attaches bearer; omits header when no token; **401 → clears token + handler**;
  `errorMessage` extracts the backend message.
- `guards.test.tsx` (4): unauth → /login; authed renders; role present → page; role absent → /forbidden.

## 5. Verification
`npm run typecheck` (tsc), `npm run lint` (eslint, 0 warnings), `npm test` (**14 passing**), and
`npm run build` (emits `dist/`) all green on Node 18.16. `git status` scoped to `frontend/`
(`node_modules`/`dist`/`coverage` gitignored; `package-lock.json` committed).

### Install note (for the record)
The flaky network truncated two native/ESM artifacts on first install — `@rollup/rollup-darwin-arm64`
(corrupt `.node`) and `@mswjs/interceptors` (missing `.mjs`). Fixed by clearing the npm cache and
force-reinstalling the rollup native binary at the matching version, then reinstalling msw. A clean
`rm -rf node_modules && npm ci` on a stable network avoids this.

---

## Outcome
✅ The SPA foundation is in place — typed api client with auth + 401 handling, JWT-derived identity,
role-aware routing, TanStack Query, and a green logic-layer test harness. Next: **13.4** — the real login
page (email/password → token → identity bootstrap) + the fleshed-out app shell/nav.
