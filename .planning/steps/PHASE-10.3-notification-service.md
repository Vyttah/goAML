# Phase 10.3 — NotificationService (recipients + in-app write + gated email)

> **Status: ✅ DONE (2026-06-06).**
> Part of [../plans/phase-10-notifications.md](../plans/phase-10-notifications.md). Third step of Phase 10.

---

## 1. Goal & why
The logic that turns a transition into notifications: resolve **who** (author + tenant MLROs), write the
in-app rows (the durable record), and dispatch **gated, best-effort** email. No firing yet — the
`SubmissionService` seam + REST land in 10.4.

## 2. What was built

| File | Role |
|---|---|
| `repository/appuser/AppUserRepository` | `+ findByTenantIdAndStatusAndRoles_Name(tenantId, status, roleName)` — active MLROs of a tenant (shared `public` schema). |
| `service/notification/NotificationService` | Interface: `notifyReportTransition(report, newStatus, tenantId)`, `list(userId, unreadOnly)`, `markRead(userId, notificationId)`. |
| `service/notification/DefaultNotificationService` | Resolve recipients (author via `findById` + MLROs, de-duped, author first) → write one in-app row each → if `email.enabled`, SES per recipient. |
| `service/notification/NotificationExceptions` | `NotificationNotFoundException` (also covers "another user's notification" → 404). |
| `build.gradle` | `+ com/vyttah/goaml/service/notification/**` to the JaCoCo `coveredPackages`. |

## 3. Key understanding / decisions
- **No self-managed `TenantContext`, no `TransactionTemplate`.** Unlike `AuditService` (which sets context
  itself), `notifyReportTransition` is always called with a tenant already bound (by the poller or the
  request filter), and `submit()`/`refreshStatus()` hold no outer transaction — so a plain
  `notificationRepository.save(...)` opens its own tx with the correct `search_path`. Simpler and trivially
  unit-testable.
- **Recipient resolution reads `public.app_user`.** `AppUser` is `@Table(schema="public")`, so the MLRO
  query + author lookup are independent of the bound tenant. De-dup via `LinkedHashMap` (author first; an
  author who is also an MLRO collapses to one row).
- **Ordering = in-app first, email second.** The in-app rows are the source of truth; email is a side
  channel. Each `sesClient.send` is wrapped in try/catch (`SesAccessException` → log + continue) so one bad
  address never blocks the rest — and email failures never affect the rows.
- **Email gate lives here, not in the client.** `goaml.notifications.email.enabled` (default false) is
  checked before any send; the `from` null-guard tolerates an unbound `email` config without NPE.
- **Notifiable transitions only** (`templateFor`): `ACCEPTED`/`REJECTED`/`FAILED` → typed title+body;
  anything else (e.g. `SUBMITTED`) is a no-op (the actor already knows).

## 4. Tests
`DefaultNotificationServiceTest` (12, mocked repos + `SesClient`): author+MLRO de-dup writes the right
rows/types; email sent per recipient when enabled / skipped when disabled; an `SesAccessException` is
swallowed and the next recipient still emailed; blank recipient email + null email-config skipped
gracefully; author-not-found falls back to MLROs only; `SUBMITTED`/no-recipients are no-ops; `list` routes
the unread filter; `markRead` marks own / throws `NotificationNotFoundException` for another's.
`DefaultNotificationService` → **100% instruction**, all branches covered.

## 5. Verification
`./gradlew test jacocoTestCoverageVerification` → **BUILD SUCCESSFUL** (full suite + gate); the new beans
(`SesV2Client`, `NotificationConfig`, `DefaultNotificationService`) load cleanly into every
`@SpringBootTest` context. `git status` scoped to Phase 10.3 files.

---

## Outcome
✅ Recipients resolve, in-app rows write, email is gated + best-effort — all unit-proven. Next: **10.4** —
wire the `SubmissionService` transition seam, add the `GET/POST /api/v1/notifications` REST, the
Testcontainers integration test, and the docs/planning sync.
