# Phase 12.1 — `mcp/` server scaffold + transport + token auth + tenant/RBAC wiring

> **Status: ✅ DONE (2026-06-07).** First step of Phase 12 (the last phase). Stands up the Spring AI MCP
> server inside the Spring Boot app, authenticated and tenant-scoped exactly like the REST API, with a
> baseline tool surface and a real end-to-end SSE-client parity test.

---

## 1. Done criteria (from the plan) — all met
- MCP server starts inside the app ✅
- An authed client lists tools ✅
- Tenant context + role resolved **per call** ✅ (proven by a real client calling `goaml_whoami`)
- Parity test green ✅ (real MCP SSE client, not a mock)

## 2. What was built

**Dependencies (`build.gradle`)**
- `org.springframework.ai:spring-ai-bom:1.0.2` (platform) + `spring-ai-starter-mcp-server-webmvc`
  (→ MCP SDK `io.modelcontextprotocol.sdk:mcp:0.10.0` + `mcp-spring-webmvc`).
- `io.projectreactor:reactor-core` + `io.micrometer:context-propagation` declared explicitly (used
  directly by the context bridge; both arrive transitively via the starter, versions pinned by the BOM).
- Added `com/vyttah/goaml/mcp/**` to the JaCoCo `coveredPackages` (≥90% instr / ≥80% branch gate).

**`mcp/` package (no business logic — thin over existing infra)**
- `GoamlMcpServerConfig` — registers a `ToolCallbackProvider` (`MethodToolCallbackProvider`) over the
  tool beans. Later steps append their tool objects here.
- `mcp/tool/SystemTools` — baseline `@Tool`s: `goaml_ping` (server name/version/ok) and `goaml_whoami`
  (echoes the caller's email + tenant schema + bare roles). Typed record returns (structured JSON).
- `McpIdentity` — resolves the caller from the thread's SecurityContext + `TenantContext` (strips the
  `ROLE_` authority prefix); `current()/require()/hasRole()`. The single seam tools use for tenant + RBAC.
- `McpContextPropagationConfig` — **the key fix** (see §3).

**Config (`application.yml`, new `spring.ai.mcp.server` block only)**
- `type: SYNC`, tool capability only (resources/prompts/completions off), `name/version`, `instructions`.
- `sse-endpoint: /api/v1/mcp/sse`, `sse-message-endpoint: /api/v1/mcp/message` — namespaced under
  `/api/v1` so the existing `/api/**` security rule + `JwtAuthFilter` authenticate every MCP call.
- `enabled: ${GOAML_MCP_ENABLED:true}`.

**Security (`SecurityConfig`)**
- Added an explicit `.requestMatchers("/sse", "/mcp/**").authenticated()` safety net (the configured
  endpoints already fall under `/api/**`; this guards against the prefix ever changing). No new filter —
  the existing `JwtAuthFilter` is the single auth path → **REST/MCP parity by construction**.

## 3. Two non-obvious findings (and the fixes) — important for later steps

1. **`spring.ai.mcp.server.base-url` is NOT honored** for the WebMvc SSE route in this version. With
   `base-url=/api/v1`, the SSE route stayed at the default `/sse` (probe: `GET /api/v1/sse` → 404 with a
   valid token, 401 without — security intercepts `/api/**` before routing). **Fix:** set the explicit
   `sse-endpoint` / `sse-message-endpoint` properties (those *are* honored) to the full `/api/v1/mcp/...`
   paths. Don't use `base-url`.

2. **The MCP sync server hops tool execution off the servlet thread.** `WebMvcSseServerTransportProvider.
   handleMessage` does `McpServerSession.handle(msg).block()` on the servlet thread (where `JwtAuthFilter`
   set the SecurityContext + `TenantContext`), but the sync server runs the `@Tool` method on a Reactor
   scheduler thread — so `goaml_whoami` first returned `authenticated:false` / tenant `null`. **Fix:**
   `McpContextPropagationConfig` registers `ThreadLocalAccessor`s for the SecurityContext and
   `TenantContext` and enables `Hooks.enableAutomaticContextPropagation()`. Reactor captures those
   ThreadLocals at subscription (the servlet thread) and restores them on the tool thread. **Every future
   MCP tool that touches tenant data or RBAC depends on this** — without it, JPA would route to `public`
   and `@PreAuthorize`/role checks would see no authentication.

## 4. Tests
- `McpIdentityTest` (unit) — resolves identity, strips `ROLE_`, fails closed when unauthenticated /
  non-`UserPrincipal` / not-authenticated token; `require()` throws; `hasRole()` both ways.
- `mcp/tool/SystemToolsTest` (unit) — `ping()` server identity; `whoami()` authenticated vs not.
- `GoamlMcpServerContextIT` (`@SpringBootTest` + Testcontainers) — a `McpSyncServer` bean auto-configures
  and the provider registers `goaml_ping` + `goaml_whoami`.
- `GoamlMcpAuthIT` (`@SpringBootTest(RANDOM_PORT)` + Testcontainers) — **the parity test**: a real
  `McpSyncClient` over `HttpClientSseClientTransport` with a tenant-scoped JWT lists the tools and calls
  `goaml_whoami`, asserting it echoes the token's schema (`public`) + roles (`MLRO`,`ANALYST`) + email;
  and an unauthenticated client's `initialize()` is rejected.

## 5. Verification
- `./gradlew test jacocoTestCoverageVerification` → **BUILD SUCCESSFUL** (full suite; the global
  context-propagation hook broke no existing tests).
- `mcp/` coverage: instruction **96.9%** (189/195), branch **87.5%** (7/8) — above the gate.

## 6. Notes / carried forward
- `Hooks.enableAutomaticContextPropagation()` is a global Reactor side effect; verified harmless here
  (the app is servlet-based; Lettuce/AWS paths unaffected). Standard for Micrometer-instrumented apps.
- **Helm/infra touch-up (deferred to 12.6/12.7):** expose `/api/v1/mcp/**` on the ingress + the `--cli`
  run-mode (the Phase-14 carried-forward item).
- Auth currently accepts the **same JWT** the REST API issues (full parity). The plan's per-tenant
  *revocable MCP service token* (a longer-lived, admin-issued, revocable token resolved through the same
  path) is a focused later addition — the resolution seam is already in place.

## Outcome
✅ The goAML MCP server is live inside the app, authenticated + tenant-scoped + RBAC-aware on every tool
call, proven by a real SSE client. Foundation ready for **12.2** (read/build/validate/preview tools).
