# Plan — Phase 12: goAML Claude Plugin & MCP Harness

> **Status: ✅ APPROVED (2026-06-07) — the 4 open decisions are resolved (all recommended defaults; see §10).
> The LAST phase.** Elaborates roadmap Phase 12 (`mcp/` + `cli/`) into a full-fledged, distributable
> **Claude plugin + MCP harness** so a user can connect Claude and drive *all* goAML features by natural
> language — safely.
>
> **Stack locked:** Spring AI **1.0.2** MCP server starter **`org.springframework.ai:spring-ai-starter-mcp-server-webmvc`**
> (SSE/streamable-HTTP, auto-discovers `@Tool` methods; verified compatible with Spring Boot 3.3.x / Java 21).
> Pin the version + add a contract test (the starter API has moved fast across milestones).
>
> **Depends on:** Phases 6–11 (the MCP tools are thin wrappers over the already-built `engine/` and the
> upcoming `b2b/`, `persistence/`, `service/`, `scheduler/`, `ingestion/` layers). The plugin can be
> built incrementally: read/build/validate tools first (engine-only, available today), then
> submit/track/ingest tools as those backend phases land.
>
> See also: [docs/02 architecture](../../docs/02-system-architecture.md),
> [docs/05 engine](../../docs/05-engine.md), [docs/06 security](../../docs/06-multitenancy-and-security.md),
> [docs/10 B2B protocol](../../docs/10-b2b-submission-protocol.md).

---

## 1. Goal & the experience we're building

**One sentence:** a compliance officer (or the Vyttah AML Co-Pilot agent) connects Claude to their
goAML tenant and can say *"build a DPMSR for this gold sale, validate it, show me the XML, and submit
it"* — and Claude does it through guarded, auditable tools, never touching the regulator without a
human's explicit go-ahead.

**What the user installs/connects:**
1. A **Claude Code / Claude Desktop plugin** (the *harness*) — bundles domain skills, slash commands,
   and the MCP server connection config. One install, everything wired.
2. That plugin talks to the **goAML MCP server** (a surface of the Spring Boot backend), authenticated
   as the user's tenant with their RBAC role.

**What "all goAML features" means here** — the full lifecycle, exposed as tools:
build → validate → preview XML → package → **submit** → track status → handle FIU messages → manage
attachments → browse/refresh lookups → list jurisdictions → import (XML/CSV) → (for admins) manage
tenants/users/credentials.

---

## 2. Architecture — two artifacts, one backend

```
   ┌─────────────────────── Claude (Code / Desktop / agent) ───────────────────────┐
   │                                                                                │
   │   goAML PLUGIN  (the harness — this plan's deliverable)                        │
   │   ├─ skills/        domain knowledge so Claude builds CORRECT reports          │
   │   ├─ commands/      /goaml-build /goaml-validate /goaml-submit /goaml-status …  │
   │   ├─ .mcp.json      points Claude at the goAML MCP server (transport + auth)    │
   │   └─ hooks/         pre-submit confirmation gate, audit echo                    │
   └───────────────────────────────────┬────────────────────────────────────────────┘
                                        │ MCP (stdio local  OR  streamable-HTTP/SSE remote)
                                        │ Authorization: tenant-scoped token → tenant + role
   ┌────────────────────────────────────▼───────────────────────────────────────────┐
   │  Spring Boot backend  (com.vyttah.goaml)                                          │
   │  mcp/  GoamlMcpServer + @Tool components  ── thin adapter over: ──────────────────│
   │        engine/(build·validate·marshal·zip) · b2b/(submit·status) · service/(orch) │
   │        persistence/ · tenant/(schema routing) · security/(RBAC) · scheduler/      │
   └───────────────────────────────────────────────────────────────────────────────-─┘
```

**Key principle:** the MCP layer adds **no business logic**. Every tool delegates to an existing
service/engine method, so REST, MCP, and CLI stay at **parity** (same validation, same RBAC, same
audit). MCP is just another caller — like the REST controllers.

### Where the MCP server lives
Built **into the Spring Boot app** (not a separate process) using **Spring AI's MCP Server** support, so
it shares the tenant routing, RBAC, audit, and service beans already in place. Two transports:
- **stdio** — for a locally-run jar (developer / single-tenant desktop use).
- **streamable-HTTP / SSE** — for the deployed multi-tenant server on EKS (the primary mode), behind the
  same auth as `/api/v1`.

