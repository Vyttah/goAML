# Phase 12.6 — CLI (picocli) parity (`--cli` run-mode of the same jar)

> **Status: ✅ DONE (2026-06-07).** Adds a terminal surface to the same jar: `java -jar goaml.jar --cli …`
> boots a non-web Spring context and runs picocli commands that delegate to the **same services** as REST and
> MCP — parity by construction, with the same safety harness on submit.

---

## 1. What shipped
- **`--cli` switch** in `GoamlApplication.main`: when the first arg is `--cli`, the jar boots with
  `WebApplicationType.NONE` (no server, no MCP, no SPA), runs the command via `GoamlCliRunner`, and exits with
  its status code.
- **`cli/` package:**
  - `GoamlCliRunner` — runs the picocli tree with a Spring-backed `IFactory` that creates a **fresh** instance
    per invocation (`createBean`) — picocli commands hold per-invocation parse state, so they must not be
    reused singletons. A clean execution-exception handler maps thrown errors to a one-line message + exit 2.
  - `CliAuthenticator` — resolves the operator's JWT (`--token` / `GOAML_TOKEN`) to a principal and binds the
    SecurityContext + `TenantContext` — the same resolution as `JwtAuthFilter` / MCP. `requireRoles` enforces
    RBAC at the CLI edge.
  - `GoamlCli` (root, `--token` as an **inherited** option so it works before or after the subcommand) +
    `cli/command/`: `AbstractGoamlCommand` (authenticate → role-check → run → always clear) and
    `validate`, `preview`, `submit`, `status`, `import`, `lookups`.
- **Submit parity:** `SubmitCommand` carries the **same harness** as the MCP tool — MLRO-only, dry-run unless
  `--send`, real send needs `--send --confirm`, only VALID submits — with distinct exit codes
  (0 ok/dry-run, 1 not-submittable/rejected/transport, 2 refused).

## 2. Required change to make the jar dual-mode (web + CLI)
Booting the full context with `web-application-type=none` initially failed: `SecurityConfig`
(`filterChain(HttpSecurity)` + `authenticationManager`) and `DefaultAuthService`/`AuthController` (which need
the `AuthenticationManager`) are servlet-web concerns. Fixed by annotating those three with
**`@ConditionalOnWebApplication(type = SERVLET)`** so they load only in the web app; the CLI authenticates via
`CliAuthenticator` instead. `JwtProperties` (needed by the always-on `JwtService`) was moved from
`@EnableConfigurationProperties` on `SecurityConfig` to **`GoamlApplication`** so it is registered in both
modes. Web tests are unaffected (in a servlet test the condition is true → identical behavior — verified by the
full suite staying green).

## 3. Tests
- `CliAuthenticatorTest` — token → bound context + principal; role enforcement; cleanup; bad token throws.
- `SubmitCommandTest` — the harness via `AbstractGoamlCommand`: dry-run default, send-without-confirm refused
  (exit 2), non-VALID not submittable (exit 1), confirmed send files (exit 0); context always cleared.
- `GoamlCliIT` (`@SpringBootTest(webEnvironment = NONE)` + Testcontainers) — boots the real non-web context and
  runs `lookups` through the Spring-backed picocli factory with a minted token (exit 0), plus the no-token
  (exit 2) and unknown-command (non-zero) paths. This proves the `--cli` wiring + auth + a real command
  end-to-end. (`lookups` needs no tenant row — it reads the bundled jurisdiction config.)

## 4. Notes / scope
- The `cli/**` package is **not** under the JaCoCo 90% gate: the bootstrap glue (`main` `--cli` switch,
  `System.exit`, the Spring `IFactory`) is process-level and not unit-testable for coverage. The command logic
  + authenticator have unit + IT coverage. (Consistent with the gate covering product-core packages.)
- `lookups sync` (FIU refresh) is **not** a CLI command — same reason as 12.5: no backend sync exists.
- Harmless shutdown-time `WebMvcSseServerTransportProvider` warnings ("response recycled") appear when MCP IT
  clients disconnect; not failures.

## 5. Verification
- `./gradlew test jacocoTestCoverageVerification` → **BUILD SUCCESSFUL** (full suite incl. CLI IT; web tests
  green after the conditional changes).

## Outcome
✅ The same jar now offers three parity surfaces over one engine/service core: **REST**, **MCP**, and **CLI** —
all tenant-scoped, role-gated, and (for submit) dry-run/confirm-guarded. Next: **12.7** — packaging,
versioning, marketplace + install docs, and the merge of Phase 12 to `main`.
