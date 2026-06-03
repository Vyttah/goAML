# Roadmap

Build order for the goAML platform. Phases 1–5 are reconstructed from git history; Phase 6 is the README's
stated "next". **Phases 7–14 detail is reconstructed and should be reconciled with the developer's local
canonical roadmap** — the granularity is right but exact scope per phase may differ.

## Standalone core

| Phase | Scope | Status |
|------:|-------|--------|
| 1 | Project skeleton — Spring Boot 3.3 · Java 21 · Gradle · Postgres · Flyway · Actuator | ✅ done |
| 2 | Multi-tenancy (schema-per-tenant) + JWT / RBAC / audit foundation | ✅ done |
| 3 | `domain/` — JAXB POJOs for goAML schema (v4.0 generation) | ✅ done |
| 4 | `engine/` — builders + marshaller + zip packager + golden XMLs | ✅ done |
| 5 | `engine/` — validation + UAE jurisdiction config + lookups | ✅ done |
| 6 | **AWS integration + goAML Web B2B client** | ⏭️ next |
| 7–14 | Submission → tracking → REST API surface → React UI → MCP server → CLI *(reconstructed — reconcile with local)* | ⬜ planned |

> Surfaces to be delivered across the later phases: REST API, React UI, MCP server (for the Vyttah AML
> Co-Pilot / agents), CLI.

## Cross-cutting / out of standalone sequence

| Phase | Scope | Status | Notes |
|------:|-------|--------|-------|
| **1.5** | **Suite integration + federated auth + `ingestion/`** | ⬜ planned | Accounting (RabbitMQ) + Screening (REST) ingestion, reportability detection, auto-create→MLRO-approve, and federated token-exchange auth. See [`plans/integration-and-auth-architecture.md`](plans/integration-and-auth-architecture.md). **Despite the "1.5" label it is scheduled *after* the standalone core** — it depends on the engine, the B2B client (Phase 6), and submission already existing. |
| — | **XSD-first foundation** | ⬜ planned | Immediate priority, tracked as a separate plan (not yet committed here — reconcile with local copy). Does not change the standalone build order above. |

## Dependency notes

- Phase 1.5 ingestion needs: a working report **engine** (Phases 3–5 ✅), the **B2B client + submission**
  (Phase 6+), `spring-boot-starter-amqp`, and a REST push endpoint for screening.
- Phase 1.5 federated auth reuses the existing `JwtService` and `SecurityConfig`; it adds a shared-schema
  migration `db/migration/shared/V3__federated_identity.sql`.
