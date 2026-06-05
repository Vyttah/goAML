# Phase 9 — `scheduler/` (async submission-status poller + retry)

> **Status: 🔲 PROPOSED (2026-06-05) — awaiting your approval before implementation.**
> Roadmap **Phase 9**. Moves submission-status tracking from **on-demand** (`GET …/status`) to
> **proactive**: a periodic poller refreshes the FIU status of submitted reports across all tenants.
> Builds on Phase 7 (`SubmissionService.refreshStatus`) + the Phase 6 b2b client.

---

## 1. What this phase is, and why

Today a report's FIU outcome is only learned when someone calls `GET /api/v1/reports/{id}/status`. The
FIU decides asynchronously (minutes-to-hours after submit), so without a poller a report sits at
`SUBMITTED` until a human checks. Phase 9 adds a **background poller** that, on a schedule, finds every
`SUBMITTED` report in every tenant, refreshes its status via the b2b OData call, and persists the
transition (`ACCEPTED`/`REJECTED`). Transient transport/auth blips during polling are retried with bounded
backoff instead of failing the cycle.

**Scope (your decisions this session):**
- **Poll-only + retry transient poll errors** — the scheduler refreshes status and retries transient
  transport/auth failures during polling. It **does not auto-resubmit** `FAILED` reports; re-submit stays
  a manual MLRO action (zero risk of accidental double-filing to the FIU).
- **Plain `@Scheduled`, no distributed lock** — `@EnableScheduling` + a config-flagged poll loop. Status
  polling is an idempotent OData GET, so concurrent pods are wasteful-but-harmless; ShedLock is noted as a
  Phase 14 (infra) follow-up if write-contention ever matters.

## 2. Decisions

| # | Decision | Choice |
|---|----------|--------|
| D1 | Trigger | Spring `@Scheduled(fixedDelayString = …)` on a config-driven interval; `@EnableScheduling` on a new `config/scheduler/SchedulerConfig`. Disabled by a flag (`goaml.scheduler.status-poll.enabled`, default **true**, but trivially off in tests). |
| D2 | What it polls | Across all **ACTIVE** tenants: each tenant's reports in status `SUBMITTED` that have a submission with a non-null `reportkey`. |
| D3 | Reuse, don't duplicate | The poller calls the existing **`SubmissionService.refreshStatus(reportId, tenantId)`** per report — the FIU call + status mapping + persistence already live there and are tested. The scheduler only adds *discovery + iteration + retry*. |
| D4 | Tenant binding | The poller runs untenanted (no HTTP request). For each tenant it wraps work in `try { TenantContext.set(schema); … } finally { TenantContext.clear(); }` so tenant-scoped repos resolve to the right schema — mirroring the request-time `JwtAuthFilter` pattern. |
| D5 | Retry | A small **`RetryService`** (or `retryTransient(Supplier, maxAttempts)`) wraps each `refreshStatus` call: retry **only** `B2bTransportException`/`B2bAuthException` with bounded attempts + fixed/expo backoff; `B2bValidationException` and everything else propagate (not retried). Per-report failure is logged + skipped — one bad report never aborts the cycle. |
| D6 | New queries | `ReportRepository.findByStatus(String)` (per-tenant) for discovery; `TenantRepository.findByStatus("ACTIVE")` (shared) to enumerate tenants. (Both are thin additions to existing repos.) |
| D7 | No auto-resubmit | `FAILED` reports are **not** retried by the scheduler (D-scope). Re-submit remains the MLRO endpoint. |
| D8 | Config missing / unreachable tenant | A tenant with no `tenant_goaml_config` (→ `TenantConfigMissingException`) or a transport failure after retries is **logged and skipped**; the cycle continues to the next tenant/report. |
| D9 | Observability | Each cycle logs a one-line summary (tenants scanned, reports polled, transitions, skipped+reason); per-report transitions audited via the existing `AuditService` (`REPORT.STATUS_REFRESH`). No new metrics infra (Phase 14). |

## 3. Step breakdown (one commit per step, each green; per-step doc in `steps/`)

### Step 9.1 — Repository queries + scheduling enablement
- **`ReportRepository.findByStatus(String status)`** (tenant-scoped) + **`TenantRepository.findByStatus(String)`**
  (shared). Unit/Testcontainers coverage that each returns the right rows.
- **`config/scheduler/SchedulerConfig`** — `@Configuration @EnableScheduling`; bind
  `goaml.scheduler.*` props (enabled flag, poll interval, retry attempts/backoff) via a `SchedulerProperties` record.
