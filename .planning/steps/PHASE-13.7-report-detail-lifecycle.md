# Phase 13.7 — Report detail + lifecycle

> **Status: ✅ DONE (2026-06-07).**
> Part of [../plans/phase-13-react-frontend.md](../plans/phase-13-react-frontend.md). Seventh step of Phase 13.
> The per-report page: summary, MLRO submit, FIU status, and attachments — the route the dashboard and the
> builder's "View report" link into.

---

## 1. Goal & why
Give each report a home where its lifecycle happens: see its summary/status, (MLRO) submit it to the FIU
with a confirmation, check the latest FIU status on demand, and manage supporting attachments.

## 2. What was built (`frontend/`)

| File | Role |
|---|---|
| `types/index.ts` | +`AttachmentView` (mirror of `model.dto.attachment.AttachmentView`). |
| `api/attachments.ts` | `listAttachments` / `addAttachment` (multipart) / `removeAttachment`. |
| `features/reports/useReportDetail.ts` | `useReport`, `useSubmitReport`, `useCheckStatus`, `useAttachments`, `useAddAttachment`, `useRemoveAttachment` (with cache invalidation). |
| `features/reports/ReportDetailPage.tsx` | Summary (Descriptions + StatusTag + copyable id); **Lifecycle** card (MLRO submit w/ Popconfirm, VALID-only + disabled-with-reason; on-demand "Check FIU status" → reportKey/status/errors); **Attachments** card (list + Upload + remove, role-gated). |
| `routes/AppRoutes.tsx` | `/reports/:id` (any authenticated role; matches the read RBAC). |

## 3. Key understanding / decisions
- **Submit is VALID-only + MLRO-only** — mirrors `DefaultSubmissionService` (`!"VALID" → ReportNotSubmittableException`)
  and the `@PreAuthorize("hasRole('MLRO')")`. The button shows for MLROs but is disabled with an inline
  reason when the report isn't VALID; a `Popconfirm` guards the irreversible filing.
- **FIU status is on-demand, not eager** — `GET /{id}/status` calls the FIU (`refreshStatus`) and 409s when
  there's no submission yet, so the "Check FIU status" button only appears for submitted/accepted/rejected/
  failed reports, and errors render via `errorMessage`.
- **Attachments: no download** — list/upload/remove exist; there is **no GET-bytes endpoint** (D8), so the
  table is metadata-only with a note. Upload uses AntD `Upload` `customRequest` → multipart (the api client's
  JSON default content-type is cleared so axios sets the multipart boundary). Upload/remove gated ANALYST/MLRO.
- **No XML preview / no validation panel** — there is **no backend endpoint** to fetch an existing report's
  XML or to re-fetch its validation result (validation messages are returned only at create time, 13.6).
  Both are deferred as small future backend adds and called out in the UI rather than faked. (Same posture
  as the attachment-download gap.)
- **Cache-coherent lifecycle** — submit/remove invalidate the report + attachments (and the dashboard list),
  so the page and list reflect the new state without manual refetch.

## 4. Tests (Vitest + RTL + MSW)
`ReportDetailPage.test.tsx` (6): renders summary; **MLRO submits a VALID report → status flips to SUBMITTED
and the status-check action appears** (stateful handlers); submit hidden from ANALYST; submit disabled +
explained for a non-VALID report; on-demand FIU status renders reportKey + status; attachments list + remove
(→ "No attachments"). Suite now **46 passing** (12 files).

## 5. Verification
`npm run typecheck`, `npm run lint` (0 warnings), `npm test` (**46 passing**), `npm run build` (emits
`dist/`) — all green on Node 18.16. `git status` scoped to `frontend/`.

---

## Outcome
✅ The report lifecycle is drivable from the UI: view → (MLRO) submit → track FIU status → manage
attachments. Next: **13.8** — file import UI (upload goAML XML / DPMSR CSV → per-row results table + import
history) over `POST/GET /api/v1/imports`.
