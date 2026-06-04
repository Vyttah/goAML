# goAML Platform — project orientation

> Auto-loaded every session. Keep this short; it's a map, not the content.

**What:** multi-tenant RegTech platform that builds, validates, submits & tracks **goAML** AML reports to
the **UAE FIU** (goAML Web B2B REST), for many client Reporting Entities. Java 21 · Spring Boot 3.3 ·
Gradle · PostgreSQL (schema-per-tenant) · JWT/RBAC/audit. Sold **standalone** and as part of Vyttah's
suite (accounting/ERP + AML screening).

## Start here (in order)
1. **`.planning/STATE.md`** — where we are / what's next. **Read first.**
2. **`.planning/ROADMAP.md`** — the 14-phase build order + status (and Phase 1.5).
3. **`.planning/PROJECT.md`** — what it is, requirements, **Key Decisions**, constraints.
4. **`.planning/discussion-log.md`** — the running Q&A/decision history (how we got here).
5. **`docs/`** — full developer documentation (business → architecture → domain → engine → security →
   testing → glossary). `docs/00-implementation-plan.md` is the original plan.

## Active plans (`.planning/plans/`)
- **`xsd-first-foundation.md`** — current priority: build the domain over the real `goAMLSchema.xsd`
  (5.0.2) via xjc-generated JAXB + an XSD validation gate. The authoritative XSD + 2 real DPMSR samples
  are vendored at `src/main/resources/xsd/goaml/5.0.2/` and `src/test/resources/samples/`.
- **`integration-and-auth-architecture.md`** — suite positioning, **Phase 1.5** accounting/screening
  integration, and the **unified-auth** design (goAML keeps its own JWT + a federated token-exchange
  on-ramp). Docs-only; code is Phase 1.5.
- **`phase-12-plugin-and-mcp-harness.md`** — the Claude plugin + MCP harness.

## Key facts to not re-derive
- **Status:** Phases 1–7 committed (+ XSD-first foundation + layer-first refactor); next = **Phase 8
  (S3 attachments)**. Phase 7 wired the engine + b2b to HTTP: the **DPMSR report lifecycle REST API**
  (`/api/v1/reports` create/validate/submit/status, MLRO-gated submit) over `report`/`submission` tenant
  tables — so the flow is now manually testable via the API (live submit needs per-tenant FIU config).
- **First report type = `DPMSR`** (precious-metals dealers; cash ≥ AED 55,000). All 17 schema codes later.
- **DPMSR is activity-shaped** (goods + parties, no `<transaction>` block).
- **Auth:** self-managed HS256 JWT, RBAC roles SUPER_ADMIN/TENANT_ADMIN/MLRO/ANALYST; tenant routing via
  the `schema` JWT claim. **FIU B2B creds ≠ user login** (per-tenant, Secrets Manager).
- The `aml-ai-skills` Claude skill is a **different** project (a Python chatbot) — **do not** use its
  build order here.

## Conventions
- Money = `BigDecimal`; timestamps = `OffsetDateTime` (UTC). Flyway owns the schema (`ddl-auto: none`).
- Build/test: `docker compose up -d postgres localstack redis` then `./gradlew test` (Postgres uses
  Testcontainers; the Phase 6 LocalStack/Redis integration tests run against compose and skip if absent).
- Update `.planning/STATE.md` + `ROADMAP.md` when a phase starts/finishes; append `discussion-log.md` for
  decisions. **Commit `.planning/` + `docs/`** — they're the durable project memory.
