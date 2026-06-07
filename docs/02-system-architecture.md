# 02 — System Architecture

> The big picture. How the pieces fit together, before we zoom into any one of them.

---

## 1. One deployable, four surfaces

The whole backend is **a single Spring Boot application** (`com.vyttah.goaml`), built as **one
`bootJar`**. It is a *modular monolith*: internal Java packages keep concerns separated
(`domain`, `engine`, `security`, `config/tenant`, …) without the overhead of multiple build modules. The
package layout follows the **layer-first Vyttah conventions** — see [`CONVENTIONS.md`](CONVENTIONS.md).

That one application is designed to expose **four surfaces** over the same core logic:

| Surface | What it is | Status |
|---------|-----------|--------|
| **REST API** (`/api/v1/...`) | The primary HTTP interface, JWT-secured. The React UI and external systems call it. | Partially built (auth + me + admin ping). |
| **React UI** (`frontend/`) | Single-page app (React + TypeScript + Vite + Ant Design) served as static assets. | ⚠️ Not yet built (Phase 13). |
| **MCP server** (`mcp/`) | Model-Context-Protocol tools so AI agents (the Vyttah "AML Co-Pilot") can build/validate/submit reports. | ⚠️ Not yet built (Phase 12). |
| **CLI** (`cli/`) | The same jar run with `--cli` (picocli) for scripted/offline use. | ⚠️ Not yet built (Phase 12). |

> Today only the REST API surface exists, and only its auth/identity slice. The engine
> (build/validate/marshal/zip) exists and is fully tested but is **not yet wired to any HTTP
> endpoint** — that wiring is Phase 7.

---

## 2. The high-level diagram

```
            ┌──────────── React SPA (Ant Design) ───────────┐   ← Phase 13
            │  login · dashboard · report builder · import   │
            │  detail+track · lookups · admin · notifications │
            └───────────────┬────────────────────────────────┘
                            │ HTTPS /api/v1 (JWT)
   ┌────────────────────────▼─────────────────────────────────────┐
   │  Spring Boot monolith (com.vyttah.goaml)                       │
   │                                                                │
   │ controller/(REST) · mcp/(Tools) · cli/(picocli) · security/(JWT)│
   │  service/(orchestration) · engine/(build·validate·marshal·zip) │
   │  b2b/(goAML REST client) · repository+model/(JPA) · domain/(JAXB)│
   │  scheduler/(async poller) · config/tenant/(schema-per-tenant)  │
   └───┬─────────┬──────────┬───────────┬──────────┬───────────────┘
       │ RDS     │ S3        │ Secrets/KMS│ SES      │ goAML B2B (per FIU)
   (schema/tenant)(attach)  (tenant creds)(email)   (UAE test/prod)
```

Legend of the external dependencies (right-hand edge):
- **RDS** — managed PostgreSQL (one schema per tenant).
- **S3** — stores report attachments (per-tenant prefixes).
- **Secrets Manager + KMS** — stores each tenant's goAML credentials and the JWT signing key, encrypted.
- **SES** — sends notification emails.
- **goAML B2B** — the FIU's submission endpoint (UAE test / prod).

---

## 3. The package map (what lives where)

These are the Java packages under `src/main/java/com/vyttah/goaml/`. ✅ = exists today, ⚠️ = planned. The
layout is **layer-first** (controllers / services / repositories / model split by feature) per
[`CONVENTIONS.md`](CONVENTIONS.md), with `domain/` + `engine/` kept outside the CRUD layout as product core.

