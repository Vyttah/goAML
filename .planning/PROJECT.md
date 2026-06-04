# PROJECT — goAML Platform

> Stable project facts: what this is, the requirements, the locked decisions, and the hard constraints.
> Changes rarely. For live status see [STATE.md](STATE.md); for depth see [docs/](../docs/README.md).

---

## What This Is

A **multi-tenant, compliance-grade full-stack platform** that files **Anti-Money-Laundering (AML)
reports** to the **UAE Financial Intelligence Unit (FIU)** via UNODC's **goAML Web** B2B REST interface.
Vyttah (a RegTech company) operates it on behalf of **many client Reporting Entities (REs)** — gold
dealers, exchange houses, banks. UAE is the first target, behind a jurisdiction abstraction so other
countries slot in by config later.

Full primer for newcomers: [docs/01-business-context.md](../docs/01-business-context.md).

## Requirements

**In scope (v1):**
- Build goAML schema-v4.0 XML for **all UAE report types**: STR, SAR, AIF, AIFT, ECDD, ECDDT, **DPMSR**
  (transaction-based and activity-based shapes).
- Validate against schema + conditional business rules **before** submission.
- Submit over goAML B2B REST (auth → multipart ZIP → async OData status) using **each tenant's own**
  goAML credentials; track outcomes with retries + audit.
- Ingest three ways: **React UI**, **generic inbound REST**, **file import** (goAML XML + CSV).
- Four surfaces over one backend: **REST API + React UI + MCP server + CLI**.
- **Multi-tenant** with full **auth, RBAC, and audit**.

**Out of scope (for now):** countries other than UAE (config-only later); anything gated on the open
external inputs (see STATE.md → Blockers).

## Key Decisions (locked)

| Area | Decision |
|------|----------|
| Build shape | Single-module Gradle (Groovy DSL) Spring Boot monolith, package-separated |
| Backend | Java 21, Spring Boot 3.3.x, group `com.vyttah`, base package `com.vyttah.goaml` |
| Frontend | React + TypeScript + Vite + Ant Design, under `frontend/` |
| Database | PostgreSQL (RDS), **schema-per-tenant** isolation |
| Tenancy | Multi-tenant — Vyttah files for many client REs |
| goAML creds | Per-tenant own credentials, encrypted in AWS Secrets Manager + KMS |
| App auth | Self-managed JWT (HS256) + RBAC (SUPER_ADMIN / TENANT_ADMIN / MLRO / ANALYST) + full audit. **goAML stays the identity authority** (no external IdP). |
| Product positioning | **(DECIDED 2026-06-04)** goAML is **sold standalone AND** runs inside Vyttah's suite (accounting/ERP + AML screening). Its own dedicated microservice — not merged into either. |
| Unified auth | **(DECIDED 2026-06-04)** Three on-ramps, all ending in the standard goAML JWT: (1) native login; (2) **federated token-exchange** (`POST /api/v1/auth/federated/token`) where accounting/screening authenticate their user then exchange for a goAML token via service-to-service trust; (3) service principal. goAML stores **external-identity links**. `goaml.auth.mode = native\|federated\|both`. Design: [plans/integration-and-auth-architecture.md](plans/integration-and-auth-architecture.md). |
| Suite integration (Phase 1.5) | **(DECIDED 2026-06-04)** Accounting→goAML via **RabbitMQ** (txn event → reportability detection in goAML → auto-create DPMSR draft); Screening→goAML via **REST + UI form** (party/director/KYC). Sequenced **after** the standalone core despite the "1.5" label. |
| Auto-submit safety | **(DECIDED 2026-06-04)** Auto-create a **validated draft → MLRO 1-click approve**; fully-automatic submission is a **per-tenant opt-in** (`tenant_goaml_config.auto_submit`, default false). FIU B2B creds are separate from all user login. |
| Report scope | **Phased (DECIDED 2026-06-03):** **Phase 1 target = precious-metals dealers → `DPMSR`**, plus `STR`/`SAR` (baseline) + `PNMRA`/`CNMRA` (sanctions/TFS) as likely near-term. **Next phase = all 17** schema report types. Engine is XSD-generated so it *can* do all 17; we deliver DPMSR first. (See [discussion-log.md](discussion-log.md) topic 10.) |
| Ingestion | UI + generic inbound REST + file import (goAML XML + CSV) |
| Surfaces | REST API + React UI + MCP server + CLI (all v1) |
| Attachments | Amazon S3 (per-tenant prefixes) |
| Notifications | In-app + email via Amazon SES |
| Deployment | Docker + AWS EKS, RDS Postgres, Secrets Manager + KMS, S3, SES |
| Observability | Actuator probes, Prometheus metrics, structured JSON logs, correlation IDs |
| CI/CD | GitHub Actions — build+test, image push to ECR, deploy to EKS |
| Domain model | **XSD-first (DECIDED 2026-06-03):** build over the **authoritative goAML 5.0.2 XSD** (obtained + vendored) via **xjc-generated JAXB** + an XSD validation gate using **standard JDK JAXP** (the schema has **no** XSD-1.1 asserts → no Saxon/Xerces-EE). Supersedes the interim hand-modeled v4.0 POJOs. Migration plan: [plans/xsd-first-foundation.md](plans/xsd-first-foundation.md) |
| Report types | The authoritative `goAMLSchema.xsd` (5.0.2) defines **17 codes**: AIF, AIFT, CIR, CNMRA, DPMSR, ECDD, ECDDT, HRC, HRCA, IRR, ITR, PNMRA, PSTR, REAR, SAR, SIR, STR. (CIR/IRR/ITR/PSTR/SIR meanings TBC from UAE "Different Types of Reports v1.2".) Coverage is delivered per the phased Report-scope decision above. |

Rationale and the full clarification history are in
[docs/00-implementation-plan.md](../docs/00-implementation-plan.md) ("Locked decisions").

## Constraints

- **goAML XML element ordering is significant** (XSD `<sequence>`) — JAXB must emit a fixed order
  ([docs/04](../docs/04-domain-model.md)).
- **FIU code-lists are runtime lookups**, not enums — validated Strings refreshed from `OdataLookups`.
- **UAE specifics:** currency AED; DPMS cash threshold **AED 55,000**; attachments **5 MB/file,
  20 MB/report**.
- **Per-tenant isolation is non-negotiable** — every DB access routes through the tenant's schema;
  every submission uses that tenant's own FIU credentials.
- **Money = `BigDecimal`, timestamps = `OffsetDateTime` (UTC)** throughout.
- **Flyway owns the schema** (`ddl-auto: none`); never let Hibernate create/validate DDL.
