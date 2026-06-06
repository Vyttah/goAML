# Phase 13.9 — Notifications + lookups browser

> **Status: ✅ DONE (2026-06-07).**
> Part of [../plans/phase-13-react-frontend.md](../plans/phase-13-react-frontend.md). Ninth step of Phase 13.
> The in-app notifications surface (Phase 10 API) + a read-only reference-data browser (Phase 13.1 API).

---

## 1. Goal & why
Two read-leaning surfaces: a notifications center + header bell so users see report-transition alerts
(accepted/rejected/failed) without watching the dashboard, and a reference browser so they can inspect the
jurisdictions + lookup code sets the builder validates against.

## 2. What was built (`frontend/`)

| File | Role |
|---|---|
| `types/index.ts` | +`NotificationView` (mirror `model.dto.notification.NotificationView`). |
| `api/notifications.ts` | `listNotifications(unreadOnly)`, `markNotificationRead(id)`. |
| `features/notifications/useNotifications.ts` | `useNotifications`, `useUnreadNotifications` (60s poll for the badge), `useMarkNotificationRead` (invalidates all notification queries). |
| `components/notifications/NotificationBell.tsx` | Header bell: unread `Badge` + popover of unread items; clicking one marks read + opens its report; "View all" → /notifications. |
| `features/notifications/NotificationsPage.tsx` | Center: list with unread emphasis (badge + "new" tag), unread-only toggle, mark-read, open-report. |
| `features/lookups/LookupsBrowserPage.tsx` | Pick jurisdiction → its config (name/currency/threshold/report types) + lookup sets → pick a set → its codes. |
| `features/lookups/useLookups.ts` | Added `enabled` guards to `useLookupSets`/`useLookupCodes` (no fetch with an empty jurisdiction/set). |
| `routes/AppRoutes.tsx` + `components/AppShell.tsx` | `/notifications` + `/reference` routes; "Reference" nav item; the bell in the header. |

## 3. Key understanding / decisions
- **Own-rows only, any role** — notifications endpoints are `isAuthenticated()` and scoped server-side to
  the caller (`principal.getUserId()`), so the bell + center work for every role with no client-side
  ownership logic.
- **Badge polls; lists don't** — the header unread count uses a 60s `refetchInterval` so it drifts toward
  fresh (the backend poller flips report statuses → notifications out-of-band); the full list refetches on
  mark-read via cache invalidation rather than polling.
- **Mark-read is shared-invalidation** — `useMarkNotificationRead` invalidates the whole `['notifications']`
  key family so the bell badge and the center page both update from one mutation.
- **Reference is read-only + jurisdiction-first** — defaults to the first jurisdiction; sets/codes load
  lazily (the new `enabled` guards prevent malformed `/lookups/` and `/lookups/ae/` calls). Codes render as
  the same bare-code tags the builder uses (placeholder seeds; code == label).
- **Reference is visible to all** (incl. SUPER_ADMIN) since lookups are platform reference data, not tenant
  data — matches the `isAuthenticated()` lookup endpoints.
- **Shell test impact** — the bell added a `GET /notifications` to every AppShell render, so `AppShell.test`
  now stubs it (the suite's `onUnhandledRequest: 'error'` would otherwise flag it).

## 4. Tests (Vitest + RTL + MSW)
- `NotificationsPage.test.tsx` (2): lists + marks an unread read (stateful → "new" tag clears); "Open
  report" → detail route.
- `NotificationBell.test.tsx` (1): unread count badge renders.
- `LookupsBrowserPage.test.tsx` (1): default jurisdiction + its sets render; selecting a set shows its codes.
- Suite now **54 passing** (16 files).

## 5. Verification
`npm run typecheck`, `npm run lint` (0 warnings), `npm test` (**54 passing**), `npm run build` (emits
`dist/`) — all green on Node 18.16. `git status` scoped to `frontend/`.

---

## Outcome
✅ Notifications (bell + center) and the reference browser are live. Next: **13.10** — admin UI (tenant
management for SUPER_ADMIN; user + goAML-config management for TENANT_ADMIN) over the 13.2 admin API.
