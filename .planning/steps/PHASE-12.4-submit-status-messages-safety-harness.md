# Phase 12.4 — Submit / status / messages MCP tools + safety harness + pre-submit hook

> **Status: ✅ DONE (2026-06-07).** Adds the irreversible, regulator-facing tools — submit to the FIU, track
> status, post FIU messages — behind a strict safety harness. **The single most important rule of Phase 12:
> an AI agent never silently files a regulatory report.**

---

## 1. Done criteria (from the plan) — met
- Dry-run shows the exact payload ✅ (`xmlPreview` = the stored goAML XML).
- A real submit requires confirmation + MLRO ✅ (`dryRun=false` AND `confirm=true` AND the MLRO role).
- An invalid report is refused ✅ (validate-first — only `VALID` reports submit; also enforced server-side).
- reportKey returned + audited ✅ (delegates to `SubmissionService.submit`, which persists + audits).
- Tests green ✅ (the existing WireMock/Mockito submission tests + new tool unit tests + over-the-wire ITs).

## 2. Tools added (`mcp/tool/SubmissionTools`, registered in `GoamlMcpServerConfig`)
- **`goaml_submit_report(reportId, dryRun?, confirm?)`** — the harness. Layered guards, in order:
  1. **MLRO-gated** (`requireAnyRole("MLRO")`);
  2. **confirmation gate** — a real send (`dryRun=false`) without `confirm=true` is refused **before any
     lookup** (so it's safe and testable with no report);
  3. **load + validate-first** — only `VALID` reports proceed (else a structured refusal naming the status);
  4. **dry run (the default)** — returns the exact XML that *would* be sent, sends nothing;
  5. **real send** — only MLRO + `dryRun=false` + `confirm=true` + `VALID` calls `SubmissionService.submit`.
  FIU rejection / transport failure / missing-config are **caught and returned as structured results**
  (status REJECTED/FAILED + a plain message), not opaque exceptions — so the agent reacts correctly.
- **`goaml_get_fiu_status(reportId)`** — polls the FIU via `refreshStatus`; ANALYST/MLRO/TENANT_ADMIN.
- **`goaml_post_message(message, confirm?)`** — posts to the FIU MessageBoard; **MLRO + confirm=true**.

## 3. Backend change (reuse)
- `SubmissionService.postMessage(message, tenantId, actorUserId)` (+ impl) — resolves the tenant B2B config
  via the existing private `b2bConfig(...)`, calls `b2bClient.postMessage`, and audits `FIU.MESSAGE`
  (**the message body is not logged** — only its length). Transport/auth failures map to
  `SubmissionTransportException`. No other business logic added; submit/refreshStatus are reused as-is.

## 4. Plugin additions (`plugin/goaml/`)
- **`hooks/hooks.json`** — `PreToolUse` reminders on `goaml_submit_report` and `goaml_post_message` that
  print a clear "this files to the FIU / is irreversible / MLRO + confirm required" warning to stderr. The
  **authoritative interlock is server-side** (the tool's guards, tested); the hook is a visible reminder
  (kept non-blocking to avoid fragile client-side JSON parsing).
- **`commands/goaml-submit.md`** (`/goaml-submit`) — whoami → check VALID → dry-run → human review → MLRO
  `confirm=true` send → track. Never one-shots a submission.
- **`commands/goaml-status.md`** (`/goaml-status`) — poll + explain the FIU status.
- README updated (submission supported but guarded; the two new commands).

## 5. Tests
- **Unit** `SubmissionToolsTest` — every guard: MLRO-only; confirm-gate refuses without a lookup; dry-run
  previews + sends nothing; non-VALID refused; confirmed submit returns the key; FIU rejection→REJECTED and
  transport→FAILED structured mapping; status delegation + RBAC; post-message confirm + RBAC.
- **Unit** `DefaultSubmissionServiceTest` — added `postMessage` send+audit and transport-failure cases.
- **E2E** (`GoamlMcpAuthIT`, real SSE client): `goaml_submit_report` is MLRO-gated over the wire (ANALYST →
  error "requires one of roles"); a real submit without `confirm=true` is refused over the wire. (A
  full confirmed submit needs a provisioned tenant + VALID report; that path is covered by the unit tests +
  the existing `DefaultSubmissionServiceTest`/WireMock submission tests.)

## 6. Verification
- `./gradlew test jacocoTestCoverageVerification` → **BUILD SUCCESSFUL**.
- Coverage: `mcp` instruction **97.4%** / branch **86.5%**; `service.submission` instruction **91.1%** /
  branch **83.3%** — above the gate.

## 7. Notes / out of scope
- `goaml_delete_report` (retract a submission from the FIU) is not included here — also irreversible; it would
  follow the same confirm+MLRO pattern and can be added alongside admin tools (12.5) if needed.
- A live confirmed-submit-over-MCP E2E (WireMock FIU + provisioned tenant + report) is heavier than the value
  over the existing submission-service WireMock coverage; deferred unless required.

## Outcome
✅ The MCP server can now drive the *entire* DPMSR lifecycle through to a **guarded** FIU submission: dry-run
by default, MLRO-gated, human-confirmed, validate-first, idempotent, audited — proven by unit tests and
over-the-wire ITs. Next: **12.5** — import + lookups-refresh + admin tools.
