# Phase 10.4 — Wire the seam + REST + integration + docs

> **Status: ✅ DONE (2026-06-06).**
> Part of [../plans/phase-10-notifications.md](../plans/phase-10-notifications.md). Final step of Phase 10.

---

## 1. Goal & why
Connect the service to the one place transitions happen, expose the in-app store over REST, and prove the
whole chain end-to-end. After this, an FIU outcome reaches the right users without anyone opening a screen.

## 2. What was built

| File | Role |
|---|---|
| `service/submission/DefaultSubmissionService` | `+ NotificationService` dep + `safeNotify(report, status, tenantId)`. `refreshStatus` captures `oldStatus` and notifies only on a genuine change to `ACCEPTED`/`REJECTED`; `submit` notifies `REJECTED` (FIU validation) / `FAILED` (transport/auth). All best-effort (try/catch, after the saves). |
| `controller/notification/NotificationController` | `GET /api/v1/notifications` (`?unread=`), `POST /api/v1/notifications/{id}/read`; recipient = authenticated `UserPrincipal`; `isAuthenticated()`. |
| `model/dto/notification/NotificationView` | API view (`+ from(Notification)`). |
| `exception/GlobalExceptionHandler` | `+ NotificationNotFoundException` → 404. |

## 3. Key understanding / decisions
- **The seam is `DefaultSubmissionService`, not the poller.** Both `submit()` and `refreshStatus()` route
  through `safeNotify`, so the poller, the on-demand `GET …/status`, and submit-time rejections all notify
  from one place — no duplication.
- **Transition-detected.** `refreshStatus` compares `oldStatus` → `mapped` and only notifies on a real
  change; a no-op poll (`SUBMITTED → SUBMITTED`) stays silent. (Unit-asserted both ways.)
- **`safeNotify` mirrors the poller's "never throw".** Status is persisted first; a notification/email
  failure is logged and swallowed so it can never roll back a filing outcome or abort a poll cycle.
- **Submit-time rejection notifies even though the report stays `VALID`.** The notification carries the
  logical outcome (`REJECTED`/`FAILED`) so the analyst-author learns of it, even though the report remains
  editable for a fix.
- **REST is owner-scoped by construction.** The recipient is always `principal.getUserId()` and the tenant
  schema is bound by the JWT filter, so a user can only ever see/mark their own rows.

## 4. Tests
- **`DefaultSubmissionServiceTest`** (extended): rejection → `notify(report,"REJECTED")`; transport →
  `notify(report,"FAILED")`; refresh→REJECTED notifies; refresh no-op (SUBMITTED→SUBMITTED) → `never`
  notifies. Constructor updated with a mocked `NotificationService`.
- **`NotificationIntegrationTest`** (Testcontainers + `@MockBean` `GoamlB2bClient`/`SesClient`): seed a
  tenant + author (ANALYST) + MLRO + a SUBMITTED report; FIU `Accepted`; run the poller; assert an in-app
  `REPORT_ACCEPTED` row for **both** author and MLRO in the tenant schema (email disabled → `SesClient`
  untouched).
- **`NotificationEmailIntegrationTest`** (`properties = email.enabled=true`, `@MockBean SesClient`): same
  poll-driven transition also calls `sesClient.send(mlroEmail, "Report accepted", …)` — proving the gate
  wires through end-to-end.

## 5. Verification
`docker compose up -d postgres localstack redis` → `./gradlew test jacocoTestCoverageVerification` →
**BUILD SUCCESSFUL** (full suite + gate). The seam fires from one place across all transition paths;
best-effort isolation holds; REST is owner-scoped.

---

## Outcome
✅ Phase 10 complete — report transitions now reach users (in-app always, email when enabled) without
anyone polling a screen. Next: **Phase 11 — `ingestion/`** (the Phase 1.5 accounting/screening intake).
