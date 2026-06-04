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
| **6** | **`integration/aws/` + Secrets Manager (LocalStack) → `b2b/` client (per-tenant creds) vs WireMock** | **⏭️ NEXT** | — |
| 7 | **`persistence/` + `service/` + `web/`** reports/submissions REST (Testcontainers Postgres) | ⬜ | — |
| 8 | **S3 attachments** (presigned upload, pull into ZIP) — LocalStack | ⬜ | — |
| 9 | **`scheduler/`** async poller + `RetryService` across tenants; status transitions | ⬜ | — |
| 10 | **`notification/`** in-app + SES email (LocalStack SES) | ⬜ | — |
| 11 | **`ingestion/`** generic inbound REST + file import (goAML XML + CSV) | ⬜ | — |
| 12 | **`mcp/` tools + `cli/`** (picocli) | ⬜ | — |
| 13 | **React frontend** — auth → dashboard → report builder → detail/track → import → lookups → admin → notifications | ⬜ | — |
| 14 | **Infra** — Dockerfile finalize, Helm chart, observability baseline, GitHub Actions CI/CD | ⬜ | — |

Progress: **5 / 14 (≈36%).**

---

## 3. Phase 6 in detail (what's next)

Phase 6 connects the (already-built, tested) engine to the outside world: AWS for secrets, and the FIU
for submission. It splits into **two independently-testable pieces**:

### 6a. `integration/aws/` — AWS clients (tested vs LocalStack)
- **`SecretsManagerClient`** (+ KMS) — fetch a tenant's goAML credentials from the `secrets_path` stored
  in `tenant_goaml_config`. Also the home for the JWT signing key in prod.
- **`S3StorageClient`** — attachment storage (fully used in Phase 8, but the client lands here).
- **`SesClient`** — email (fully used in Phase 10).
- Tested against the **LocalStack** service already declared in `docker-compose.yml`
  (`SERVICES=s3,secretsmanager,kms,ses`, region `me-central-1`).
- ⚠️ Requires adding the **AWS SDK v2** dependency to `build.gradle` (not present yet) and, on EKS,
  **IRSA** for credential-free access.

### 6b. `b2b/` — the goAML B2B REST client (tested vs WireMock)
- **`GoamlB2bClient`** interface + **`RestGoamlB2bClient`** (Spring `RestClient`).
- **`TokenManager`** — resolves **per-tenant** goAML creds from Secrets Manager, authenticates, caches
  the token per tenant, attaches `SqlAuthCookie`, re-auths on 401 (or HTTP-Basic mode).
- Operations: `postReport(zip) → reportkey`, `getReportStatus(reportkey)`, `deleteReport`, `postMessage`,
  `getLookups`.
- Typed errors: `B2bAuthException` (401), `B2bValidationException` (400 + body),
  `B2bTransportException`.
- Tested against **WireMock** (200 / 400 / 401, reportkey parse, OData status parse).
- ⚠️ Requires adding a **WireMock** test dependency (not present yet).

See [10 — B2B Submission Protocol](10-b2b-submission-protocol.md) for the actual goAML wire protocol
this client implements.

**Suggested order:** build 6a (Secrets Manager first, since the B2B client depends on it for creds),
then 6b. Keep the per-tenant credential resolution path clean — it's the seam everything downstream
relies on.

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
- **AWS SDK and WireMock dependencies aren't in `build.gradle`** — adding them is part of Phase 6.
- **No HTTP endpoint exercises the engine yet** — build/validate/marshal/zip work in tests only until
  Phase 7 wires them to `web/`.

---

**Next:** [10 — B2B Submission Protocol](10-b2b-submission-protocol.md).
