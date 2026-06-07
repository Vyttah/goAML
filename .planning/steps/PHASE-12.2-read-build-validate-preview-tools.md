# Phase 12.2 — Read + build + validate + preview MCP tools (engine-only)

> **Status: ✅ DONE (2026-06-07).** Adds the first real domain tool surface to the goAML MCP server:
> reference-data reads, report-type description, and the DPMSR validate / preview-XML / create / read tools —
> each a thin adapter over the same engine/service the REST API uses, with matching RBAC.

---

## 1. Done criteria (from the plan) — all met
- `validate` / `preview_xml` / `build_*` / `list_lookups` / `describe_report_type` work via MCP ✅
- Outputs are structured (typed records → JSON) ✅
- Results match the REST/engine path (same `ReportService`/engine, same RBAC) ✅

## 2. Tools added (registered in `GoamlMcpServerConfig`)
**Read (require authentication, any role) — `mcp/tool/LookupTools`:**
- `goaml_list_jurisdictions` → `LookupViews.JurisdictionView[]` (reused REST DTO)
- `goaml_list_lookup_sets(jurisdiction)` → sorted set names
- `goaml_list_lookups(jurisdiction, set)` → sorted codes (null-safe)
- `goaml_describe_report_type(reportCode, jurisdiction?)` → shape + conditional-field requirements +
  whether the jurisdiction accepts it + DPMS threshold

**Build/validate/preview + read (RBAC mirrors `ReportController`) — `mcp/tool/ReportTools`:**
- `goaml_validate_dpmsr(request)` → `ReportValidationResult` — build+validate, **no persist** (ANALYST/MLRO)
- `goaml_preview_dpmsr_xml(request)` → `ReportPreview` — marshalled XML that WOULD be sent, **no persist** (ANALYST/MLRO)
- `goaml_create_dpmsr(request)` → `CreateReportResponse` — persists a draft (does **not** submit) (ANALYST/MLRO)
- `goaml_list_reports()` / `goaml_get_report(id)` → `ReportView` (ANALYST/MLRO/TENANT_ADMIN)

## 3. Backend changes (reuse, no new business logic)
- **`engine/metadata/ReportTypeMetadata`** — new single source of truth for report shape
  (TRANSACTION/ACTIVITY) + conditional-field requirements (fiu-ref, location/reason/action). **Refactored
  `ReportValidator`** to consume it (removed its 4 private duplicate `Set`s) so the validator and the
  agent-facing `describe_report_type` can never drift. Validator tests pass unchanged (behaviour-preserving).
- **`ReportService.validate(...)` + `previewXml(...)`** (+ records `ReportValidationResult`, `ReportPreview`)
  sharing a private `buildAndValidate` helper with `create()`. This **closes the Phase-13 backend gap**
  (report XML preview + validation re-fetch had no endpoint) at the service layer, so MCP and a future REST
  endpoint stay at parity.

## 4. RBAC + input safety (the compliance-relevant bits)
- **Role enforcement is explicit** via `McpIdentity.requireAnyRole(...)` at the top of each tool (the MCP
  edge's equivalent of `@PreAuthorize`). Chosen over relying on `@PreAuthorize` proxying through Spring AI's
  `MethodToolCallback` (reflective invocation) — explicit checks are reliable and transport-agnostic.
  `McpAccessDeniedException` surfaces to the client as a tool error naming the required roles (no secrets).
- **Bean-validation short-circuit:** each DPMSR tool runs the JSR-380 `Validator` first (mirroring the REST
  `@Valid`); constraint violations are returned as structured `CONSTRAINT` messages and the engine/service is
  not called — so a malformed request gets a clear, fixable answer instead of an NPE in the mapper.
- `McpIdentity.Identity` gained `tenantId` (from `UserPrincipal`) so tenant-scoped tools resolve the tenant
  exactly like `ReportController` does from the principal.

## 5. One verified design point
Spring AI's `MethodToolCallback` deserialises tool args via `JsonParser`, whose ObjectMapper is built with
`JacksonUtils.instantiateAvailableModules()` → it auto-registers JavaTimeModule (jsr310 is on the classpath).
So the **typed `DpmsrCreateRequest` tool param (with `OffsetDateTime` fields) deserialises correctly** — kept
the rich typed param (better agent schema) rather than a JSON string. The IT proves it end-to-end with a real
date. (A single complex param is exposed under the parameter name, so MCP clients send `{"request": {...}}`.)

## 6. Tests
- Unit: `LookupToolsTest`, `ReportToolsTest` (mock services, real bean `Validator`; happy paths +
  constraint short-circuit + RBAC denial + UUID parsing), `ReportTypeMetadataTest`, expanded `McpIdentityTest`
  (`requireAnyRole`), expanded `DefaultReportServiceTest` (`validate`/`previewXml` don't persist).
- E2E (in `GoamlMcpAuthIT`, real SSE client): `goaml_list_jurisdictions` returns structured data;
  `goaml_validate_dpmsr` deserialises a dated `DpmsrCreateRequest` and returns a structured verdict;
  a TENANT_ADMIN token is refused `goaml_validate_dpmsr` over the wire (RBAC).

## 7. Verification
- `./gradlew test jacocoTestCoverageVerification` → **BUILD SUCCESSFUL**.
- Coverage: `mcp` instruction **99.7%** / branch **84.4%**; `service.report` instruction **97.2%** / branch **100%** — above the gate.

## Outcome
✅ An agent can now, over MCP, discover a tenant's jurisdiction + lookups, learn a report type's shape, and
validate / preview / create / read DPMSR reports — all tenant-scoped and role-gated, delegating to the same
engine/service as the UI. **No submission yet** (that is 12.4, behind the safety harness). Next: **12.3** —
the Claude plugin package (skill + `.mcp.json` + `/goaml-build` `/goaml-validate` commands).
