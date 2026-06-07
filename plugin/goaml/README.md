# goAML Claude plugin

Connect Claude to your **goAML platform** tenant and drive the AML report lifecycle — build, validate,
preview, and read **DPMSR** (UAE precious-metals dealer) reports — by natural language, safely. The plugin
bundles:

- a **skill** (`goaml`) that teaches Claude the goAML domain (report shapes, the UAE AED 55,000 rule, the
  required DPMSR fields, and the safe build→validate→preview→create→submit workflow);
- slash **commands** `/goaml-build` and `/goaml-validate`;
- the **MCP server connection** to your goAML backend (tenant-scoped, role-aware).

> Imports and admin tools are added in later plugin versions. **Submission is supported but guarded:**
> nothing is ever filed to the FIU without a dry run, an explicit `confirm=true`, and the **MLRO** role — the
> server enforces this, and a pre-submit hook adds a visible reminder.

## Prerequisites

- A running goAML backend with the MCP server enabled (`GOAML_MCP_ENABLED=true`, the default). The MCP SSE
  endpoint is served at **`/api/v1/mcp/sse`**, behind the same auth as the REST API.
- A **tenant-scoped bearer token** for your goAML user (the same JWT the REST API issues — obtain it from
  `POST /api/v1/auth/login`). Your token's **role** determines what you can do: `ANALYST`/`MLRO` can
  build/validate/create; reads also allow `TENANT_ADMIN`.

## Configure

The plugin's MCP connection reads two environment variables:

| Variable | What | Example |
|----------|------|---------|
| `GOAML_MCP_URL` | The full SSE endpoint URL of your goAML backend | `https://goaml.example.com/api/v1/mcp/sse` |
| `GOAML_MCP_TOKEN` | Your tenant-scoped bearer token (no `Bearer ` prefix) | `eyJhbGciOi…` |

Set them in your shell before starting Claude, e.g.:

```bash
export GOAML_MCP_URL="https://goaml.example.com/api/v1/mcp/sse"
export GOAML_MCP_TOKEN="$(curl -s https://goaml.example.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"analyst@yourtenant.com","password":"…"}' | jq -r .accessToken)"
```

For local development against a backend on `localhost:8080`, use
`GOAML_MCP_URL=http://localhost:8080/api/v1/mcp/sse`.

## Use

Once connected, start with **"who am I on goAML?"** (the `goaml_whoami` tool) to confirm your tenant and
role. Then:

- `/goaml-build` — interview-style: assemble a DPMSR, validate it, preview the XML, and (on your go-ahead)
  save a draft.
- `/goaml-validate <report or details>` — validate a DPMSR and get the failures explained in plain language.
- `/goaml-submit <report id>` — **MLRO only**: dry-run → human review → confirmed, irreversible FIU filing.
- `/goaml-status <report id>` — poll and explain a submitted report's FIU status.

Or just ask in natural language ("build a DPMSR for a AED 90,000 gold sale to Acme Trading FZE and check
it") — the `goaml` skill guides Claude through the correct, safe workflow.

## Security notes

- Every tool call runs as **your tenant, with your role** — cross-tenant access is impossible.
- The token is yours, scoped, and revocable; it is the only credential the agent holds. The platform's FIU
  B2B credentials never pass through Claude.
- Report data contains **PII** (names, IDs). Keep it minimal and handle it per your AML obligations.
- A token is short-lived; refresh `GOAML_MCP_TOKEN` when it expires (the connection will start returning
  auth errors).