| Package | Responsibility | Status | Doc |
|---------|----------------|--------|-----|
| `domain/` | The **xjc-generated** JAXB model (`domain.generated.*`, built from the goAML XSD) + the one hand-written `domain/adapter/GoamlDateTimeAdapter`. | ✅ | [04](04-domain-model.md) |
| `engine/` | Build reports, validate (rules **+** XSD), marshal to XML, ZIP-package; jurisdiction + lookup config. | ✅ | [05](05-engine.md) |
| `config/tenant/` | Schema-per-tenant Hibernate plumbing (resolver, connection provider, ThreadLocal context, customizer). | ✅ | [06](06-multitenancy-and-security.md) |
| `security/` | JWT auth filter/service, RBAC, user principal, security config. | ✅ | [06](06-multitenancy-and-security.md) |
| `model/entity/` + `repository/` | JPA entities (no `Entity` suffix) + Spring Data repositories, split per feature (shared `public` schema + per-tenant). | ✅ | [07](07-persistence-and-migrations.md) |
| `model/dto/` + `model/mapper/` | Request/response DTOs + MapStruct mappers, per feature. | ✅ (partial) | [06](06-multitenancy-and-security.md) |
| `service/` | Orchestration as interface + `Default*` impl, per feature. Today: `auth`, `tenant` (provisioning), `audit`, `report` (create/validate/persist), `submission` (package + B2B submit + status), `attachment` (S3 upload/list/remove). | ✅ (partial) | [05](05-engine.md), [06](06-multitenancy-and-security.md), [07](07-persistence-and-migrations.md) |
| `controller/` | Thin REST controllers per feature (today: `auth`, `me`, `admin`, `report` + `report` attachments) — no repos injected directly; delegate to services. | ✅ (partial) | [06](06-multitenancy-and-security.md) |
| `config/` | App config beans (today: `SecurityCryptoConfig` → BCrypt encoder; `config/tenant/*`). | ✅ | [03](03-tech-stack-and-local-dev.md) |
| `exception/` | `GlobalExceptionHandler` (`@RestControllerAdvice`). | ✅ | [06](06-multitenancy-and-security.md) |
| `b2b/` | goAML B2B REST client (`GoamlB2bClient`/`RestGoamlB2bClient` + `TokenManager` Redis token cache): PostReport, OData status, delete, MessageBoard, lookups; typed errors. **Built, tested (not yet wired to an endpoint — Phase 7).** | ✅ | [10](10-b2b-submission-protocol.md) |
| `integration/aws/` | AWS clients. **`GoamlSecretsClient` (Secrets Manager, Phase 6) + `S3StorageClient` (S3 attachments, Phase 8) built**; `SesClient` (Phase 10) planned. | ✅ (partial) | — |
| `ingestion/` | File import as a persisted `import_job` with row-level results: goAML **XML** (`GoamlXmlImporter`, reuses unmarshal+validators) + flat **DPMSR CSV** (`CsvImporter` → `DpmsrCreateRequest` → `ReportService.create`); `POST/GET /api/v1/imports`; sync + per-row isolation. **Built** (Phase 11). | ✅ | — |
| `notification/` | Per-tenant in-app store (`service/notification/` + `model/entity/notification/`) + SES email (`integration/aws/SesClient`), fired off report transitions at the `SubmissionService` seam to author + tenant MLROs; email gated off by default; `GET/POST /api/v1/notifications`. **Built** (Phase 10). | ✅ | — |
| `scheduler/` | `@Scheduled` submission-status poller (`SubmissionStatusPoller`) across tenants + bounded transient `RetryService`. **Built** (Phase 9). | ✅ | — |
| `mcp/`, `cli/` | MCP tools + picocli commands. | ⚠️ Phase 12 | — |

---

## 4. How a report flows end-to-end (target picture)

This is the intended full lifecycle. Today, steps 1–3 (build/validate/package) work in the engine;
steps 4–6 (persist/submit/track) are future phases.

