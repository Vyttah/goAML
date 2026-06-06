# Phase 13.5 — Dashboard (report list)

> **Status: ✅ DONE (2026-06-06).**
> Part of [../plans/phase-13-react-frontend.md](../plans/phase-13-react-frontend.md). Fifth step of Phase 13.
> The landing screen after login: the tenant's reports, filterable, each a doorway to its detail.

---

## 1. Goal & why
After sign-in a user needs to see their reports at a glance and get to any one of them. 13.5 builds the
report list over `GET /api/v1/reports` with status chips, a status filter + reference search, a refresh,
an author-only "New report" entry point, and row → detail navigation.

## 2. What was built (`frontend/`)

| File | Role |
|---|---|
| `types/index.ts` | +report types: `REPORT_STATUSES`/`ReportStatus`, `ValidationMessage`, `ReportView`, `CreateReportResponse`, `SubmissionView`, `StatusView` (mirror `ReportResponses.*` + `engine.validation.ValidationMessage`). Defined once here; 13.6/13.7 consume them. |
| `api/reports.ts` | `listReports`, `getReport`, `submitReport`, `getReportStatus` (createReport deferred to 13.6 with the DPMSR type). |
| `features/dashboard/useReports.ts` | `useReports()` query + exported `reportsQueryKey` (mutations elsewhere invalidate it). |
| `components/StatusTag.tsx` | Reusable status chip → AntD `Tag` color (DRAFT default · VALID blue · INVALID/REJECTED/FAILED red · SUBMITTED processing · ACCEPTED green); unknown → neutral. Reused by 13.7. |
| `features/dashboard/DashboardPage.tsx` | AntD `Table` of reports; status `Select` + reference/`reportCode` `Input.Search` (client-side filter); refresh; **"New report"** (ANALYST/MLRO only → /reports/new); row click → `/reports/:id`; loading/empty/error states with retry. |

## 3. Key understanding / decisions
- **Read endpoint allows TENANT_ADMIN too** — `GET /reports` is `ANALYST`/`MLRO`/`TENANT_ADMIN`; **create**
  is `ANALYST`/`MLRO` only. So the dashboard renders for an admin but **hides "New report"** from them
  (`can(ANALYST, MLRO)`), matching the backend's `@PreAuthorize`.
- **Client-side filter** — the list is tenant-scoped and modest; status + text filtering happens in-memory
  over the fetched array (no server query params exist). Search matches `entityReference` or `reportCode`.
- **Status chips are centralized** — one `StatusTag` maps the seven backend statuses to colors so the
  dashboard and the 13.7 detail page stay consistent; unknown statuses degrade to a neutral tag rather
  than break.
- **Row is the navigation affordance** — whole-row click → detail (`/reports/:id`); the detail + builder
  routes themselves arrive in 13.6/13.7 (the "New report" button points at the 13.6 route).
- **No FIU-error column on the list** — FIU error text lives on `StatusView` (per-report `/status`), not on
  the list `ReportView`; REJECTED/FAILED are surfaced as red chips here and the full FIU error is shown on
  the detail page (13.7).

## 4. Tests (Vitest + RTL + MSW)
`DashboardPage.test.tsx` (6): renders rows + status chips; reference search narrows the list; row click →
detail route; 500 → error state; MLRO sees + can click "New report" (→ builder route); TENANT_ADMIN does
not see it. `StatusTag.test.tsx` (2): renders the status text; unknown status degrades gracefully. Suite
now **28 passing** (8 files).

## 5. Verification
`npm run typecheck`, `npm run lint` (0 warnings), `npm test` (**28 passing**), `npm run build` (emits
`dist/`) — all green on Node 18.16. (Vitest stderr shows harmless jsdom `getComputedStyle`/scrollbar noise
from AntD's Table; tests pass.) `git status` scoped to `frontend/`.

---

## Outcome
✅ The post-login landing page lists reports with status chips, filtering, and row → detail. Next:
**13.6** — the DPMSR report builder (nested form mirroring `DpmsrCreateRequest` with Zod, lookups-driven
dropdowns, create → inline server `ValidationResult`). The largest FE step.
