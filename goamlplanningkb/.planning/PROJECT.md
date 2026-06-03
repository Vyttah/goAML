# Project — goAML Platform

## What this is

A multi-tenant compliance platform that **builds, validates, submits, and tracks goAML reports** to the
UAE FIU (first target) via the goAML Web **B2B REST** interface. It is being built both as a **standalone,
sellable product** and as a **service inside the Vyttah microservice suite** (alongside an Accounting/ERP
system and an AML Screening system).

## Stack

- **Backend:** Java 21 · Spring Boot 3.3 · Gradle (Groovy DSL)
- **Data:** PostgreSQL, **schema-per-tenant**; Flyway migrations split into `shared/` and `tenant/`
- **Security:** own **JWT** auth + **RBAC** + **audit** (goAML is its own identity authority)
- **Domain:** JAXB POJOs generated from the goAML XSD; builders + marshaller + zip packager + validation
- **Frontend (later phase):** React + TypeScript + Vite + Ant Design
- **Cloud:** AWS — EKS · RDS · S3 · Secrets Manager + KMS · SES

## Surfaces

REST API · React UI · MCP server (for the Vyttah AML Co-Pilot / agents) · CLI.

## Key decisions (locked)

1. **Product positioning** — goAML is its **own dedicated microservice** ("Regulatory Reporting Service"),
   sold standalone **and** run inside the suite. Not merged into accounting or screening.
2. **Bounded contexts** — Accounting owns transactions/financials; Screening owns KYC + sanctions; **goAML
   owns reports, reportability detection, validation, FIU submission, and filing audit**.
3. **Integration transport** — RabbitMQ (already in accounting) for transaction events; REST for screening
   party/KYC data. Built in **Phase 1.5**.
4. **Unified auth** — goAML **keeps its own JWT and remains the identity authority**. Siblings authenticate
   their own user, then call a goAML **token-exchange** endpoint (server-to-server trust) to obtain a goAML
   JWT. **No external IdP** (Keycloak/Cognito rejected). Three on-ramps (native login, federated exchange,
   service principal) all converge on the existing standard JWT, so downstream RBAC/tenant routing is
   unchanged.
5. **Per-deployment auth mode** — `goaml.auth.mode = native | federated | both`, so one binary serves the
   standalone product and the integrated service.
6. **Auto-submit gating** — accounting events auto-create a **validated draft → MLRO one-click approve**;
   fully-automatic submission is a **per-tenant opt-in** (`tenant_goaml_config.auto_submit`, default false)
   with guardrails.
7. **FIU B2B credentials ≠ user login** — per-tenant FIU machine credentials live in AWS Secrets Manager
   (`tenant_goaml_config.secrets_path`); a tenant's users all submit under that tenant's single FIU
   identity. Never mixed with app-user auth or federated exchange.

See [`plans/integration-and-auth-architecture.md`](plans/integration-and-auth-architecture.md) for the full
integration + auth design.

## Constraints

- Multi-tenant isolation via schema-per-tenant must hold across every surface and every auth on-ramp.
- The existing JWT + RBAC + audit foundation must not be disturbed by integration work — additive only.
- Regulatory submission must never be silent: a human-in-the-loop path is the default.
