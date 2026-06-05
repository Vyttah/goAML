# Phase 9.2 — RetryService (transient-only, bounded)

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-9-scheduler.md](../plans/phase-9-scheduler.md). Second step of Phase 9.

---

## 1. Goal & why
Give the poller (9.3) a small, well-tested helper that retries **only transient** B2B failures with bounded
attempts + backoff, so a network blip or token expiry mid-poll doesn't lose a status refresh — while a real
validation error fails fast.

## 2. What was built

| File | Role |
|---|---|
| `scheduler/Sleeper` (interface) | Indirection over `Thread.sleep` so backoff is injectable; real bean sleeps, tests pass a no-op. |
| `scheduler/RetryService` | `<T> T retryTransient(String label, Supplier<T> call)` — retries `B2bTransportException`/`B2bAuthException` up to `retry.max-attempts` with `retry.backoff`; rethrows the last on exhaustion; **non-transient exceptions propagate immediately, not retried**. |
| `config/scheduler/SchedulerConfig` | `+ @Bean Sleeper` (real `Thread.sleep`, no-op for ≤0, flags interrupt). |

## 3. Key understanding / decisions
- **Only `B2bTransportException` + `B2bAuthException` are retried** — these are the recoverable ones
  (network / expired token). `B2bValidationException` (the FIU disliked the report) and any other exception
  are **not** made better by waiting, so they propagate on the first occurrence.
- **Backoff is injected, not hard-coded** (`Sleeper`), so tests assert retry behaviour *and* the exact
  number of inter-attempt sleeps without ever waiting — deterministic, fast.
- **`maxAttempts` is floored at 1** so a misconfigured `0` can't NPE on the rethrow / skip the call.
- **N attempts → N−1 sleeps** (no trailing sleep after the final failure).
- `RetryService` is a `@Service`; it is only *invoked* by the poller (9.3), so it has no runtime effect on
  any other flow yet — it just exists as a bean in the context.

## 4. Tests
- **`RetryServiceTest`** (6, no Spring, recording `Sleeper`): success-first-try (0 sleeps); success after 2
  transient failures (2 sleeps); auth failures retried too; exhaust 3 attempts → rethrow last (2 sleeps);
  non-transient (`B2bValidationException`) not retried (1 call, 0 sleeps); single-attempt never sleeps.

## 5. Verification
`./gradlew test jacocoTestCoverageVerification` → **BUILD SUCCESSFUL**; `RetryServiceTest` passes; gate holds
(`scheduler/**` includes `RetryService` + `Sleeper`, fully covered).

> Note: one full-suite run hit a known environmental flake — an embedded Tomcat in `ReportApiE2ETest` died
> mid-request ("Unexpected end of file from server") under the combined load of many `@SpringBootTest` +
> Testcontainers contexts. It passes in isolation and on re-run; 9.2 adds only two beans nothing invokes at
> runtime, so it's not a logic regression. (Candidate future cleanup: a shared-container base test class.)

---

## Outcome
✅ Bounded transient-retry helper is in place and proven. Next: **9.3** — the `SubmissionStatusPoller`
(`@Scheduled`; per-tenant `TenantContext`; reuse `SubmissionService.refreshStatus` via `RetryService`;
never throws out of the scheduled method).
