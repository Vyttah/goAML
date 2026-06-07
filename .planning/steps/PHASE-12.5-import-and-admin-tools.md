# Phase 12.5 — Import + admin MCP tools (lookups-refresh deferred)

> **Status: ✅ DONE (2026-06-07).** Adds file-import and administrative tools to the MCP server, each a thin,
> role-gated adapter over the existing ingestion + admin services. The planned lookups-refresh tool is
> **deferred** — no backend sync capability exists to wrap (see §3).

---

## 1. Tools added (registered in `GoamlMcpServerConfig`)
**Import — `mcp/tool/IngestionTools`** (RBAC mirrors `ImportController`):
- `goaml_import_xml(filename, xmlContent)` / `goaml_import_csv(filename, csvContent)` — bulk-create draft
  reports from a goAML XML or flat DPMSR CSV; returns an `ImportJobView` with per-row results. The file
  content is passed as text (both formats are text) → UTF-8 bytes. ANALYST/MLRO. **Does not submit.**
- `goaml_list_imports` / `goaml_get_import(jobId)` — read jobs + per-row results. ANALYST/MLRO/TENANT_ADMIN.

**Admin — `mcp/tool/AdminTools`** (RBAC mirrors `AdminController`):
- `goaml_provision_tenant(request)` / `goaml_list_tenants` — **SUPER_ADMIN**.
- `goaml_create_user(request)` / `goaml_list_users` — **TENANT_ADMIN**, scoped to the caller's own tenant
  (tenant id from `McpIdentity`, never a parameter).
- `goaml_get_goaml_config` / `goaml_set_goaml_config(request)` — **TENANT_ADMIN**. The config views expose
  only references (`baseUrl`, `secretsPath`); **FIU credentials never pass through** (they stay in Secrets
  Manager). Admin request bodies are bean-validated (mirrors REST `@Valid`); a violation throws clearly.

All reuse the existing REST view/request DTOs (`ImportJobView`, `AdminViews.*`, `TenantProvisioningRequest`)
and `*View.from(...)` factories — no parallel mapping.

## 2. Plugin
- `commands/goaml-import.md` (`/goaml-import`) — read a file, pick XML vs CSV, import, and explain the per-row
  results; points to `/goaml-submit` for filing (drafts only here).

## 3. Deferred: lookups refresh (honest scope)
The plan's 12.5 line includes a lookups-refresh tool. **It is deferred deliberately:** there is no backend
sync — `LookupService` reads bundled classpath JSON (immutable at runtime) and the only FIU lookup call is the
raw `GoamlB2bClient.getLookups(config)` which returns **unparsed OData and persists nothing**. A real refresh
would need new backend work (parse the OData → persist into a mutable lookup store), which is new business
logic, not "MCP wraps an existing service." Wrapping the raw call would expose a half-feature. Recorded as
future work; the read-only `goaml_list_lookups` (12.2) already serves the bundled sets.

## 4. Tests
- `IngestionToolsTest` — delegation with tenant/actor from the identity; RBAC; list/get; UUID parsing.
- `AdminToolsTest` — SUPER_ADMIN vs TENANT_ADMIN gating; tenant-scoped delegation (own tenant id);
  bean-validation failures; read paths. (Real bean `Validator`, mocked `AdminService`.)
- `GoamlMcpAuthIT` — over the wire: `goaml_list_tenants` is role-gated (an MLRO token is refused with
  "requires one of roles"), proving admin RBAC across the real SSE transport.
- (Fixed a Mockito nested-stubbing trap — building the mock job into a local before `thenReturn`.)

## 5. Verification
- `./gradlew test jacocoTestCoverageVerification` → **BUILD SUCCESSFUL**.
- Coverage: `mcp` instruction **98.0%** / branch **87.0%** — above the gate.

## Outcome
✅ The MCP surface now spans the whole platform: reference data, build/validate/preview, guarded submit/
status/messages, file import, and tenant/user/config administration — every tool tenant-scoped and role-gated.
Next: **12.6** — the CLI (picocli) parity layer.