> ✅ Coordinates verified: `org.springframework.ai:spring-ai-starter-mcp-server-webmvc:1.0.2` (Spring AI
> 1.0 GA renamed starters to `spring-ai-starter-*`). Needs the Spring AI BOM
> (`org.springframework.ai:spring-ai-bom:1.0.2`). Pin it + a contract test (the API moved fast pre-1.0).

---

## 3. How a user connects (transport, auth, tenancy)

This is the part that makes it "just work" **and** stay safe in a multi-tenant compliance system.

- **Auth:** the MCP client sends a **tenant-scoped bearer token** (the same self-managed JWT the REST
  API uses, or a dedicated long-lived **MCP service token** mapped to one tenant + role). The server
  resolves it exactly like `JwtAuthFilter` does today → sets `TenantContext` (schema routing) + the
  RBAC roles for the call. **Every tool call runs as that tenant, with that role.** No cross-tenant
  access is possible.
- **Connection config** ships in the plugin's `.mcp.json`; the user supplies only their token + their
  tenant's base URL (env var, never hard-coded).
- **Role mapping:** `ANALYST` → read/build/validate tools only; `MLRO`/`TENANT_ADMIN` → also submit;
  `SUPER_ADMIN` → admin tools. The server enforces this (same `@PreAuthorize` model); the plugin also
  hides/labels tools by role for UX.

**Recommended default:** per-tenant **MCP service token** (revocable, role-scoped, audited) issued from
the admin surface — cleaner than pasting a short-lived user JWT. (Decision to confirm — see §10.)

---

## 4. The tool catalog (the full feature map)

Tools are split **read-only** vs **write/irreversible**. Write tools require the right role and (for the
dangerous ones) explicit confirmation. All tools return **structured output** (typed JSON), not prose,
so Claude reasons over results reliably.

### Read-only (safe; ANALYST+)
| Tool | Wraps | Returns |
|------|-------|---------|
| `goaml_list_jurisdictions` | `JurisdictionRegistry` | jurisdictions + allowed report codes |
| `goaml_list_lookups` | `LookupService` | a lookup set (countries/currencies/transmode/funds) |
| `goaml_describe_report_type` | engine metadata | required fields + shape for a given `report_code` |
| `goaml_validate_report` | `ReportValidator` | `ValidationResult` (errors + warnings, by path/code) |
| `goaml_preview_xml` | `ReportMarshaller` | the marshalled XML (no submission) |
| `goaml_get_report` / `goaml_list_reports` | `persistence` / `service` | stored reports + status (Phase 7) |
| `goaml_get_submission_status` | `b2b`/`scheduler` | reportkey, FIU status, errors (Phase 9) |
| `goaml_list_messages` | `b2b` MessageBoard | FIU correspondence (Phase 9) |

### Build (creates a draft; low risk; ANALYST+)
| Tool | Wraps |
|------|-------|
| `goaml_build_transaction_report` | `TransactionReportBuilder` (STR/AIFT/ECDDT) |
| `goaml_build_activity_report` | `ActivityReportBuilder` (SAR/AIF/ECDD/DPMSR) |
| `goaml_save_draft` | `service` persist (Phase 7) |
| `goaml_register_attachment` | S3 presigned upload + register (Phase 8) |

### Write / irreversible (guarded; MLRO/ADMIN; explicit confirmation)
| Tool | Wraps | Guardrail |
|------|-------|-----------|
| `goaml_submit_report` | `SubmissionService` → `b2b.postReport` | **validate-first, dry-run default, human confirmation, MLRO role, idempotent on `entity_reference`** |
| `goaml_delete_report` | `b2b.deleteReport` | confirmation + role |
| `goaml_post_message` | `b2b.postMessage` | confirmation |
| `goaml_refresh_lookups` | `LookupSyncService` | admin role |
| `goaml_import` | `ingestion` (XML/CSV) | preview + per-row errors before commit (Phase 11) |
| `goaml_admin_*` (tenants/users/credentials) | admin services | SUPER_ADMIN / TENANT_ADMIN only |

