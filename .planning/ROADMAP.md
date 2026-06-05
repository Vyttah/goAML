# ROADMAP — goAML Platform

> The 14-phase build order with status. Each phase is independently testable and lands as one git commit.
> Update the Status column when a phase completes (and mark it in [STATE.md](STATE.md)).
> Detail + Phase 6 breakdown: [docs/09-build-order-and-roadmap.md](../docs/09-build-order-and-roadmap.md).

| # | Phase | Status | Commit |
|---|-------|--------|--------|
| 1 | **Skeleton** — Gradle, Spring Boot 3.3/Java 21, package layout, Dockerfile, docker-compose, Flyway shared baseline, Actuator health | ✅ done | `8764ed3` |
| 2 | **Multi-tenancy + security foundation** — shared entities, Hibernate SCHEMA multi-tenancy, tenant provisioning + per-tenant Flyway, JWT + RBAC + audit | ✅ done | `1f1933e` |
| 3 | **`domain/`** — JAXB POJOs for the `<report>` tree + round-trip/ordering tests | ✅ done | `8ea6fdf` |
| 4 | **`engine/` builders + marshaller** + golden-file XML per report type | ✅ done | `220b763` |
| 5 | **`engine/` validation + UAE jurisdiction + lookups** | ✅ done | `102484d` |
| 6 | **`integration/aws/` Secrets Manager + Redis B2B token cache → `b2b/` goAML REST client; LocalStack/Redis/WireMock tests + JaCoCo ≥90% gate** (Secrets-only; S3/SES → 8/10) | ✅ done | `e6a03d6`…`81f61b0` |
| 7 | **`persistence/` + `service/` + `web/`** DPMSR reports/submissions REST — wires the engine + b2b to HTTP (Testcontainers + WireMock E2E; JaCoCo gate) | ✅ done | `154a2f5`…`82af99f` |
| 8 | **S3 attachments** — `S3StorageClient` + `attachment` table; multipart upload (proxied through the API) → S3, pulled into the submission ZIP; attach/list/remove REST; LocalStack IT + E2E (AV scanning deferred) | ✅ done | `07afd21`…`77de56e` |
| 9 | **`scheduler/`** async poller + `RetryService` across tenants; status transitions | ⏭️ **next** | — |
| 10 | **`notification/`** in-app + SES email (LocalStack SES) | ⬜ todo | — |
| 11 | **`ingestion/`** generic inbound REST + file import (goAML XML + CSV) | ⬜ todo | — |
| 12 | **goAML Claude Plugin & MCP harness** + `cli/` — full plugin so users connect Claude and drive all goAML features safely. Plan: [plans/phase-12-plugin-and-mcp-harness.md](plans/phase-12-plugin-and-mcp-harness.md) | ⬜ todo (planned) | — |
| 13 | **React frontend** — auth → dashboard → report builder → detail/track → import → lookups → admin → notifications | ⬜ todo | — |
| 14 | **Infra** — Dockerfile finalize, Helm chart, observability baseline, GitHub Actions CI/CD | ⬜ todo | — |
| **1.5** | **Suite integration + federated auth** — RabbitMQ accounting consumer (txn → reportability → auto-create DPMSR draft → MLRO 1-click), screening REST/form push, `/api/v1/auth/federated/token` token-exchange + `external_identity`. Plan: [plans/integration-and-auth-architecture.md](plans/integration-and-auth-architecture.md) | ⬜ todo (planned) | — |

> **Note on Phase 1.5:** the "1.5" label reflects its product priority, but it is **sequenced after the
> standalone core** (it depends on the engine, the b2b client, and submission existing). Deps: RabbitMQ +
> the screening API + Phase 6/7 submission.

## Phase 6 (DONE) — what shipped

- **6a `integration/aws/`** — `GoamlSecretsClient` reads per-tenant goAML creds from Secrets Manager
  (no separate KMS client). **Secrets-only**; `S3StorageClient`/`SesClient` deferred to Phases 8/10.
- **6b `b2b/`** — `GoamlB2bClient`/`RestGoamlB2bClient` (HTTP/1.1) + `TokenManager` (Redis token cache,
  401 re-auth); ops `postReport`/`getReportStatus`/`deleteReport`/`postMessage`/`getLookups`; typed errors.
- Tests: LocalStack + Redis integration (tagged/conditional) **and** Mockito/WireMock unit tests; JaCoCo
  ≥90% gate on the new packages (achieved ~98.7% instr). Per-step docs: `steps/PHASE-6.1..6.5`.

See [docs/10-b2b-submission-protocol.md](../docs/10-b2b-submission-protocol.md) for the wire protocol.

## Detailed phase plans

- **Phase 12** — [plans/phase-12-plugin-and-mcp-harness.md](plans/phase-12-plugin-and-mcp-harness.md):
  full Claude plugin + MCP harness (read/build/validate tools buildable now; submit/track/import need
  Phases 6/7/9/11). Has 4 open decisions to confirm before building.
- **Phase 1.5** — [plans/integration-and-auth-architecture.md](plans/integration-and-auth-architecture.md):
  suite positioning, accounting (RabbitMQ) + screening integration, and the unified-auth token-exchange
  design. Docs-only so far; code sequenced after the standalone core.
- **XSD-first foundation** — [plans/xsd-first-foundation.md](plans/xsd-first-foundation.md): the current
  priority — build the domain over the real goAML 5.0.2 XSD.

## Open external inputs (gate live correctness, not the build)

UAE goAML XSD · per-tenant B2B URLs + credentials · UAE Business Rejection Rules · real UAE lookup
exports · CSV import template · AWS account specifics. Detail: [docs/09 §4](../docs/09-build-order-and-roadmap.md).
