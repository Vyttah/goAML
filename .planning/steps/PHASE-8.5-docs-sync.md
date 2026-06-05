# Phase 8.5 — Docs & planning sync (close out Phase 8)

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-8-s3-attachments.md](../plans/phase-8-s3-attachments.md). Docs-only — no code.

---

## 1. Goal
Bring `docs/` + `.planning/` in line with the built Phase 8 attachment feature, record the deferred
AV-scanning gap, and give the developer a copy-paste way to exercise attachments locally.

## 2. What changed
- **`docs/07`** — `V3__attachments.sql` (`attachment` table) documented; entities table gains
  `Attachment` (note: distinct from the engine `engine.packaging.Attachment`); repository list extended
  (report/submission/attachment/goamlconfig).
- **`docs/06`** — attachment endpoints added to the REST table; the proxy-upload + freeze-on-submit note;
  exception-mapping note extended (attachment 404/409/400, packaging 422).
- **`docs/02`** — `integration/aws/S3StorageClient` marked built; `service`/`controller` rows gain
  `attachment`.
- **`docs/03`** — the local-testing section gains an **Attachments** block (`aws s3 mb` + curl multipart
  attach/list/remove) and a pointer to the AV-scanning gap.
- **`docs/09`** — Phase 8 ✅ (commit range), Phase 9 ⏭️ next, progress 8/14 (≈57%); §5 gaps updated:
  added **no attachment virus/content scanning** (deferred hardening), refreshed the now-stale Phase-7
  gaps (RBAC enforced; HTTP endpoints exist), noted status tracking is on-demand until Phase 9.
- **`.planning/ROADMAP` / `STATE` / `CLAUDE.md`** — Phase 8 ✅, Phase 9 next, progress 8/14.
- Plan outcome + this step doc filled.

## 3. Verification
Stale-grep for "Phase 8 … next" / "⏭️ … 8" over docs/ + .planning/ clean; `git status` docs/planning only;
suite still green from 8.4.

---

## Outcome
✅ Phase 8 is closed. S3 attachments are reachable over REST, pulled into the submission ZIP, E2E-proven,
documented, and locally testable; the AV-scanning deferral is recorded. Next: **Phase 9 — `scheduler/`**
(async status poller + retry across tenants).
