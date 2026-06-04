# 09 — Build Order & Roadmap

> The 14-phase plan, what's done, **what Phase 6 entails**, and what remains.
> The full implementation plan is in-repo at [00-implementation-plan.md](00-implementation-plan.md).
> Live status & next step: [`.planning/STATE.md`](../.planning/STATE.md); phase table:
> [`.planning/ROADMAP.md`](../.planning/ROADMAP.md).

---

## 1. How progress is tracked

Progress is tracked **in-repo** by:
- **[`.planning/STATE.md`](../.planning/STATE.md)** — the living "current status / next step" file (read first),
- **[`.planning/ROADMAP.md`](../.planning/ROADMAP.md)** — the 14-phase table with status,
- the 14-phase build order (below), and
- **one git commit per phase** (e.g. `Phase 5: engine/ — validation + UAE jurisdiction + lookups`).

So "where are we?" = read `.planning/STATE.md` (or the git log). Everything is in the repo — no
machine-local state — so anyone on any machine can resume.

> Also note: the `aml-ai-skills` Claude skill describes a **different** component — a Python/FastAPI
> "AML Co-Pilot" chatbot — **not** this Java goAML platform. Don't use that skill's build order here.

---

## 2. The 14 phases

| # | Phase | Status | Commit |
|---|-------|--------|--------|
| 1 | **Skeleton** — Gradle, Spring Boot 3.3/Java 21, package layout, Dockerfile, docker-compose, Flyway shared baseline, Actuator health | ✅ | `8764ed3` |
| 2 | **Multi-tenancy + security foundation** — shared entities, Hibernate SCHEMA multi-tenancy, tenant provisioning + per-tenant Flyway, JWT + RBAC + audit | ✅ | `1f1933e` |
| 3 | **`domain/`** — JAXB POJOs for the `<report>` tree + round-trip/ordering tests | ✅ | `8ea6fdf` |
| 4 | **`engine/` builders + marshaller** + golden-file XML per report type | ✅ | `220b763` |
| 5 | **`engine/` validation + UAE jurisdiction + lookups** | ✅ | `102484d` |
| — | **XSD-first foundation** (migration, not a numbered phase) — generate the domain from the real goAML **5.0.2** XSD via xjc, add the **XSD validation gate**, reconcile lookups (lookup⊆XSD), add the **DPMSR convenience builder** | ✅ | `6911cf6`…`70f9cdb` |
| — | **Vyttah layer-first refactor** — restructure to `controller`/`service`/`repository`/`model` conventions; Lombok + MapStruct on the JPA/web side | ✅ | `8fed6a1`…`e54f64c` |
| 6 | **`integration/aws/` Secrets Manager (per-tenant creds) + Redis token cache → `b2b/` goAML REST client; LocalStack/Redis/WireMock tests + JaCoCo ≥90% gate** | ✅ | `e6a03d6`…`81f61b0` |
| 7 | **`persistence/` + `service/` + `web/`** DPMSR reports/submissions REST — wires the engine + b2b to HTTP (Testcontainers + WireMock E2E) | ✅ | `154a2f5`…`82af99f` |
| **8** | **S3 attachments** (presigned upload, pull into ZIP) — LocalStack | **⏭️ NEXT** | — |
| 9 | **`scheduler/`** async poller + `RetryService` across tenants; status transitions | ⬜ | — |
| 10 | **`notification/`** in-app + SES email (LocalStack SES) | ⬜ | — |
| 11 | **`ingestion/`** generic inbound REST + file import (goAML XML + CSV) | ⬜ | — |
| 12 | **`mcp/` tools + `cli/`** (picocli) | ⬜ | — |
| 13 | **React frontend** — auth → dashboard → report builder → detail/track → import → lookups → admin → notifications | ⬜ | — |
| 14 | **Infra** — Dockerfile finalize, Helm chart, observability baseline, GitHub Actions CI/CD | ⬜ | — |

Progress: **7 / 14 (≈50%)** + the XSD-first foundation + the layer-first refactor.

---

## 3. Phase 6 (DONE) — what was built