```
   structured input (UI / REST JSON / CSV / imported XML)
        │
        ▼
  ┌─────────────┐   engine/build      Report POJO (JAXB tree)
  │  Builder    │ ───────────────────►  [transaction shape OR activity shape]
  └─────────────┘
        │
        ▼
  ┌─────────────┐   engine/validation   ValidationResult
  │  Validator  │ ───────────────────►  (ERROR blocks; WARNING surfaces)
  └─────────────┘   schema + business rules, keyed by report_code + jurisdiction
        │  (no ERRORs)
        ▼
  ┌─────────────┐   engine/marshal      UTF-8 XML bytes
  │ Marshaller  │ ───────────────────►  (fixed element order from JAXB annotations)
  └─────────────┘
        │
        ▼
  ┌─────────────┐   engine/packaging    ZIP bytes (report.xml + attachments)
  │  Packager   │ ───────────────────►  (5 MB/file, 20 MB/report limits)
  └─────────────┘
        │
        ▼
  ┌─────────────┐   b2b/ (Phase 6)      reportkey
  │  B2B Client │ ───────────────────►  POST multipart ZIP to goAML, using THIS tenant's creds
  └─────────────┘
        │
        ▼
  ┌─────────────┐   scheduler/ (Phase 9)  poll OData for status → Accepted/Rejected/Errors
  │   Poller    │ ───────────────────►    → update DB → fire notification (Phase 10)
  └─────────────┘
```

---

## 5. Two databases-worth of schema in one database

PostgreSQL holds **two kinds of schema**:

1. **The shared/admin schema** (`public`): platform-wide tables — tenants, users, roles, per-tenant
   goAML config, jurisdictions, refresh tokens. There is exactly one of these.
2. **A per-tenant schema** (`tenant_<uuid>`): each client RE gets its own Postgres schema holding
   *their* data (today: `audit_log`; later: reports, submissions, attachments, etc.). There are N of
   these, one per tenant.

Hibernate is configured for **`SCHEMA` multi-tenancy**: on every database connection, the app issues
`SET search_path TO "<tenant_schema>"` so a tenant only ever sees its own schema. The tenant identity
travels in the JWT and is pushed into a `ThreadLocal` per request. Full detail in
[06 — Multi-Tenancy & Security](06-multitenancy-and-security.md) and the table-by-table breakdown in
[07 — Persistence & Migrations](07-persistence-and-migrations.md).

---

## 6. Deployment (target)

- **Container:** multi-stage `Dockerfile` (build the SPA → build the jar → slim JRE 21 runtime).
- **Orchestration:** AWS **EKS** (Kubernetes), with a **Helm chart** (Deployment, Service, Ingress+TLS,
  HPA autoscaling, ConfigMap, ServiceAccount with **IRSA** for AWS access without static keys).
- **Data/infra:** RDS Postgres, S3, Secrets Manager + KMS, SES, ECR (image registry).
- **Observability:** Actuator health/liveness/readiness probes, Micrometer → Prometheus metrics
  (`/actuator/prometheus`), structured JSON logging with correlation IDs.
- **CI/CD:** GitHub Actions — `ci.yml` (build + test backend & frontend on PRs), `cd.yml` (build image,
  push to ECR, `helm upgrade` to EKS on main).

✅ **Phase 14 shipped most of the above:** the finalized SPA-bundled non-root `Dockerfile`, the full Helm
chart (`helm/goaml/` — Deployment+probes, Service, Ingress+TLS, HPA, ConfigMap, ServiceAccount+IRSA, secret
wiring), the observability baseline (`micrometer-registry-prometheus` → `/actuator/prometheus`, `prod`-profile
JSON logs, a `CorrelationIdFilter`), and GitHub Actions (`ci.yml` gates + `cd.yml` image build + secret-gated
ECR/EKS deploy). What remains external (not code): a real AWS account/EKS/ECR/RDS + the GitHub remote + CD
secrets. For local development you only need Postgres (see [03](03-tech-stack-and-local-dev.md)); to run the
container locally, `docker build -t goaml:dev .` then run it with `SPRING_DATASOURCE_*` + `GOAML_JWT_SECRET`.

---

**Next:** [03 — Tech Stack & Local Dev](03-tech-stack-and-local-dev.md) — get it running on your machine.
