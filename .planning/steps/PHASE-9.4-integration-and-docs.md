# Phase 9.4 — Integration test + docs/planning sync (close out Phase 9)

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-9-scheduler.md](../plans/phase-9-scheduler.md). Closes Phase 9.

---

## 1. Goal
Prove the poller works against real persistence across tenants, then bring docs + planning in line.

## 2. What was built / changed
- **`SubmissionStatusPollerIntegrationTest`** (`@SpringBootTest` + Testcontainers; `@MockBean GoamlB2bClient`):
  seed two tenants, each with a `SUBMITTED` report + a submission carrying a `reportkey` + a
  `tenant_goaml_config` row; stub the FIU status to `Accepted` (A) / `Rejected` (B); invoke
  `poller.pollAllTenants()` directly (not the timer); assert tenant A's report+submission → **ACCEPTED** and
  tenant B's → **REJECTED**, each read back in its own schema (isolation).
- **Docs/planning:** `docs/02` (`scheduler/` ✅), `docs/06` (status now also refreshed proactively by the
  poller), `docs/09` (Phase 9 ✅ + Phase 10 next + 9/14; §5 gap reworded to the deliberate
  no-auto-resubmit non-goal), `.planning/ROADMAP`/`STATE`/`CLAUDE.md`; plan outcome + this step doc.

## 3. Key understanding
- **Direct invocation, not the timer:** the IT calls `pollAllTenants()` so it's deterministic and fast —
  it doesn't wait on the 5-minute `@Scheduled` delay (and the timer stays disabled in tests anyway).
- **Real transition persisted:** unlike the 9.3 unit test (which mocks `refreshStatus`), this drives the
  real `SubmissionService.refreshStatus` → b2b (mocked) → `mapStatus` → DB, proving the whole chain +
  per-tenant schema routing.

## 4. Verification
`docker compose up -d postgres localstack redis` → `./gradlew test jacocoTestCoverageVerification` →
**BUILD SUCCESSFUL**; the IT passes; full suite green; gate holds. Stale-grep for "Phase 9 … next" clean;
`git status` scoped to Phase 9.4 files.

---

## Outcome
✅ Phase 9 is closed. Submission status now updates proactively across tenants, retry-guarded and
E2E-proven. Next: **Phase 10 — `notification/`** (in-app + SES email, fired on the transitions the poller
produces).
