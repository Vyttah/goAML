# Phase 12.7 — Packaging, marketplace, docs + merge (Phase 12 close)

> **Status: ✅ DONE (2026-06-07).** Final step of Phase 12 (the last phase). Makes the plugin installable,
> documents the MCP/plugin/CLI surface, lands the carried-forward infra touch-up, syncs the durable project
> memory, and merges `phase-12/plugin-mcp` → `main`.

---

## 1. Packaging / marketplace
- **`.claude-plugin/marketplace.json`** (repo root) — marketplace `vyttah-goaml` listing the `goaml` plugin with
  `source: ./plugin/goaml`, versioned `0.1.0` (matches the plugin manifest + the MCP tool contract). Installable
  via `/plugin marketplace add <this repo>` → `/plugin install goaml@vyttah-goaml`.
- The plugin itself (`plugin/goaml/`, from 12.3–12.6) has its manifest, `.mcp.json`, skill, 5 commands, hook,
  and README.

## 2. Carried-forward infra touch-up (from Phase 14) — DONE
- **Helm ingress** — the `/` prefix rule already routes `/api/v1/mcp/**`; added a comment + `values.yaml`
  guidance for the **SSE** annotations (`nginx.ingress.kubernetes.io/proxy-buffering: "off"` +
  `proxy-read-timeout`) the MCP stream needs.
- **Dockerfile** — documented that the same image runs the CLI via `--cli` (override the args).

## 3. Docs / planning sync
- **`docs/13-plugin-mcp-cli.md`** — new developer doc: the MCP server (transport/auth/tenancy/RBAC + context
  propagation), the submission safety harness, the plugin, and the CLI (+ the dual-mode conditionals).
- **`ROADMAP.md`** — Phase 12 → ✅ done with a "what shipped" section; the table row updated.
- **`STATE.md`** — Current Position (14/14 complete), Next Action (no next build phase; Phase 1.5 + go-live
  prerequisites), progress `14/14`, session continuity entry.
- **`CLAUDE.md`** (root) — status line flipped to "ALL 14 phases committed; Phase 12 done".
- **`discussion-log.md`** — Phase 12 entry (decisions + the 7 steps + honest scope).
- **Plan Outcome** filled in `plans/phase-12-plugin-and-mcp-harness.md`.

## 4. Verification
- `./gradlew test jacocoTestCoverageVerification` green (full suite incl. the MCP + CLI ITs); plugin +
  marketplace JSON validated; `git status` scoped to packaging/docs/planning + the infra touch-up.

## 5. Merge
- `phase-12/plugin-mcp` → `main` (local, no-ff), matching the per-phase ritual. **Phase 12 — and the standalone
  build — is complete.**

---

## Outcome
✅ Phase 12 complete, documented, and merged. **The goAML standalone product is done: 14/14 phases.** It builds,
validates, submits, and tracks DPMSR reports to the UAE FIU, with a React SPA, deployable infra, and three
parity surfaces (REST, MCP, CLI). Remaining is **not** standalone-build work: Phase 1.5 (suite integration +
federated auth, deferred) and external go-live prerequisites — an AWS account/EKS/ECR/RDS, a GitHub remote + CD
secrets, the **PII-sample git-history purge before any first push**, and per-tenant FIU creds + real UAE
lookup/BRR exports.
