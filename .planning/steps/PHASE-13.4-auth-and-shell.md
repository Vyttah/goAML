# Phase 13.4 — Auth + shell (login flow, identity bootstrap, role-aware shell)

> **Status: ✅ DONE (2026-06-06).**
> Part of [../plans/phase-13-react-frontend.md](../plans/phase-13-react-frontend.md). Fourth step of Phase 13.
> Turns the 13.3 placeholder login into a working sign-in flow and fleshes out the authenticated shell.

---

## 1. Goal & why
Give the SPA a real front door: an email/password form that authenticates against
`POST /api/v1/auth/login`, adopts the returned token's claims as the in-app identity, and routes the user
to where they were headed. Plus the shell chrome every authenticated screen sits in (role-aware nav,
signed-in identity, logout).

## 2. What was built (`frontend/`)

| File | Role |
|---|---|
| `features/auth/useLogin.ts` | TanStack `useMutation` wrapping `api/auth.login` (loading/error state for the form). |
| `features/auth/LoginPage.tsx` | AntD card form (email + password, client validation). On success: `signIn(token)` → navigate to the intended route (`location.state.from`) or `/dashboard`. **401 → "Invalid email or password"**; other errors → backend message. Already-authenticated visitors are `<Navigate>`-bounced away. |
| `components/AppShell.tsx` | +role `Tag` next to the email (alongside the existing role-aware nav + sign-out from 13.3). |
| `api/client.ts` | +`httpStatus(err)` helper (pulls an axios error's status for the 401 branch). |
| `test/render.tsx` | `renderWithProviders` — wraps a `<Routes>` tree in the real QueryClient + AuthProvider + MemoryRouter (retries off) for flow tests. |
| `test/setup.ts` | +`matchMedia` + `ResizeObserver` polyfills (jsdom lacks both; AntD's responsive components need them). |

## 3. Key understanding / decisions
- **Identity bootstrap is token-only** — there's no `/me`. On login success the SPA stores the access
  token and derives identity from its claims (the 13.3 `jwt.ts` path). No second round-trip.
- **Redirect-to-intended-route** — `RequireAuth` stashes `location` in route state; `LoginPage` reads
  `state.from.pathname` and returns there post-login (default `/dashboard`). Deep links survive a login bounce.
- **401 on login ≠ 401 elsewhere** — a failed login shows a friendly inline error and stays put; the global
  interceptor's token-clear is a no-op here (there's no identity yet). Distinguished via `httpStatus`.
- **Already-authenticated guard on /login** — prevents a logged-in user from seeing the form (bounces to
  `from`/dashboard), so the login route is idempotent.
- **AntD needs jsdom polyfills** — `matchMedia`/`ResizeObserver` are stubbed once in `setup.ts`; without
  them AntD's grid/responsive hooks throw in tests. (AppShell happened to pass without it; the Card-based
  LoginPage did not — hence the global stub.)

## 4. Tests (Vitest + RTL + MSW)
- `LoginPage.test.tsx` (3): success → token stored + nav to dashboard; bad creds (401) → error shown,
  stays on /login, no token; already-authenticated → redirected off /login.
- `AppShell.test.tsx` (3): shows email + role + routed page; Admin link hidden for ANALYST, shown for
  TENANT_ADMIN; **sign out** → token cleared + back to /login.
- Full suite now **20 passing** (6 files).

## 5. Verification
`npm run typecheck`, `npm run lint` (0 warnings), `npm test` (**20 passing**), `npm run build` (emits
`dist/`) — all green on Node 18.16. `git status` scoped to `frontend/`.

---

## Outcome
✅ Working sign-in: login → token → identity → intended route, with a role-aware shell and clean logout.
Next: **13.5** — the dashboard (report list with status chips, filters, FIU-error surfacing, row → detail)
over `GET /api/v1/reports`.
