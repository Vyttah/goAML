<!--
  IN-REPO CANONICAL COPY of the implementation plan.
  Originally authored outside the repo (~/.claude/plans/); copied here so the project is
  self-contained and resumable on any machine. This is the source of intent (the "why" and the
  14-phase build order). The source of *truth* is the code + docs/. Current status & next step
  live in .planning/STATE.md. If you revise the plan, edit THIS file (the in-repo copy).
-->

# goAML Multi-Tenant Report Submission Platform — Implementation Plan

> 📜 **Historical — original plan, preserved as intent.** This is the *original* end-to-end plan and is
> kept for the "why" and the 14-phase build order. The codebase has since evolved beyond it — notably the
> **XSD-first foundation** (the domain is now xjc-generated from the real goAML 5.0.2 XSD, with an XSD
> validation gate) and the **Vyttah layer-first refactor** (`controller`/`service`/`repository`/`model`,
> Lombok + MapStruct). For the **current** structure and state, see [`CONVENTIONS.md`](CONVENTIONS.md),
> the rest of `docs/`, and [`.planning/STATE.md`](../.planning/STATE.md) / [`.planning/ROADMAP.md`](../.planning/ROADMAP.md).
> Don't treat package names or "not-yet-built" notes below as current.

## Context

**Why this is being built.** Vyttah is building a **RegTech platform** that files Anti-Money-Laundering
reports to the **UAE FIU** (first target; other countries later) via UNODC's **goAML Web**. Vyttah acts
on behalf of **many client Reporting Entities (REs)** — dealers, banks, etc. Today
`/Users/rajatgajera/Documents/vyttah/dev/goAML` is empty; this is greenfield.

**What we're building.** A **multi-tenant, compliance-grade full-stack application** that:
1. Builds **goAML schema-v4.0-compliant XML** for **all UAE report types** — transaction-based
   (STR/AIFT/ECDDT) and activity-based (SAR/AIF/ECDD + **DPMSR** for precious metals & stones).
2. Validates against schema + conditional business rules **before** submission.
3. **Submits** over the goAML **B2B REST interface** (auth → multipart ZIP → async OData status) using
   **each tenant's own goAML credentials**, and **tracks** outcomes with retries + audit.
4. Ingests data three ways — **React UI**, **generic inbound REST**, and **file import (goAML XML + CSV)**.
5. Exposes four surfaces over one backend: **REST API + React UI + MCP server + CLI**.
6. Is **multi-tenant** (Vyttah filing for many client REs) with **full auth, RBAC, and audit**.
7. Targets **UAE first** behind a jurisdiction abstraction so other FIUs slot in by config later.

**Intended outcome.** A deployable AWS-hosted platform where Vyttah staff and client REs build, validate,
submit, and track goAML reports for the UAE FIU, with per-tenant isolation and a full audit trail.

**Source of truth.** Three vendor PDFs (extracted into this design): goAML *XML Schema Guide v.5*, goAML
*B2B Developers Guide*, UAE *DPMS Report Submission Guide*. The **XSD ships from the FIU, not in the PDFs**
— so we hand-model a representative subset now and swap to XSD-generated JAXB later (Open Items).

---

## Locked decisions (from clarification rounds)

| Area | Decision |
|---|---|
| Build shape | **Single-module Gradle (Groovy DSL)** Spring Boot monolith, package-separated; `service` package distinct |
| Backend | **Java 21 LTS**, **Spring Boot 3.3.x**, group `com.vyttah`, base package `com.vyttah.goaml` |
| Frontend | **React + TypeScript + Vite**, **Ant Design**, under `frontend/` |
| Database | **PostgreSQL (RDS)**, **schema-per-tenant** isolation |
| Tenancy | **Multi-tenant** — Vyttah files for many client REs |
| goAML creds | **Per-tenant own credentials**, encrypted, in **AWS Secrets Manager + KMS** |
| App auth | **Self-managed JWT** + **RBAC** (admin / MLRO / analyst) + **full audit trail** |
| Report scope | **All UAE types in v1**: STR, SAR, AIF, AIFT, ECDD, ECDDT, DPMSR |
| Ingestion | **UI + generic inbound REST + file import (goAML XML + CSV)** |
| Surfaces | **REST API + React UI + MCP server + CLI** (all in v1) |
| Attachments | **Amazon S3** (per-tenant prefixes) |
| Notifications | **In-app + email via Amazon SES** |
| Deployment | **Docker + AWS EKS**, RDS Postgres, Secrets Manager + KMS, S3, SES |
| Infra in-repo | **Dockerfile + Helm chart** |
| Observability | **Full baseline** — Actuator probes, Prometheus metrics, structured JSON logs, correlation IDs |
| CI/CD | **GitHub Actions** — build + test (backend & frontend), image push to ECR, deploy to EKS |