- **`application.yml`** — add only `goaml.scheduler.{status-poll.enabled, status-poll.interval, retry.max-attempts, retry.backoff}`.
- **Tests:** repo query tests (Testcontainers); properties bind test.

### Step 9.2 — RetryService (transient-only, bounded)
- **`scheduler/RetryService`** (or `service/…`): `<T> T retryTransient(String label, int maxAttempts, Supplier<T> call)`
  — retries `B2bTransportException`/`B2bAuthException` with backoff; rethrows the last on exhaustion; never
  retries other exceptions. Pure, deterministic (inject backoff as a no-op/short in tests — no real sleeps).
- **Tests:** succeeds-first-try; succeeds-after-N; exhausts-and-rethrows; non-transient-not-retried. (Mockito
  `Supplier`; backoff hook stubbed so tests don't sleep.)

### Step 9.3 — The poller
- **`scheduler/SubmissionStatusPoller`** — `@Scheduled` method (guarded by the enabled flag): enumerate
  ACTIVE tenants → per tenant bind `TenantContext` → `reportRepository.findByStatus("SUBMITTED")` → per
  report `retryService.retryTransient(() -> submissionService.refreshStatus(id, tenantId))`; catch +
  log + skip per-report and per-tenant failures; emit the cycle summary (D9). **Never** throws out of the
  scheduled method (a thrown exception would suppress future runs).
- **Tests:** unit with mocked `TenantRepository`/`ReportRepository`/`SubmissionService`/`RetryService`:
  polls only SUBMITTED across multiple tenants; a per-report exception is swallowed and the next report
  still polled; `TenantContext` is set then cleared (even on exception); disabled-flag → no work.

### Step 9.4 — Integration + docs/planning sync
- **Integration test** (Testcontainers + mocked `GoamlB2bClient`): seed two tenants, a `SUBMITTED` report
  each, stub the b2b status to `Accepted`/`Rejected`, invoke the poller bean directly (not waiting on the
  timer), assert both reports/submissions transitioned and tenant isolation held.
- **Docs/planning:** `docs/02` (`scheduler/` ✅), `docs/07`/`docs/06` (status lifecycle note: now proactive),
  `docs/09`/`ROADMAP`/`STATE`/`CLAUDE.md` (Phase 9 ✅, Phase 10 next, 9/14); fill the plan outcome +
  per-step docs. Update the docs/09 §5 gap ("status tracking is on-demand only" → resolved).

## 4. JaCoCo / coverage
Add `com/vyttah/goaml/scheduler/**` (+ `config/scheduler/**` if logic lands there) to `coveredPackages` at
the same **≥90% instruction / ≥80% branch** bar. Deterministic unit tests (mocked collaborators, stubbed
backoff — no real sleeps/timers) carry coverage; the Testcontainers integration test proves the wiring.

## 5. What this phase does NOT do
Auto-resubmit of `FAILED` reports (D7); distributed locking / ShedLock (noted for Phase 14); notifications
on status change (Phase 10 — the poller leaves a clean seam to fire them); a status-history table (the
latest status on `report`/`submission` is sufficient now); real FIU calls in tests (b2b mocked); the React
status view (Phase 13).

## 6. How you'll verify it (after this phase)
With `docker compose up -d postgres localstack redis`: the integration test drives the full poll cycle
(two tenants, statuses transition) without waiting on the timer. Manually, with a seeded
`tenant_goaml_config` + a stubbed goAML endpoint, a `SUBMITTED` report flips to `ACCEPTED`/`REJECTED` on the
next poll without anyone calling `GET …/status`.

## 7. Verification
`docker compose up -d postgres localstack redis` → `./gradlew test jacocoTestCoverageVerification` green;
the integration test proves cross-tenant polling + isolation + transition persistence; the poller never
throws out of its scheduled method; `git status` scoped to Phase 9 dirs + the 9.4 docs.

## 8. Notes / to confirm in-step
- **The scheduled method must not propagate exceptions** — Spring suppresses future executions of a
  `@Scheduled` method that throws. All per-tenant/per-report work is try/caught; only logged.
- **Tenant binding hygiene:** always `TenantContext.clear()` in `finally` per tenant, or a pooled thread
  leaks one tenant's schema into the next iteration (same hazard the request filter guards against).
- **Interval default** kept conservative (e.g. 5 min) so local/dev runs aren't chatty; production tuning is
  config-only.
- **Re-poll terminal states:** once `ACCEPTED`/`REJECTED`, a report is no longer `SUBMITTED`, so it drops
  out of the next cycle naturally — no extra "done" flag needed.

---

## Outcome
_(filled at phase close)_
