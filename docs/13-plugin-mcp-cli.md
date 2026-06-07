# 13 — Claude plugin, MCP server & CLI (Phase 12)

Phase 12 gives goAML two extra surfaces over the **same engine/services** the REST API uses — an **MCP
server** (so Claude can drive goAML by natural language) and a **CLI** (the same jar in `--cli` mode) — plus a
distributable **Claude plugin**. The guiding principle: the MCP and CLI layers add **no business logic**; each
tool/command delegates to an existing service, so REST, MCP, and CLI stay at parity (same validation, same
RBAC, same audit).

> See also: [02 architecture](02-system-architecture.md), [05 engine](05-engine.md),
> [06 security](06-multitenancy-and-security.md), [10 B2B protocol](10-b2b-submission-protocol.md).

## The MCP server (`mcp/`)
Built into the Spring Boot app with **Spring AI 1.0.2** (`spring-ai-starter-mcp-server-webmvc`, MCP SDK
0.10.0). It serves an SSE transport at **`/api/v1/mcp/sse`** (messages at `/api/v1/mcp/message`), behind the
same auth as `/api/**`.

- **Auth + tenancy:** the MCP client sends the tenant-scoped JWT (`Authorization: Bearer …`). The existing
  `JwtAuthFilter` authenticates the call exactly like REST → `TenantContext` (schema routing) + the principal.
  Because the MCP sync server runs tools on a Reactor scheduler thread, `McpContextPropagationConfig`
  registers `ThreadLocalAccessor`s for the SecurityContext + `TenantContext` and enables Reactor automatic
  context propagation, so the tool executes as the caller's tenant + role.
- **RBAC:** each tool calls `McpIdentity.requireAnyRole(...)` (the MCP edge's `@PreAuthorize`), mirroring the
  REST controllers' roles.
- **Tools:** `goaml_whoami`/`goaml_ping`; reference data (`goaml_list_jurisdictions`,
  `goaml_list_lookup_sets`, `goaml_list_lookups`, `goaml_describe_report_type`); DPMSR lifecycle
  (`goaml_validate_dpmsr`, `goaml_preview_dpmsr_xml`, `goaml_create_dpmsr`, `goaml_list_reports`,
  `goaml_get_report`); **guarded** FIU ops (`goaml_submit_report`, `goaml_get_fiu_status`,
  `goaml_post_message`); import (`goaml_import_xml`, `goaml_import_csv`, `goaml_list_imports`,
  `goaml_get_import`); admin (`goaml_provision_tenant`, `goaml_list_tenants`, `goaml_create_user`,
  `goaml_list_users`, `goaml_get_goaml_config`, `goaml_set_goaml_config`).

### The submission safety harness (the most important rule)
**An agent never silently files a regulatory report.** `goaml_submit_report` is, in order: MLRO-gated →
confirmation-gated (a real send needs `dryRun=false` AND `confirm=true`) → validate-first (only `VALID`
submits) → dry-run by default (returns the exact XML, sends nothing). FIU rejection/transport failures come
back as structured results. `goaml_post_message` is likewise MLRO + `confirm=true`.

## The Claude plugin (`plugin/goaml/`)
A standard plugin: `.claude-plugin/plugin.json`, `.mcp.json` (remote SSE server using `${GOAML_MCP_URL}` +
`${GOAML_MCP_TOKEN}`), a `goaml` **skill** (teaches the DPMSR domain + the safe workflow), **commands**
(`/goaml-build`, `/goaml-validate`, `/goaml-submit`, `/goaml-status`, `/goaml-import`), and a **pre-submit
hook** (a visible reminder; the binding interlock is server-side). Distributed via the repo-root
`.claude-plugin/marketplace.json` (marketplace `vyttah-goaml`). Install + token/URL setup:
[plugin/goaml/README.md](../plugin/goaml/README.md).

## The CLI (`cli/`)
The **same jar** run as `java -jar goaml.jar --cli <command>` boots a non-web Spring context and runs picocli
commands against the same services: `validate`, `preview`, `submit` (same dry-run/confirm/MLRO harness),
`status`, `import`, `lookups`. The operator supplies a JWT via `--token` (or `GOAML_TOKEN`); `CliAuthenticator`
binds the SecurityContext + `TenantContext`. The container image supports it by passing `--cli` as the args.

> Dual-mode note: `SecurityConfig`, `DefaultAuthService`, and `AuthController` are
> `@ConditionalOnWebApplication(SERVLET)` so the non-web CLI context loads; `JwtProperties` is registered on
> `GoamlApplication` so it exists in both modes.

## Connecting (deployed)
Expose the backend behind the Helm ingress (the `/` rule already covers `/api/v1/mcp/**`); for the SSE stream
set `nginx.ingress.kubernetes.io/proxy-buffering: "off"` + a long `proxy-read-timeout`. Point the plugin's
`GOAML_MCP_URL` at `https://<host>/api/v1/mcp/sse` and `GOAML_MCP_TOKEN` at a login JWT.