Phase 6 connected the (already-built, tested) engine to the outside world: AWS for secrets, and the goAML
B2B REST surface. It built **two independently-tested pieces** as standalone libraries (not yet wired to an
HTTP endpoint — that's Phase 7). Detail per step: [`.planning/steps/PHASE-6.1..6.5`](../.planning/steps/).

### 6a. `integration/aws/` — Secrets Manager (tested vs LocalStack)
- **`GoamlSecretsClient`** / `DefaultGoamlSecretsClient` — fetches a tenant's goAML credentials
  (`GoamlCredentials` JSON) from the `secrets_path` in `tenant_goaml_config`. **No separate KMS client** —
  Secrets Manager decrypts on `GetSecretValue`. (Named `GoamlSecretsClient`, not `SecretsManagerClient`, to
  avoid a clash with the AWS SDK type.)
- **Scope:** Secrets Manager only this phase. `S3StorageClient` (Phase 8) + `SesClient` (Phase 10) land when
  first used.
- AWS SDK v2 added to `build.gradle`; tested against the docker-compose **LocalStack** (`:4566`).

### 6b. `b2b/` — the goAML B2B REST client (tested vs WireMock)
- **`GoamlB2bClient`** + **`RestGoamlB2bClient`** (Spring `RestClient`, pinned **HTTP/1.1**).
- **`TokenManager`** (Redis-backed) — resolves per-tenant creds, authenticates, caches the `SqlAuthCookie`
  in **Redis** (`goaml:b2b:token:<tenantId>`, TTL), re-auths on 401; HTTP-Basic mode supported.
- Operations: `postReport(zip) → reportkey`, `getReportStatus`, `deleteReport`, `postMessage`, `getLookups`.
- Typed errors: `B2bAuthException` (401), `B2bValidationException` (400 + body), `B2bTransportException`.
- Tested with **WireMock** + a **JaCoCo ≥90% gate** on the new code (achieved ~98.7% instr).

See [10 — B2B Submission Protocol](10-b2b-submission-protocol.md) for the wire protocol this client speaks.

---

## 4. Open items (inputs needed for full correctness, not for the scaffold)

These are external inputs that gate *correctness*, tracked from the plan. None block building the
plumbing; they block going live.

1. **UAE goAML XSD — ✅ OBTAINED & INTEGRATED.** The authoritative goAML **5.0.2** XSD + 2 real DPMSR
   samples are vendored (`src/main/resources/xsd/goaml/5.0.2/`, `src/test/resources/samples/`). The
   **XSD-first migration is complete** ([`.planning/plans/xsd-first-foundation.md`](../.planning/plans/xsd-first-foundation.md)):
   the domain is now xjc-generated from the schema, the **XSD validation gate** (`XsdSchemaValidator`,
   standard JDK JAXP — the schema has no 1.1 asserts) is built and runs in the golden test, lookups are
   reconciled (lookup⊆XSD), and a DPMSR convenience builder exists. *(Still confirm the UAE production
   schema version, 4.x vs 5.x, before go-live.)*
2. **Per-tenant B2B base URLs (test + prod) + each RE's goAML credentials** — for live submission.
3. **UAE Business Rejection Rules (BRRs)** doc — to complete validation beyond `*`-mandatory fields
   (this is where Emirates-ID/passport rules and many others come from — see the
   [validation gap](05-engine.md#3-validation-engine-validation--the-core-of-correctness)).
4. **Initial UAE lookup exports** (or confirmed `OdataLookups` access) — to seed/refresh `lookups/ae/*`
   (current files are placeholders).
5. **CSV import template** — confirm the column layout per report type (Phase 11).
6. **AWS account specifics** — ECR registry, EKS cluster/region, RDS endpoint, IRSA role ARNs, SES
   verified sender, KMS key — for Helm values + CD (Phases 6/14).

---

## 5. Known gaps in the current build (recap)

A consolidated list of "the plan mentions it but the code doesn't do it yet," so you're not surprised:

- **No Emirates-ID/passport validation** (awaits BRRs — open item #3).
- **`ANALYST`/`MLRO` authorization not enforced** on any endpoint yet (the submit endpoint doesn't exist
  until Phase 7).
- **Platform/SUPER_ADMIN actions aren't audited** (no shared audit table yet).
- **`countries` and `funds` lookups are loaded but unused** by validation.
- **No HTTP endpoint exercises the engine or the B2B client yet** — build/validate/marshal/zip and
  submit/auth work in tests only until Phase 7 wires them to `web/` + persistence.

---

**Next:** [10 — B2B Submission Protocol](10-b2b-submission-protocol.md).
