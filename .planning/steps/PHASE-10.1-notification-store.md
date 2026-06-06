# Phase 10.1 — In-app notification store + config

> **Status: ✅ DONE (2026-06-06).**
> Part of [../plans/phase-10-notifications.md](../plans/phase-10-notifications.md). First step of Phase 10.

---

## 1. Goal & why
Lay the durable substrate before any notification logic: the **per-tenant in-app store** (table + entity +
repo) and the **config** that gates email. No service, no SES, no firing yet (those are 10.2–10.4). This is
the source of truth the in-app bell will read; email is a later, best-effort side channel.

## 2. What was built

| File | Role |
|---|---|
| `db/migration/tenant/V4__notifications.sql` | Per-tenant `notification` table: `recipient_user_id`, `type`, `report_id`, `title`, `body`, `read_at`, `created_at`; index on `(recipient_user_id, created_at DESC)` + partial index on unread. |
| `model/entity/notification/Notification` | JPA entity (no `@Table` schema — tenant-resolved like `AuditLog`); `markRead()` (idempotent), `@PrePersist` stamps `createdAt`. |
| `repository/notification/NotificationRepository` | `findByRecipientUserIdOrderByCreatedAtDesc`, `…AndReadAtIsNullOrderByCreatedAtDesc` (unread), `findByIdAndRecipientUserId` (owner-scoped). |
| `config/notification/NotificationProperties` | record bound from `goaml.notifications.*`: `email.{enabled, from}`. |
| `config/notification/NotificationConfig` | `@Configuration @EnableConfigurationProperties(NotificationProperties.class)`. |
| `application.yml` | `goaml.notifications.email.{enabled:false, from}` — only new keys; email **off by default**. |

## 3. Key understanding / decisions
- **Per-tenant table, no FK to `app_user`.** `recipient_user_id` references `public.app_user.id`, but the
  `notification` table lives in each `tenant_<id>` schema — a cross-schema FK would be brittle, so the link
  is by id only (same reasoning as `audit_log.actor_user_id`).
- **Resolved via `search_path`, like `AuditLog`.** No `@Table(schema=…)`; querying without a bound tenant
  hits `public.notification` (which doesn't exist) and errors — intentional: tenant data must be tenanted.
- **Email off by default, everywhere.** The flag defaults `false` in `application.yml`; nothing in 10.1
  reads it yet, but it's in place so 10.3 can gate sends and tests never attempt a real SES call.
- **`NotificationConfig` mirrors `SchedulerConfig`** — config wiring grouped in `config/notification/`,
  not on the app class. Like `config/scheduler/**`, this trivial wiring is **not** added to the JaCoCo gate
  (only the logic-bearing `service/notification/**`, landing in 10.3, will be).

## 4. Tests
- **`NotificationPersistenceTest`** (Testcontainers): provisions a tenant, writes one read + one unread for
  a recipient and one for another user; asserts the recipient query returns 2 (owner-scoped, newest-first),
  the unread filter returns only the unread one, and `findByIdAndRecipientUserId` is present for the owner
  / empty for a non-owner.
- **`NotificationPropertiesTest`** (Binder, no context): binds `email.enabled`/`email.from`; asserts
  `enabled` defaults `false` when the flag is absent.

## 5. Verification
`./gradlew test --tests NotificationPropertiesTest --tests NotificationPersistenceTest` → **BUILD
SUCCESSFUL**; 3 tests pass. `git status` scoped to Phase 10.1 files.

---

## Outcome
✅ The in-app store + config substrate is in place (no service yet). Next: **10.2** — the
`integration/aws/SesClient` (SES over `sesv2`, LocalStack-tested, failures → `SesAccessException`).
