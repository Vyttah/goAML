# goAML Platform

Multi-tenant compliance platform that builds, validates, submits, and tracks goAML reports
to the UAE FIU (first target) via the goAML Web B2B REST interface.

**Stack:** Java 21 · Spring Boot 3.3 · Gradle (Groovy DSL) · PostgreSQL (schema-per-tenant) ·
JWT auth + RBAC + audit · React + TypeScript + Vite + Ant Design (frontend) · AWS
(EKS · RDS · S3 · Secrets Manager + KMS · SES).

**Surfaces:** REST API, React UI, MCP server (for the Vyttah AML Co-Pilot / agents), CLI.

**Status:** Phase 1 — project skeleton.

See [`.claude/plans/vectorized-puzzling-planet.md`](../../../../.claude/plans/vectorized-puzzling-planet.md)
in `~/.claude/plans/` for the full implementation plan and build order.

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
