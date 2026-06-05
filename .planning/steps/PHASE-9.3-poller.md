# Phase 9.3 — SubmissionStatusPoller (the @Scheduled loop)

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-9-scheduler.md](../plans/phase-9-scheduler.md). Third step of Phase 9.

---

## 1. Goal & why
The actual poller: on a timer, refresh the FIU status of every `SUBMITTED` report across all ACTIVE
tenants — so reports move to `ACCEPTED`/`REJECTED` without anyone calling `GET …/status`.

## 2. What was built

| File | Role |
|---|---|
| `scheduler/SubmissionStatusPoller` | `@Scheduled scheduledPoll()` (flag-guarded, swallows all) → `pollAllTenants()`: enumerate ACTIVE tenants → per tenant bind `TenantContext` → `reportRepository.findByStatus("SUBMITTED")` → per report `retryService.retryTransient(() -> submissionService.refreshStatus(id, tenantId))`; per-report + per-tenant failures logged + skipped. |
| `scheduler/PollSummary` | `(tenantsScanned, reportsPolled, succeeded, skipped)` — logged + returned for tests. |

## 3. Key understanding / decisions
- **Orchestration only.** The poller reuses the tested `SubmissionService.refreshStatus` (FIU call + status
  mapping + persistence). It adds *discovery + tenant iteration + retry + error isolation* — nothing else.
- **Two invariants enforced in code + tests:**
  1. the `@Scheduled` method **never lets an exception escape** (Spring suppresses all future runs of a
     throwing `@Scheduled` method) — `scheduledPoll` try/catches the whole cycle;
  2. **`TenantContext` is always cleared in `finally`** per tenant — else a pooled thread leaks one
     tenant's schema into the next iteration.
- **Error isolation:** a per-report failure (post-retry) increments `skipped` and continues to the next
  report; a per-tenant failure (e.g. discovery query throws) skips that tenant and continues to the next.
- **Terminal states drop out naturally:** once a report is `ACCEPTED`/`REJECTED` it's no longer
  `SUBMITTED`, so the next cycle skips it — no "done" flag needed.

## 4. Bug found & fixed mid-step
- **`@Scheduled(fixedDelayString)` rejects the `"5m"` suffix.** It accepts a millis-long or **ISO-8601**
  only; `"5m"` → `NumberFormatException` → the `submissionStatusPoller` bean fails → **every
  `@SpringBootTest` context fails to load** (25 failures). Fixed by setting `goaml.scheduler.status-poll.interval`
  to **`PT5M`** (ISO-8601) in `application.yml` — Boot's `Duration` binding for `SchedulerProperties` accepts
  ISO-8601 too, so nothing else changed. (Boot's relaxed `"5m"` Duration style applies to
  `@ConfigurationProperties`, *not* to `@Scheduled` string parsing.)
- (Also fixed a test-only Mockito `UnfinishedStubbingException` — a stubbing helper was called *inside* a
  `when(...).thenReturn(...)`; hoisted the tenant-mock creation out.)

## 5. Tests
- **`SubmissionStatusPollerTest`** (6, Mockito; the mocked `RetryService` runs the supplied call so
  `refreshStatus` is really invoked): polls SUBMITTED across two tenants + asserts `TenantContext` held the
  right schema *during* each call and is null after; per-report failure skipped, cycle continues; per-tenant
  failure skipped, next tenant scanned; disabled-flag short-circuits (no tenant query); `scheduledPoll`
  swallows a thrown discovery error; enabled `scheduledPoll` runs a cycle. An `@AfterEach` asserts
  `TenantContext` is clear after every test.

## 6. Verification
`./gradlew test jacocoTestCoverageVerification` → **BUILD SUCCESSFUL**; the 6 poller tests pass; full suite
green (context loads with the poller bean); gate holds (`scheduler/**` fully covered). `git status` scoped to
Phase 9.3 files.

---

## Outcome
✅ The poller works end to end at the unit level and the context boots with scheduling live (disabled in
tests). Next: **9.4** — a Testcontainers integration test (two tenants, real persistence, mocked b2b) +
docs/planning sync to close Phase 9.