---

## 5. The harness quality bar (why it's "excellent," not just wired)

This is the differentiator. A bare MCP server is easy; a *trustworthy* one for a regulator-facing
compliance system needs:

1. **Validate-before-submit, always.** `goaml_submit_report` runs `ReportValidator` and **refuses** if
   there are ERROR-severity messages — returns them so Claude can fix and retry. No way to submit an
   invalid report.
2. **Dry-run by default.** Submit tools default to `dryRun: true` (builds + validates + shows the exact
   ZIP/XML that *would* be sent). A real submission needs `dryRun: false` **and** a confirmation token.
3. **Human-in-the-loop confirmation** for anything irreversible (submit/delete/credential change). The
   plugin uses a pre-submit hook + a two-step `prepare → confirm` tool pattern so the model can't
   one-shot a submission; a human approves the prepared payload.
4. **Idempotency.** Submit keys on the report's `entity_reference` (already a per-tenant unique idem
   key) so a retried tool call can't double-file.
5. **Structured, typed I/O.** Every tool has a JSON schema for inputs and outputs. Validation results,
   statuses, and errors are structured so Claude doesn't have to parse prose.
6. **Typed error taxonomy surfaced to the model.** `B2bAuthException`/`B2bValidationException`/
   `B2bTransportException` map to clear tool errors ("re-auth", "fix the report — here's why",
   "transient, retry") so the agent reacts correctly.
7. **Audit every agent action.** Each write tool records an `audit_log` row (actor = the token's user,
   `action`, `summary`, correlation id) — so AI-initiated actions are as traceable as UI ones.
8. **Least privilege.** Tools are role-gated server-side; the plugin advertises only the tools the
   token's role can use.
9. **Rate / size limits.** Respect `PackagingLimits` (5 MB/file, 20 MB/report) and add per-token call
   limits to prevent runaway agents.
10. **Determinism aids.** `goaml_describe_report_type` + a domain **skill** (next section) teach Claude
    the required fields and UAE rules *up front*, so it builds correct reports instead of trial-and-error
    against the validator.

---

## 6. The plugin package (the harness layout)

A standard Claude Code plugin so `add` / marketplace install works:

```
goaml-plugin/
├── plugin.json                # name, version, description, author
├── .mcp.json                  # MCP server connection (transport + auth env vars)
├── skills/
│   └── goaml/
│       └── SKILL.md           # domain knowledge: the 7 report types, the 2 shapes, required
│                              #   fields, UAE rules (AED, DPMS 55k), element-ordering traps,
│                              #   "always validate before submit". Teaches Claude to build RIGHT.
├── commands/                  # slash commands = guided workflows over the tools
│   ├── goaml-build.md         # interview the user → build_*_report → preview
│   ├── goaml-validate.md      # validate + explain failures in plain language
│   ├── goaml-submit.md        # validate → dry-run → confirm → submit → return reportkey
│   ├── goaml-status.md        # poll/show submission status + FIU errors
│   ├── goaml-lookups.md       # browse/refresh lookups
│   └── goaml-import.md        # XML/CSV import with row-error preview
├── hooks/
│   └── pre-submit.*           # blocks a real submission without explicit human confirmation
└── README.md                  # install + connect instructions (token, base URL, roles)
```

- **Skill** = the "excellent harness" brain: it front-loads the domain so the agent is competent, not
  just connected. It mirrors [docs/04](../../docs/04-domain-model.md) + [docs/05](../../docs/05-engine.md)
  + the UAE rules, condensed for the model.
- **Commands** = opinionated, safe workflows (the happy paths), so a non-expert user gets good results.
- **Hook** = the safety interlock on submission.

**Distribution:** publish via a plugin marketplace repo (or the Vyttah internal one); version with the
backend's MCP tool contract; document install + token setup in the plugin README.

---

## 7. Security & compliance considerations (this is AML — be explicit)

- **An AI agent must never silently file a regulatory report.** Submission is human-gated by default
  (§5.2–5.3). This is the single most important rule of this phase.
- **Tenant isolation holds through MCP** exactly as through REST (token → `TenantContext` → schema). A
  cross-tenant tool call is impossible by construction.
