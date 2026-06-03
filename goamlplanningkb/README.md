# goAML Platform

Multi-tenant compliance platform that builds, validates, submits, and tracks goAML reports
to the UAE FIU (first target) via the goAML Web B2B REST interface.

**Stack:** Java 21 · Spring Boot 3.3 · Gradle (Groovy DSL) · PostgreSQL (schema-per-tenant) ·
JWT auth + RBAC + audit · React + TypeScript + Vite + Ant Design (frontend) · AWS
(EKS · RDS · S3 · Secrets Manager + KMS · SES).

**Surfaces:** REST API, React UI, MCP server (for the Vyttah AML Co-Pilot / agents), CLI.

**Suite positioning:** Built as a standalone, sellable product **and** as a dedicated microservice inside
the Vyttah suite (Accounting/ERP + AML Screening). Suite integration + unified (federated) authentication
is **Phase 1.5** — designed in
[`.planning/plans/integration-and-auth-architecture.md`](.planning/plans/integration-and-auth-architecture.md),
scheduled after the standalone core.

**Status:** Phases 1–5 of 14 complete. Next: Phase 6 (AWS integration + B2B client). Test suite green.

## Resuming / onboarding (start here)

Everything needed to understand and continue this project lives **in this repo** — no machine-local
state, so anyone on any machine can resume:

- **[`.planning/STATE.md`](.planning/STATE.md)** — current status & the exact next step. **Read this first.**
- **[`.planning/ROADMAP.md`](.planning/ROADMAP.md)** — the 14-phase build order with status.
- **[`.planning/PROJECT.md`](.planning/PROJECT.md)** — what this is, requirements, locked decisions, constraints.
- **[`.planning/plans/integration-and-auth-architecture.md`](.planning/plans/integration-and-auth-architecture.md)**
  — Phase 1.5 suite integration + unified authentication design.
- **[`.planning/discussion-log.md`](.planning/discussion-log.md)** — running log of architectural discussions.

> Full developer documentation (`docs/`) lives on the maintainer's machine and is **not yet committed** to
> this repo; the `.planning/` directory above is the canonical in-repo knowledge base for now.

## Local development

```bash
# 1. Bring up Postgres (and LocalStack — used from Phase 6 onward)
docker compose up -d postgres

# 2. Run tests (Gradle will auto-provision Java 21 via toolchain)
./gradlew test

# 3. Run the app
./gradlew bootRun
```

The app boots on `http://localhost:8080`; health probe at `/actuator/health`.
