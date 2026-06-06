# Phase 10 — `notification/` (in-app notifications + SES email)

> **Status: 🔲 PROPOSED (2026-06-05) — awaiting your approval before implementation.**
> Roadmap **Phase 10**. Turns the report status **transitions** the platform already produces
> (`SUBMITTED`/`ACCEPTED`/`REJECTED`/`FAILED`) into **user-visible signals**: a per-tenant in-app
> notification store **and** outbound email via Amazon SES. Builds on Phase 7 (`SubmissionService` — the
> single place transitions happen) and Phase 9 (the poller that now drives `ACCEPTED`/`REJECTED`
> proactively). Reuses the Phase 6/8 AWS-client + LocalStack pattern.

---

## 1. What this phase is, and why

Today a report changes status silently. The Phase 9 poller flips `SUBMITTED → ACCEPTED/REJECTED` in the
background, and submit-time rejections/transport failures flip to `REJECTED`/`FAILED` — but **nobody is
told**. A compliance officer only finds out by opening the report. Phase 10 closes that gap: every
meaningful transition fans out to (a) an **in-app notification** the user sees in the app, and (b) an
**email** (SES) when enabled. The MLRO who owns the filing and the analyst who authored it learn an
outcome landed without polling a screen.

**Scope (your decisions this session):**
- **Fire at the service layer** — the transition is detected inside `DefaultSubmissionService`
  (`submit()` + `refreshStatus()`), so **one seam covers every path**: the Phase 9 poller, the on-demand
  `GET …/status`, and submit-time rejections. Notify **only on an actual status change**.
- **Wire SES, gated off by default** — build `integration/aws/SesClient` (AWS SDK v2 `sesv2`), tested vs
  LocalStack SES, but email send sits behind `goaml.notifications.email.enabled` (**default `false`**) so
  production stays silent until a verified sender identity is configured. **In-app is always on.**
- **Recipients = report author + tenant MLROs** — the author (`report.created_by`, if set) plus every
  **active MLRO** in that tenant, resolved from `public.app_user` by `tenant_id` + role.

## 2. Decisions

