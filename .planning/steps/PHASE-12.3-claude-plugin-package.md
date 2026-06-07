# Phase 12.3 — Claude plugin package (skill + .mcp.json + build/validate commands)

> **Status: ✅ DONE (2026-06-07).** Packages the goAML MCP server as an installable **Claude plugin** so a
> user connects once (token + URL) and drives the DPMSR lifecycle by natural language — with a domain skill
> that teaches Claude to build *correct* reports and the safe workflow.

---

## 1. What shipped (`plugin/goaml/`)
Verified against current Claude Code plugin spec (manifest in `.claude-plugin/`, dirs at plugin root):

- **`.claude-plugin/plugin.json`** — manifest (`name: goaml`, version, description, author, keywords) with
  `"mcpServers": "./.mcp.json"`.
- **`.mcp.json`** — the MCP connection: a **remote SSE** server (`type: sse`) at `${GOAML_MCP_URL}` sending
  `Authorization: Bearer ${GOAML_MCP_TOKEN}`. SSE (not streamable-http) because the backend uses the Spring
  AI WebMvc **SSE** transport at `/api/v1/mcp/sse`. Both vars are env-expanded; nothing hard-coded.
- **`skills/goaml/SKILL.md`** — the harness "brain". Frontmatter description 486/1536 chars. Body teaches:
  whoami-first; the two report shapes (DPMSR is **activity-shaped** — parties + goods, no transactions); the
  UAE rules (AED local currency, **AED 55,000** threshold, unique `entityReference`); the DPMSR required
  fields (mapped to the request); each tool and when to use it; how to read validation messages
  (severity/path/code); and **the golden rule — never submit without a clean validation + explicit human
  (MLRO) confirmation.**
- **`commands/goaml-validate.md`** — `/goaml-validate`: whoami → locate/assemble → `goaml_validate_dpmsr` →
  explain failures in plain language. Read-only (no create/submit).
- **`commands/goaml-build.md`** — `/goaml-build`: interview → assemble DPMSR → validate (fix loop) → preview
  XML → create a **draft** only on user confirmation. Never submits.
- **`README.md`** — install, the two env vars (`GOAML_MCP_URL` = `…/api/v1/mcp/sse`, `GOAML_MCP_TOKEN` = the
  login JWT), role model, and security notes (tenant isolation, token-only credential, PII).

Tool references use the fully-qualified `mcp__goaml__<tool>` names in each command's `allowed-tools`.

## 2. Verification
- Both JSON files parse; the skill description is within the 1536-char cap.
- Plugin layout matches the current spec (`.claude-plugin/plugin.json`; `skills/`, `commands/`, `.mcp.json`
  at root). The Gradle build is unaffected (the plugin lives outside `src/`).
- **Not automatable here:** the plan's "fresh Claude install connects + builds a report from natural
  language" is a live, manual check (needs a running backend + a real token + Claude). The connection
  contract it relies on — auth, tenant/RBAC resolution, tool discovery, and DPMSR-request round-trip over
  the real SSE transport — **is** covered automatically by `GoamlMcpAuthIT` (12.1/12.2), so the plugin's
  server side is proven; only the client-side install is manual.

## 3. Honest scope note
The plan's 12.3 line mentions "DPMSR + STR" end-to-end. The engine currently builds **DPMSR only** (there is
a `TransactionReportBuilder` but no STR request DTO / MCP tool yet), so the skill and commands cover the
**DPMSR** path. STR/other report-type build tools are future work (beyond Phase 12's first-report-type scope).

## 4. Carried forward
- 12.4 adds the submit/status/messages tools + a `/goaml-submit` + `/goaml-status` command + the pre-submit
  hook (the safety interlock the skill already promises).
- 12.7 adds marketplace packaging (`.claude-plugin/marketplace.json`) + versioning to the MCP tool contract.
- Infra touch-up (deferred): expose `/api/v1/mcp/**` on the Helm ingress so `GOAML_MCP_URL` is reachable.

## Outcome
✅ A complete, well-formed Claude plugin: one install + two env vars connects Claude to a goAML tenant, with a
domain skill and `/goaml-build` `/goaml-validate` commands over the read/build/validate/preview tools from
12.2. Next: **12.4** — guarded submit/status tools + safety harness.
