# Phase 13.8 — File import UI

> **Status: ✅ DONE (2026-06-07).**
> Part of [../plans/phase-13-react-frontend.md](../plans/phase-13-react-frontend.md). Eighth step of Phase 13.
> Upload a goAML XML / DPMSR CSV → see the per-row outcome; browse import history.

---

## 1. Goal & why
Operators bring reports in from files, not just the builder. 13.8 wraps the Phase 11 ingestion API:
upload an XML or CSV, render the resulting import job's per-row results (created/invalid/failed with
messages and links to created reports), and keep a browsable history.

## 2. What was built (`frontend/`)

| File | Role |
|---|---|
| `types/index.ts` | +`ImportRowResult`, `ImportJobView` (mirror `service.ingestion.ImportRowResult` / `model.dto.ingestion.ImportJobView`). |
| `api/imports.ts` | `importXml`/`importCsv` (multipart), `listImports`, `getImport`. |
| `features/imports/useImports.ts` | `useImports` (history) + `useImport(format)` upload mutation (invalidates history). |
| `components/imports/ImportResultsTable.tsx` | Per-row outcome table (row #, reference, StatusTag, link to created report, messages); reused by the result panel + history expansion. |
| `features/imports/ImportPage.tsx` | Format toggle (DPMSR CSV / goAML XML) + drag-drop uploader → result panel (counts + results); history table (job status chip, totals) with expandable per-row results. |
| `routes/AppRoutes.tsx` + `components/AppShell.tsx` | `/imports` route + an "Import" nav item (ANALYST/MLRO/TENANT_ADMIN). |

## 3. Key understanding / decisions
- **RBAC mirrors the API** — upload is `ANALYST`/`MLRO`; list is also `TENANT_ADMIN`. The page renders for
  all three but **hides the uploader** from a tenant admin (history-only), matching `ImportController`.
- **Two endpoints, one toggle** — `/imports/xml` vs `/imports/csv` chosen by a `Segmented` control; the
  uploader's `accept` follows the format. Upload goes through AntD `Upload.Dragger` `customRequest` →
  multipart (JSON default content-type cleared so axios sets the boundary, same as attachments).
- **Whole-file rejection vs per-row failure** — a 400 (`ImportRejectedException`: unreadable / missing
  headers / over the row cap) is surfaced as an error alert; a *created* job instead carries per-row
  `VALID`/`INVALID`/`FAILED` outcomes rendered in the results table (failed rows show the reason; created
  rows link to `/reports/:id`).
- **History is expandable** — each past job expands to its stored per-row results (no extra fetch; the list
  view already includes them), so an operator can audit any prior import.
- **Job status ≠ report status** — import-job status (COMPLETED/PARTIAL/FAILED) uses a local tag mapping;
  the per-row status reuses `StatusTag` (VALID/INVALID/FAILED) for consistency with reports.

## 4. Tests (Vitest + RTL + MSW)
`ImportPage.test.tsx` (4): CSV upload (driven via the dragger's file input) → result panel shows counts +
per-row results (reference, status, failure message, link to created report); whole-file 400 → error alert;
history renders with job status; tenant admin sees history but **no uploader**. Suite now **50 passing**
(13 files).

## 5. Verification
`npm run typecheck`, `npm run lint` (0 warnings), `npm test` (**50 passing**), `npm run build` (emits
`dist/`) — all green on Node 18.16. `git status` scoped to `frontend/`.

---

## Outcome
✅ File import is usable from the UI: upload XML/CSV → per-row outcomes → history. Next: **13.9** —
notifications center (bell + unread count, list, mark-read) + a read-only lookups/jurisdictions browser.