- **Credentials never pass through the model.** goAML B2B creds stay server-side in Secrets Manager; the
  agent only holds a tenant-scoped app token, which is revocable and audited.
- **Tokens are scoped + revocable + rate-limited**, and every write is audited with the acting identity.
- **PII in reports** (names, Emirates IDs) flows through the model — document this in the plugin README
  and keep payloads minimal; consider redaction in tool *previews* where feasible.

---

## 8. Dependencies & backend work to add

- **Spring AI MCP Server** starter (verify coordinates/version) — for the `mcp/` surface.
- **`mcp/` package**: `GoamlMcpServer` config + `@Tool`-annotated components grouped by area
  (`ReportTools`, `SubmissionTools`, `LookupTools`, `AdminTools`), each delegating to existing services.
- **MCP service-token** issuance + validation (extend `security/`), if we choose service tokens (§10).
- **Per-token rate limiting** + MCP-specific audit actions.
- Tests: MCP tool contract tests + **REST/MCP parity** tests; WireMock for the submit/status tools.

The **plugin** itself (skills/commands/hooks/.mcp.json) is a separate deliverable from the backend
`mcp/` package and can be authored in parallel once the tool schemas are fixed.

---

## 9. Build sub-phases (incremental, each testable)

| Step | Deliverable | Done criteria |
|------|-------------|---------------|
| 12.1 | `mcp/` server scaffold + transport + token auth + tenant/RBAC wiring | MCP server starts; an authed client lists tools; tenant context + role resolved per call; parity test green |
| 12.2 | **Read + build + validate + preview** tools (engine-only — buildable now) | `validate`/`preview_xml`/`build_*`/`list_lookups`/`describe_report_type` work via MCP; outputs structured; match REST/engine results |
| 12.3 | Plugin package: `goaml` **skill** + `.mcp.json` + `/goaml-build` `/goaml-validate` commands | Fresh Claude install connects; builds a valid DPMSR + STR end-to-end from natural language; validation explained |
| 12.4 | **Submit/status/messages** tools + **safety harness** (dry-run, confirm, validate-first, idempotency, audit) + `/goaml-submit` `/goaml-status` + pre-submit hook | Dry-run shows exact payload; real submit requires confirmation + MLRO; invalid report refused; reportkey returned; audited; WireMock tests green (needs Phases 7/9) |
| 12.5 | **Import + lookups-refresh + admin** tools + `/goaml-import` `/goaml-lookups` | XML/CSV import previews row errors before commit; lookup refresh admin-gated; admin tools SUPER_ADMIN-gated (needs Phase 11) |
| 12.6 | **CLI** (picocli) parity (`goaml build|validate|submit|status|import|lookups sync`) | Same operations from the terminal; parity test vs MCP/REST |
| 12.7 | Packaging, versioning, marketplace publish, README + install docs | Plugin installs from marketplace; documented token/role setup; version pinned to MCP contract |

> **Buildable today (no backend deps):** 12.1–12.3 (read/build/validate/preview) ride on the existing
> `engine/`. The submit/track/import/admin tools (12.4–12.5) need their backend phases (6/7/9/11) first.

---

## 10. Decisions — ✅ RESOLVED (2026-06-07, all recommended defaults)

1. **Auth model** — ✅ **per-tenant revocable MCP service token** (role-scoped, audited), issued from the
   admin surface; resolved exactly like the REST JWT (TenantContext + RBAC).
2. **Submission autonomy** — ✅ **human-confirmed, MLRO-gated, dry-run-first** (validate-first, idempotent).
   The agent can never silently file to the FIU.
3. **Plugin target** — ✅ **full Claude plugin + MCP server** (skill + commands + `.mcp.json` + pre-submit hook).
4. **Transport** — ✅ **streamable-HTTP/SSE on EKS** (behind the existing auth) + **stdio for local/desktop**.

---

## 11. Out of scope (for this phase)

- Building new backend business logic — MCP/CLI only wrap existing services (parity, not new behavior).
- Non-UAE jurisdictions (config-only, later).
- The React UI (Phase 13).
- Anything gated on the external open inputs (UAE XSD, live B2B creds, BRRs — see
  [docs/09 §4](../../docs/09-build-order-and-roadmap.md)).
