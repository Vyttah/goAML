# Phase 7.4 — Docs & planning sync (close out Phase 7)

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-7-persistence-service-web.md](../plans/phase-7-persistence-service-web.md).
> Docs-only — no code changes.

---

## 1. Goal
Bring `docs/` + `.planning/` in line with the built Phase 7 report API, and give the developer a
copy-paste way to exercise it locally.

## 2. What changed
- **`docs/07`** — `V2__reports.sql` (`report` + `submission` tables) documented; entities table gains
  `TenantGoamlConfig`, `Report` (note: distinct from the JAXB `Report`), `Submission`.
- **`docs/06`** — report endpoints added to the REST table; RBAC note updated (submit = MLRO); the
  service-exception → HTTP mapping noted.
- **`docs/02`** — `service/` + `controller/` rows include `report`/`submission`.
- **`docs/03`** — new **"Trying the DPMSR report API locally"** section: curl for login/create/list, plus
  the **local seed** (a `tenant_goaml_config` row + a LocalStack Secrets Manager secret + a stub `base_url`)
  needed for hands-on submit.
- **`docs/09` / `ROADMAP` / `STATE` / `CLAUDE.md`** — Phase 7 ✅, Phase 8 next, progress 7/14 (≈50%).
- Plan outcome + this step doc filled.

## 3. Verification
Stale-grep for `Phase 7 … next` over docs/ clean; `git status` docs/planning only; suite still green from 7.3.

---

## Outcome
✅ Phase 7 is closed. The DPMSR report lifecycle is reachable over REST, E2E-proven, documented, and
locally testable. Next: **Phase 8 — S3 attachments**.