---

## Key spec facts the design relies on

- **Root** `<report>` (schema v4.0). Header: `rentity_id`, `rentity_branch`, `submission_code` (E/M/-),
  `report_code` (STR|SAR|AIF|AIFT|ECDD|ECDDT|**DPMSR**), `entity_reference`, `fiu_ref_number` (cond.),
  `submission_date` (`YYYY-MM-DDTHH:MM:SS`), `currency_code_local`, `reporting_person`, `location`/`reason`/
  `action` (cond.), `transaction[1..N]` **or** `activity`, `report_indicators[1..N]`.
- **Two shapes** — **transaction** (STR/AIFT/ECDDT) vs **activity** (SAR/AIF/ECDD/**DPMSR**: report parties +
  `goods_services`). Transactions are **bi-party** (exactly one `t_from*` + one `t_to*`) or **multi-party**
  (`t_party[]` with roles).
- **Reusable complex types** w/ stricter `*_my_client` variants: `t_person`, `t_account`, `t_entity`,
  `t_address`, `t_phone`, `t_person_identification`, `t_foreign_currency`, `goods_services`.
- **Element ordering is significant** (XSD `sequence`) → JAXB must emit fixed order.
- **Many enums are FIU-defined lookups** (refreshable from B2B `OdataLookups`) → validated strings, not enums.
- **B2B is REST**: `POST /api/Authenticate/GetToken` → `SqlAuthCookie` (or HTTP Basic); `POST /api/Reports/
  PostReport` takes a **multipart ZIP** (one report XML + attachments) → returns `reportkey`; **async** — poll
  `GET /odata/Odata/OdataReports?$filter=ReportKey eq '…'` for `Status`/`Errors`. HTTP 200/400/401.
  `MessageBoard/*` for FIU correspondence.
- **UAE specifics**: report type **DPMSR**, currency **AED**, **Emirates ID** + passport rules, DPMS cash
  threshold **AED 55,000**, attachment cap (guide) 5 MB/file & 20 MB/report.

---

## Architecture overview

One Spring Boot deployable (REST API + MCP server + static React SPA) + a CLI mode of the same jar, on EKS,
backed by RDS Postgres (schema-per-tenant), S3 (attachments), Secrets Manager/KMS (tenant goAML creds + JWT
key), SES (email). React talks to the REST API. The AML Co-Pilot / agents drive the MCP server.

```
            ┌──────────── React SPA (Ant Design) ───────────┐
            │  login · dashboard · report builder · import   │
            │  detail+track · lookups · admin · notifications │
            └───────────────┬────────────────────────────────┘
                            │ HTTPS /api/v1 (JWT)
   ┌────────────────────────▼─────────────────────────────────────┐
   │  Spring Boot monolith (com.vyttah.goaml)                       │
   │  web/(REST) · mcp/(Tools) · cli/(picocli) · security/(JWT,RBAC)│
   │  service/(orchestration) · engine/(build·validate·marshal·zip) │
   │  b2b/(goAML REST client) · persistence/(JPA) · domain/(JAXB)   │
   │  scheduler/(async poller) · tenant/(schema-per-tenant) · config│
   └───┬─────────┬──────────┬───────────┬──────────┬───────────────┘
       │ RDS     │ S3        │ Secrets/KMS│ SES      │ goAML B2B (per FIU)
   (schema/tenant)(attach)  (tenant creds)(email)   (UAE test/prod)
```

---

## Project structure

```
goAML/
├── settings.gradle · build.gradle (Groovy) · gradlew + gradle/wrapper
├── Dockerfile                                   # multi-stage: build SPA → build jar → runtime
├── helm/goaml/                                  # Helm chart (Deployment, Service, Ingress, HPA,
│                                                #   ServiceAccount+IRSA, ConfigMap, ExternalSecret)
├── .github/workflows/ci.yml · cd.yml            # GitHub Actions
├── docker-compose.yml                           # local dev: postgres + localstack (s3/secrets/ses)
├── src/main/java/com/vyttah/goaml/
│   ├── GoamlApplication.java                    # web mode default; --cli → CLI mode
│   ├── domain/                                  # JAXB POJOs + fixed enums for <report> tree
│   ├── engine/                                  # builders · validation · marshal · zip · jurisdiction · lookup
│   ├── b2b/                                     # goAML B2B REST client (auth · PostReport · OData · MessageBoard)
│   ├── service/                                 # orchestration (kept separate, per request)
│   ├── persistence/                            # JPA entities + repositories (shared + per-tenant)
│   ├── tenant/                                  # schema-per-tenant resolver, connection provider, provisioning
│   ├── security/                               # JWT, RBAC, audit interceptor
│   ├── web/                                     # REST controllers + DTOs + error mapping
│   ├── ingestion/                              # generic inbound REST + file import (XML/CSV)
│   ├── notification/                           # in-app + SES email
│   ├── integration/aws/                        # S3, Secrets Manager/KMS, SES clients
│   ├── scheduler/                              # async submission poller + retry
│   ├── mcp/                                     # MCP @Tool components
│   ├── cli/                                     # picocli commands
│   └── config/                                  # app + jurisdiction + AWS config beans
├── src/main/resources/
│   ├── application.yml · application-aws.yml
│   ├── db/migration/shared/V1__shared.sql       # Flyway: shared/admin schema (tenants, users, roles, configs, lookups)
│   ├── db/migration/tenant/V1__tenant.sql       # Flyway: per-tenant schema (reports, submissions, attachments, audit, notifications)
│   ├── jurisdictions/ae.yml                     # UAE config (report codes, AED, base URLs, limits, thresholds)
│   ├── lookups/ae/*.json                        # seed UAE lookup sets
│   └── static/                                  # built React app (Gradle node task populates in prod)
├── src/test/java/com/vyttah/goaml/...           # JUnit5, WireMock, XMLUnit, Testcontainers, LocalStack
└── frontend/                                    # React + TS + Vite + Ant Design
    ├── package.json · vite.config.ts · tsconfig.json
    └── src/{api,pages,components,types,auth}/
```

> One Gradle module, one `bootJar`. Internal package boundaries keep `domain`/`engine`/`b2b`/`service`/
> `tenant`/`security` separated without multi-module overhead. CLI = same jar run with `--cli`.

---

## Multi-tenancy (schema-per-tenant)

- **Shared/admin schema** (`public`): `tenant`, `app_user`, `role`, `user_role`, `tenant_goaml_config`
  (jurisdiction, rentity_id, base URL, **Secrets Manager path** to goAML creds, auth mode), `jurisdiction`,
  `lookup` (jurisdiction-level, shared across that jurisdiction's tenants), `refresh_token`.
- **Per-tenant schema** (`tenant_<id>`): `report`, `submission`, `attachment`, `report_party`, `audit_log`,
  `notification`, `import_job`.
- **Hibernate multi-tenancy = SCHEMA strategy**: `tenant/TenantIdentifierResolver` (reads `tenant_id` from
  the JWT / request context) + `tenant/SchemaMultiTenantConnectionProvider` (sets Postgres `search_path` per
  connection). `tenant/TenantContext` (ThreadLocal) set by a servlet filter post-auth.
- **Flyway**: migrate the shared schema at startup; migrate each tenant schema on **provisioning** and on
  **app upgrade** (iterate all `tenant.*` schemas). `db/migration/shared` vs `db/migration/tenant` locations.
- **Tenant provisioning** (`service/TenantProvisioningService`, super-admin only): create row → `CREATE SCHEMA
  tenant_<id>` → run tenant Flyway → create S3 prefix → store goAML creds in Secrets Manager → seed first
  tenant-admin user.

---

## Security (self-managed JWT + RBAC + audit)

- **Spring Security** with stateless JWT: short-lived access token + refresh token; passwords **BCrypt**;
  **JWT signing key in Secrets Manager** (HS256 secret or RS256 keypair). `security/JwtService`,
  `JwtAuthFilter`, `SecurityConfig`.
- **JWT claims**: `sub` (userId), `tenant_id`, `roles`. Tenant context derived from `tenant_id` claim →
  drives schema routing. Super-admin (Vyttah) can act across tenants via explicit tenant selection.
- **RBAC roles**: `SUPER_ADMIN` (Vyttah platform), `TENANT_ADMIN`, `MLRO`, `ANALYST`. Method-level
  `@PreAuthorize`. Submission/credential ops gated to MLRO/admin.
- **Audit**: `security/AuditInterceptor` + `audit_log` (per tenant) — actor, tenant, action, entity, before/
  after summary, timestamp, correlation ID. Records create/validate/submit/delete/credential-change/login.

---

## Backend packages & representative files

### `domain/` — schema as Java types
Hand-modeled JAXB POJOs for the v4.0 `<report>` tree; **every type sets `@XmlType(propOrder={...})`**;
`BigDecimal` for money/rates; shared `GoamlDateTimeAdapter` (`yyyy-MM-dd'T'HH:mm:ss`).
`Report`, `ReportingPerson`, `ReportIndicator`, `Transaction`, `TFrom`/`TFromMyClient`, `TTo`/`TToMyClient`,
`TParty`, `TForeignCurrency`, `GoodsServices`, `Activity`, `ReportParty`, `TPerson`(+`MyClient`),
`TAccount`(+`MyClient`), `TEntity`(+`MyClient`), `TAddress`, `TPhone`, `TPersonIdentification`;
`enums/ReportCode`, `SubmissionCode` (fixed); FIU lookups = validated strings.

### `engine/` — build, validate, package
- `build/`: `ReportBuilder` + `TransactionReportBuilder`, `ActivityReportBuilder` (neutral `*Input` DTOs → POJOs).
- `validation/ReportValidator` — rule sets keyed by `report_code` + jurisdiction: conditional mandatory,
  bi-party "one from/one to", `t_party` "one subject", max-lengths, enum-in-lookup, UAE thresholds (DPMS AED
  55k), Emirates-ID/passport rules → structured `ValidationResult`. `XsdSchemaValidator` gate when XSD present.
- `marshal/ReportMarshaller` (JAXB → UTF-8, fixed order); `packaging/ReportZipPackager` (in-memory ZIP + limits).
- `jurisdiction/JurisdictionRegistry` + `JurisdictionConfig` (UAE shipped); `lookup/LookupService`
  (seed `lookups/ae/*`, refresh from `OdataLookups`).

### `b2b/` — submission transport (per-tenant creds)
`GoamlB2bClient` + `RestGoamlB2bClient` (Spring `RestClient`); `auth/TokenManager` resolves **per-tenant**
goAML creds from Secrets Manager, caches token per tenant, attaches `SqlAuthCookie`, re-auths on 401 (HTTP-Basic
option). Ops: `postReport(zip)→reportkey`, `getReportStatus(reportkey)`, `deleteReport`, `postMessage`,
`getLookups`. Typed errors: `B2bAuthException`(401), `B2bValidationException`(400+body), `B2bTransportException`.

### `service/` — orchestration (kept separate)
`ReportService`, `SubmissionService`, `StatusTrackingService`, `RetryService`, `LookupSyncService`,
`AttachmentService` (S3), `TenantProvisioningService`, `UserService`, `NotificationService`, `ImportService`.

### `persistence/` — JPA + Flyway (shared + per-tenant)
Entities listed under Multi-tenancy. `report` carries `entity_reference` **unique per tenant** = idempotency;
`submission` tracks reportkey/http_status/b2b_status/errors/attempt_count/last_polled_at; `attachment` holds S3
key + metadata; `audit_log`, `notification`, `import_job`.

### `web/` — REST API (`/api/v1`)
Auth (`/auth/login`,`/refresh`), reports (`POST` build+validate+persist, `POST /{id}/submit`, `GET` list/detail),
attachments (presigned S3 upload + register), imports (`POST /imports` XML/CSV), lookups, jurisdictions,
notifications, admin (tenants, users, goAML credentials). `@ControllerAdvice` error mapping; OpenAPI via springdoc.

### `ingestion/` — inbound REST + file import
Generic inbound: authenticated `POST /api/v1/reports` accepting neutral report-input JSON. File import:
`GoamlXmlImporter` (unmarshal → validate → persist for view/resubmit) and `CsvImporter` (defined per-report-type
CSV template → map → build → validate); async `import_job` with row-level error reporting.

### `notification/` — in-app + SES email
`notification` entity + endpoints + UI center; created on status transitions (poller) and FIU message-board
replies; `SesEmailSender` sends templated emails on accept/reject + replies (per-user preferences).

### `scheduler/` — async tracking
`SubmissionPoller` (`@Scheduled`) enumerates tenants, polls OData per tenant creds for in-flight submissions,
updates status/errors, fires notifications; `RetryService` re-submits transient failures with backoff;
idempotency via `entity_reference`.

### `mcp/` & `cli/`
MCP `GoamlMcpTools` (`build_report`, `validate_report`, `submit_report`, `get_report_status`, `list_lookups`,
`list_jurisdictions`) — tenant/auth context via service token mapped to a tenant. CLI (picocli): `goaml build|
validate` (offline via engine), `goaml submit|status|lookups sync` (via API with JWT), `goaml import`.

### `integration/aws/`
`S3StorageClient`, `SecretsManagerClient` (+ KMS), `SesClient`. On EKS, AWS access via **IRSA** (IAM Roles for
Service Accounts) — no static keys. LocalStack used for local/dev/test.

---

## React frontend (`frontend/`, Ant Design)

- **Auth**: login, token refresh, role-aware routing/guards.
- **Dashboard**: report list (Draft/Validated/Submitted/Processing/Accepted/Rejected), filters, FIU error surfacing.
- **Report Builder**: dynamic forms for **all report types** — transaction view (STR/AIFT/ECDDT: from/to parties,
  amounts) and activity view (SAR/AIF/ECDD/**DPMSR**: report parties + `goods_services` with item type/invoice/
  currency/size-UOM). Person/Entity/Account sub-forms with address/phone/identification. Client-side validation
  (Zod) mirroring backend; server `ValidationResult` inline.
- **Report Detail**: XML preview, validation results, submit, submission timeline (reportkey, poll history, FIU
  errors), S3 attachment upload (presigned).
- **File Import**: upload goAML XML / CSV, preview + per-row validation, commit.
- **Lookups**: browse/refresh UAE lookups.
- **Admin**: tenant management (super-admin), user/role management (tenant-admin), goAML credential config.
- **Notifications center**. `api/` = typed client + TanStack Query; `types/` mirror backend DTOs.

---

## UAE-first jurisdiction model

`JurisdictionConfig` (`ae.yml`): base B2B URLs (test/prod), default `currency_code_local` (AED), allowed report
codes (incl. DPMSR), active lookup set, file limits, threshold rules (DPMS AED 55,000), Emirates-ID/passport
rules. `rentity_id` + creds are **per-tenant** (in `tenant_goaml_config` + Secrets Manager). Only `ae` ships
now; new country later = new config + lookup sync, no code change.

---

## Deployment & ops (Docker + EKS)

- **Dockerfile**: multi-stage — build React (`frontend/dist` → `static/`), build `bootJar`, slim JRE 21 runtime.
- **Helm chart** (`helm/goaml`): Deployment, Service, Ingress (TLS), HPA, ConfigMap, ServiceAccount with **IRSA**
  (S3/Secrets/KMS/SES access), ExternalSecret/secret refs, liveness/readiness probes.
- **AWS**: EKS (app), RDS Postgres (schema-per-tenant), S3 (attachments), Secrets Manager + KMS (tenant goAML
  creds + JWT key), SES (email), ECR (images).
- **Observability**: Actuator health/liveness/readiness; Micrometer → Prometheus `/actuator/prometheus`;
  structured JSON logging (Logback + logstash encoder); correlation IDs in MDC tied to audit + traces.
- **CI/CD (GitHub Actions)**: `ci.yml` — backend `./gradlew test`, frontend `vitest` + build, on PRs.
  `cd.yml` — build image, push to ECR, `helm upgrade` to EKS on main.

---

## Build order (phased; each independently testable)

1. **Skeleton**: Gradle (Groovy), Spring Boot 3.3 / Java 21, package layout, Dockerfile, docker-compose
   (Postgres + LocalStack), Flyway shared baseline, Actuator health.
2. **Multi-tenancy + security foundation**: shared entities (tenant/user/role), Hibernate SCHEMA multi-tenancy
   (resolver + connection provider + filter), tenant provisioning + per-tenant Flyway, JWT auth + RBAC + audit.
3. **`domain/`** POJOs + round-trip/ordering tests (XMLUnit).
4. **`engine/` builders + marshaller** + golden-file XML per report type (STR, SAR, AIFT, ECDD, **DPMSR**).
5. **`engine/` validation + UAE jurisdiction + lookups**.
6. **`integration/aws/` + Secrets Manager** wiring (LocalStack) → **`b2b/` client** (per-tenant creds) vs WireMock.
7. **`persistence/` + `service/` + `web/`** reports/submissions REST (Testcontainers Postgres, schema-per-tenant).
8. **S3 attachments** (presigned upload, pull into ZIP) — LocalStack tests.
9. **`scheduler/` async poller + `RetryService`** across tenants; status transitions.
10. **`notification/`** in-app + SES email (LocalStack SES).
11. **`ingestion/`** generic inbound REST + file import (goAML XML + CSV, with a defined CSV template).
12. **`mcp/` tools** + **`cli/`**.
13. **React frontend**: auth → dashboard → report builder (all types) → detail/track → import → lookups → admin →
    notifications; Gradle node task wires `dist` into `static/`.
14. **Infra**: Dockerfile finalize, Helm chart, observability baseline, GitHub Actions CI/CD.

---

## Verification (end-to-end)

- **Backend unit/contract**: `./gradlew test` — round-trip marshalling, golden-file XML per report type
  (XMLUnit), validation-rule coverage, B2B client vs WireMock (200/400/401, reportkey + OData parse),
  Secrets/S3/SES vs LocalStack.
- **Multi-tenancy/security**: tests proving schema routing isolates tenant data; JWT/RBAC denies cross-tenant +
  unauthorized-role access; audit rows written.
- **Integration**: Testcontainers Postgres — provision tenant → create → validate → submit (WireMock B2B) →
  poller flips status → notification (in-app + SES) fires; idempotent re-submit.
- **XSD conformance**: once UAE XSD loaded, marshal each golden report and validate against it (authoritative gate).
- **Ingestion**: REST JSON build; goAML XML re-import round-trips; CSV import maps + reports row errors.
- **MCP/CLI**: drive `build_report`/`submit_report` via MCP and CLI; assert parity with REST.
- **Frontend**: Vitest component tests; manual run (`npm run dev` proxying to backend) — login, build a DPMSR
  report, validate, submit, watch status + notification.
- **Deploy**: image builds; Helm deploys to a kind/EKS test cluster; probes green; CI/CD pipeline runs green.
- **Manual dry-run**: against the UAE FIU **test** environment once a tenant's credentials + base URL are set.

---

## Open items / inputs to obtain (gate full correctness, not the scaffold)

1. **UAE goAML XSD file(s)** — enables generated JAXB + authoritative XSD validation gate.
2. **Per-tenant B2B base URLs (test + prod) + each client RE's goAML credentials** — for live submission.
3. **UAE Business Rejection Rules (BRRs)** doc (hyperlinked on goAML homepage) — to complete validation beyond
   `*` mandatory fields.
4. **Initial UAE lookup exports** (or confirmed `OdataLookups` access) — to seed `lookups/ae/*`.
5. **CSV import template** — confirm the column layout per report type (we'll propose one for sign-off).
6. **AWS account specifics** — ECR registry, EKS cluster name/region, RDS endpoint, IRSA role ARNs, SES verified
   sender domain, KMS key — needed for Helm values + CD.
