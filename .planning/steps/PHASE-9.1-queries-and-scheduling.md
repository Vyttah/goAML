# Phase 9.1 — Repository queries + scheduling enablement

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-9-scheduler.md](../plans/phase-9-scheduler.md). First step of Phase 9.

---

## 1. Goal & why
Lay the groundwork the poller needs before any polling logic: the **discovery queries** (which tenants,
which reports) and **turning Spring scheduling on** with bound config. No poller yet (that's 9.3).

## 2. What was built

| File | Role |
|---|---|
| `repository/report/ReportRepository` | `+ findByStatus(String)` (tenant-scoped) — the poller queries `SUBMITTED`. |
| `repository/tenant/TenantRepository` | `+ findByStatus(String)` (shared `public`) — enumerate `ACTIVE` tenants to scan. |
| `config/scheduler/SchedulerConfig` | `@Configuration @EnableScheduling @EnableConfigurationProperties(SchedulerProperties.class)`. |
| `config/scheduler/SchedulerProperties` | record bound from `goaml.scheduler.*`: `status-poll.{enabled, interval}` + `retry.{max-attempts, backoff}` (Durations). |
| `application.yml` | `goaml.scheduler.*` keys (only new keys), poller **enabled by default** (5m interval, 3 attempts, 2s backoff). |
| `src/test/resources/application.properties` (new) | Test-only override: `goaml.scheduler.status-poll.enabled=false` — keeps the poller off the timer in every `@SpringBootTest`. |
| `build.gradle` | Added `com/vyttah/goaml/scheduler/**` to the JaCoCo `coveredPackages`. |

## 3. Key understanding / decisions
- **`@EnableScheduling` lives in its own `config/scheduler/SchedulerConfig`**, not on the main app class —
  keeps the entry point clean and groups the scheduler wiring.
- **Test safety via a merged property file, not a replacement.** `src/test/resources/application.properties`
  is *merged over* `src/main/resources/application.yml` (properties win on conflicts) — it overrides only
  the one `enabled` key, so the datasource/JPA/etc. config is untouched. This stops the (9.3) poller from
  firing on its timer during unrelated `@SpringBootTest`s; the poller's own integration test (9.4) will
  invoke the poll method directly.
- **`config/scheduler/**` deliberately NOT added to the coverage gate** — `SchedulerConfig` +
  `SchedulerProperties` are trivial wiring/records (no branches); only the logic-bearing `scheduler/**`
  (RetryService + poller, 9.2–9.3) is held to the ≥90%/≥80% bar.
- **Durations use the suffix notation** (`5m`, `2s`) — bound to `java.time.Duration`; `@Scheduled`
  (9.3) reads the interval via `fixedDelayString`.

## 4. Tests
- **`SchedulerQueriesTest`** (Testcontainers): `ReportRepository.findByStatus("SUBMITTED")` returns only
  the SUBMITTED rows in the bound tenant (and empty for ACCEPTED); `TenantRepository.findByStatus("ACTIVE")`
  contains a freshly-provisioned tenant and excludes it from `"DELETED"`.
- **`SchedulerPropertiesTest`** (Binder, no context): `5m`→`Duration.ofMinutes(5)`, `2s`→`ofSeconds(2)`,
  `max-attempts`/`enabled` bind correctly.

## 5. Verification
`./gradlew test jacocoTestCoverageVerification` → **BUILD SUCCESSFUL**; the new tests pass; full suite green
(the test property override loads cleanly with `SchedulerConfig` on the context). `git status` scoped to
Phase 9.1 files.

---

## Outcome
✅ Discovery queries + scheduling subsystem are in place (poller still dormant). Next: **9.2** — the
`RetryService` (transient-only, bounded, stubbed backoff in tests).