| # | Decision | Choice |
|---|----------|--------|
| D1 | Trigger seam | Fire inside **`DefaultSubmissionService`**: `submit()` (→ `REJECTED`/`FAILED`) and `refreshStatus()` (→ `ACCEPTED`/`REJECTED`). Notify only when the status **actually changes** (refresh compares old→new). Single seam for poller + on-demand + submit. |
| D2 | Events | `REPORT_ACCEPTED`, `REPORT_REJECTED`, `REPORT_FAILED`. `SUBMITTED` is user-initiated (the actor already knows) → no notification. |
| D3 | Recipients | The report **author** (`report.created_by`, if non-null) **+ all active MLROs** of the tenant. De-duplicated by user id. Resolved from `public.app_user` (shared schema) by `tenant_id` + role `MLRO`. |
| D4 | In-app store | A **per-tenant `notification` table** (mirrors `audit_log`: no `@Table(schema=…)`, resolved via the active `search_path`). Entity `model/entity/notification/Notification` + `repository/notification/NotificationRepository`. |
| D5 | In-app write hygiene | `DefaultNotificationService` writes rows in its **own programmatic `TransactionTemplate`** under the bound `TenantContext` — same reason `DefaultAuditService` does (tenant must be set before the connection's `SET search_path`). Recipients come from `app_user` which is explicitly `schema="public"`, so resolution is unaffected by the bound tenant. |
| D6 | Email | `integration/aws/SesClient` (`void send(to, subject, body)`) + `DefaultSesClient` over `SesV2Client`; failures → `SesAccessException`. Bean in `AwsConfig` with the LocalStack endpoint-override pattern. Send **only if** `goaml.notifications.email.enabled` (default `false`); `from` address from config. |
| D7 | Best-effort, isolated | Notifications are a **side effect, never a gate**. The `notify(...)` call in `SubmissionService` is wrapped in try/catch (log + swallow) so a notification/email failure **never** rolls back a status change or aborts a poll cycle — mirrors the poller's "never throw" discipline. Status is persisted **before** notify is invoked. |
| D8 | API surface | `GET /api/v1/notifications` (current user's, newest first; optional `?unread=true`) + `POST /api/v1/notifications/{id}/read`. Any authenticated user, scoped to **their own** rows (a user can only read/mark their own). |
| D9 | Config | New `goaml.notifications.*`: `email.enabled` (bool, default false), `email.from` (string). Bound via a `NotificationProperties` record. No other keys folded in. |

## 3. Step breakdown (one commit per step, each green; per-step doc in `steps/`)

### Step 10.1 — In-app notification store + config
- **`db/migration/tenant/V4__notifications.sql`** — `notification` table: `id` PK, `recipient_user_id`
  UUID NOT NULL, `type` VARCHAR(32) NOT NULL, `report_id` UUID NULL, `title` VARCHAR(255), `body` TEXT,
  `read_at` TIMESTAMPTZ NULL, `created_at` TIMESTAMPTZ NOT NULL DEFAULT NOW(); indexes on
  `(recipient_user_id, created_at)` and a partial index on `recipient_user_id WHERE read_at IS NULL`.
- **`model/entity/notification/Notification`** — JPA entity (no schema qualifier; tenant-resolved like
  `AuditLog`), `@PrePersist` stamps `createdAt`, `markRead()` sets `readAt`.
- **`repository/notification/NotificationRepository`** — `findByRecipientUserIdOrderByCreatedAtDesc`,
  `findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc`, `findByIdAndRecipientUserId`.
- **`config/notification/NotificationProperties`** (`@ConfigurationProperties("goaml.notifications")`) +
  `application.yml` keys (`notifications.email.{enabled:false, from}`).
- **Tests:** Testcontainers persistence + query tests (per-tenant routing, unread filter); properties bind test.

### Step 10.2 — SES client (`integration/aws/`)
- **`integration/aws/SesClient`** (`void send(String to, String subject, String body)`) +
  **`DefaultSesClient`** over `SesV2Client`; resolve the `from` address eagerly (the Phase 8 eager-resolve
  lesson); all SDK failures → **`SesAccessException`**.
- **`config/aws/AwsConfig`** — add `@Bean SesV2Client` (regional + default-creds in prod; endpoint-override
  + static dummy creds under LocalStack — same shape as the S3/Secrets beans).
- **`build.gradle`** — add `implementation 'software.amazon.awssdk:sesv2'` (BOM-managed).
- **Tests:** `DefaultSesClientTest` (mocked `SesV2Client`: builds the request, maps failures to
  `SesAccessException`); `SesClientIT` (`@Tag("localstack")`, socket-reachability `assumeTrue` skip — like
  `S3StorageClientIT`): verify-identity then send vs LocalStack SES.

### Step 10.3 — NotificationService (recipients + in-app write + gated email)
- **`AppUserRepository`** — add recipient queries: active MLROs of a tenant
  (`findByTenantIdAndStatusAndRoles_Name(tenantId, "ACTIVE", "MLRO")` or a `@Query`) and reuse `findById`
  for the author.
- **`service/notification/NotificationService`** + **`DefaultNotificationService`** —
  `notifyReportTransition(Report report, String newStatus, UUID tenantId)`: resolve recipients (author +
  MLROs, de-duped), build a `type`/`title`/`body` per event (D2), write one in-app row per recipient (own
  `TransactionTemplate`, bound `TenantContext`), then — **if** `email.enabled` — `sesClient.send(...)` per
  recipient email. Plus `list(userId, unreadOnly)` and `markRead(userId, notificationId)`.
- **`service/notification/NotificationExceptions`** — `NotificationNotFoundException` (404; also covers a
  user trying to mark another user's notification — not found *for them*).
- **Tests:** `DefaultNotificationServiceTest` (mocked repos + `SesClient`): recipients = author + MLROs,
  de-dup when author is the MLRO; one in-app row per recipient with the right type/title; email **sent when
  enabled, skipped when disabled**; an `SesAccessException` is swallowed (in-app rows still written —
  best-effort); `list`/`markRead` scope to the owner.

### Step 10.4 — Wire the seam + REST + integration + docs/planning sync
- **`DefaultSubmissionService`** — inject `NotificationService`; in `refreshStatus()` capture the old
  status and, when it **changes** to `ACCEPTED`/`REJECTED`, call `notifyReportTransition` (try/catch,
  log+swallow, **after** the saves). In `submit()` fire `REPORT_REJECTED` (on `B2bValidationException`) and
  `REPORT_FAILED` (on transport/auth) likewise. SUBMITTED → no notify (D2).
- **`controller/notification/NotificationController`** — `GET /api/v1/notifications` (`?unread=`), `POST
  /api/v1/notifications/{id}/read`; resolves the current user from the JWT principal; any authenticated role.
- **`model/dto/notification/NotificationView`** (+ `from(Notification)`); `GlobalExceptionHandler` →
  `NotificationNotFoundException` 404.
- **Integration test** (Testcontainers + mocked `GoamlB2bClient`, building on the Phase 9 poller IT): seed a
  tenant + author + MLRO + a `SUBMITTED` report; stub FIU `Accepted`; run the poller; assert notification
  rows landed for **both** author and MLRO in the tenant schema (email left disabled). A second case:
  `email.enabled=true` with a mocked `SesClient` asserts `send` invoked per recipient.
- **Docs/planning:** `docs/02` (`notification/` ✅), `docs/06`/`docs/07` (status lifecycle now notifies),
  `docs/09`/`ROADMAP`/`STATE`/`CLAUDE.md` (Phase 10 ✅, Phase 11 next, 10/14); fill this plan's outcome +
  per-step docs `steps/PHASE-10.1..10.4`.

## 4. JaCoCo / coverage
Add `com/vyttah/goaml/service/notification/**` to `coveredPackages` at the same **≥90% instruction /
≥80% branch** bar (`integration/aws/**` is already gated — `SesClient` lands there). Deterministic unit
tests (mocked repos + `SesClient`, no real SES) carry coverage; the `@Tag("localstack")` IT and the
Testcontainers integration test prove the wiring.

## 5. What this phase does NOT do
SMS/push/webhooks; user notification preferences or digest batching (every transition notifies its
recipients now); templated/HTML email (plain text first — templating is a later polish); a "mark all read"
or delete endpoint; notifications for `DRAFT`/`VALID`/`INVALID`/`SUBMITTED` (D2); retry of failed email
(best-effort — the in-app row is the durable record); the React notification bell (Phase 13 consumes the
`GET /notifications` API); real SES sends in prod (gated off until a verified identity exists).

## 6. How you'll verify it (after this phase)
With `docker compose up -d postgres localstack redis`: the integration test drives a poll cycle and asserts
in-app rows for author + MLRO (and, with the flag on + mocked `SesClient`, an email per recipient); the
`@Tag("localstack")` IT proves a real SES round-trip against LocalStack. Manually: a `SUBMITTED` report
that the poller flips to `ACCEPTED` produces notifications visible via `GET /api/v1/notifications` for the
author and the MLRO — without anyone polling the report screen.

## 7. Verification
`docker compose up -d postgres localstack redis` → `./gradlew test jacocoTestCoverageVerification` green;
notifications are **best-effort** (a forced `SesAccessException` neither rolls back the status change nor
aborts the poll); recipients resolve to author + MLROs and de-dup; email is gated off by default; `git
status` scoped to Phase 10 dirs + the 10.4 docs.

## 8. Notes / to confirm in-step
- **Best-effort is load-bearing:** the `notify(...)` call must be try/caught at the `SubmissionService`
  seam and status must be persisted *before* it — a broker/SES outage must never undo a filing outcome or
  suppress future poll runs (same hazard the poller's "never throw" guards).
- **Transition detection:** `refreshStatus` already reloads the report; capture `oldStatus` before
  `setStatus` and only notify on a genuine change to a terminal state (avoids re-notifying on a no-op poll).
- **Tenant binding:** in-app writes use a programmatic tx under the bound `TenantContext` (audit pattern);
  `app_user` is `schema="public"` so recipient resolution is tenant-independent and safe either way.
- **Email gating:** keep `email.enabled=false` everywhere by default (incl. tests) so no test or dev run
  attempts a real send; the unit test flips it on with a mocked client.
- **SES identity (live, not now):** real sends need a verified sender/domain in the tenant's SES region;
  recorded as a go-live input, not a build blocker.

---

## Outcome — 🔲 (fill on completion)
