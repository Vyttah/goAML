# goAML Platform — Developer Documentation

> **Audience:** a developer joining this project who knows nothing about AML, goAML, the
> business, or this codebase. These docs are written to take you from zero to productive.
>
> **Status (as of this writing):** Phases 1–11 **+ 13** of 14 complete, **plus** the XSD-first foundation
> (domain xjc-generated from the real goAML **5.0.2** XSD + an XSD validation gate + a DPMSR builder) and the
> Vyttah layer-first refactor. Backend suite green (`./gradlew test`); the **`frontend/` React SPA** (Phase
> 13) is built with 58 Vitest specs green. Next up: **Phase 14 — infra (Dockerfile + Helm + CI/CD)**, then
> Phase 12 (plugin/MCP/CLI) last. Live status: [`.planning/STATE.md`](../.planning/STATE.md).

---

## Read these in order

| # | Doc | What you'll learn |
|---|-----|-------------------|
| 0 | [00 — Implementation Plan](00-implementation-plan.md) | The original end-to-end plan: context, locked decisions, architecture, and the 14-phase build order. The "why." |
| 1 | [01 — Business Context](01-business-context.md) | What AML is, who the FIU is, what goAML is, why Vyttah is building this, and the 7 report types. **Start here.** |
| 2 | [02 — System Architecture](02-system-architecture.md) | The big picture: the one Spring Boot deployable, its four surfaces, AWS services, and how a report flows from data → submitted. |
| 3 | [03 — Tech Stack & Local Dev](03-tech-stack-and-local-dev.md) | Exact stack & versions, every dependency, how to build/run/test locally, config, Docker. |
| 4 | [04 — Domain Model](04-domain-model.md) | The **xjc-generated** JAXB model built from the goAML XSD: how codegen + the `.xjb` bindings work, the `reportActivity` choice slot, enums vs Strings. |
| 5 | [05 — The Engine](05-engine.md) | How a report is built, **validated** (full rule table), marshalled to XML, and zipped. UAE jurisdiction config & FIU lookups. |
| 6 | [06 — Multi-Tenancy & Security](06-multitenancy-and-security.md) | Schema-per-tenant isolation, JWT auth, RBAC roles, audit logging, tenant provisioning — with the non-obvious gotchas. |
| 7 | [07 — Persistence & Migrations](07-persistence-and-migrations.md) | Every database table (shared + per-tenant), Flyway setup, JPA entities. |
| 8 | [08 — Testing](08-testing.md) | The test strategy, golden-file XML regression, how to run and regenerate, what each test proves. |
| 9 | [09 — Build Order & Roadmap](09-build-order-and-roadmap.md) | The 14-phase plan, what's done, **what Phase 6 entails in detail**, and what's left. |
| 10 | [10 — B2B Submission Protocol](10-b2b-submission-protocol.md) | How reports are actually submitted to the FIU over goAML's REST API (the target of Phase 6/9). |
| 11 | [11 — Glossary](11-glossary.md) | Every acronym and term in one place. Keep this open in a tab. |
| 12 | [12 — Frontend (SPA)](12-frontend.md) | The Phase 13 React + Ant Design SPA: stack, layout, how it talks to the API (JWT-claims identity, 401→login), local run, and testing. |
| 15 | [15 — DPMSR field requirements](15-dpmsr-field-requirements.md) | **Mandatory vs optional** for every DPMSR field (from the goAML XSD), for the frontend "required" markers — incl. the context-dependent person rules + a regeneration recipe. |
| — | [CONVENTIONS.md](CONVENTIONS.md) | The repo's **layer-first folder structure & coding conventions** (Vyttah standard, adapted). The authoritative structure reference. |

---

## The one-paragraph summary

Vyttah is building a **multi-tenant RegTech platform** that files **Anti-Money-Laundering (AML)
reports** to the **UAE Financial Intelligence Unit (FIU)** on behalf of many client businesses
("Reporting Entities" — e.g. gold dealers, banks). The platform takes structured data, builds a
**goAML-schema-compliant XML report** (generated from the authoritative **5.0.2** XSD), **validates** it
against the XSD + business rules,
**submits** it over the goAML **B2B REST interface** using each client's own credentials, and
**tracks** the outcome. It's one Java/Spring Boot application exposing four surfaces (REST API,
React UI, MCP server, CLI), backed by PostgreSQL (one schema per client), and deployed on AWS.
UAE is the first target, behind a "jurisdiction" abstraction so other countries slot in by config.

---

## A note on accuracy

These docs are written against the **actual code in this repo**, not just the original plan. Where
the code differs from the plan, or where something the plan mentions does **not yet exist**, the docs
say so explicitly (look for **⚠️ Gap / Not-yet-built** callouts). The source of intent is the
implementation plan, now kept **in-repo** at [00-implementation-plan.md](00-implementation-plan.md); the
source of *truth* is the code. **Live status & the next step** live in
[`.planning/STATE.md`](../.planning/STATE.md).

Everything required to understand and resume this project is in the repo — no machine-local state — so
anyone on any machine can clone, read [`.planning/STATE.md`](../.planning/STATE.md), and continue.
