# goAML Platform — orientation for Claude

Multi-tenant compliance platform that builds, validates, submits, and tracks goAML reports to the UAE FIU
via the goAML Web B2B REST interface. Built as a **standalone product** *and* as a service in the Vyttah
microservice suite (Accounting/ERP + AML Screening).

**Start every session by reading the in-repo knowledge base, in this order:**

1. [`.planning/STATE.md`](.planning/STATE.md) — current status + the exact next step. **Read first.**
2. [`.planning/ROADMAP.md`](.planning/ROADMAP.md) — phase build order and status.
3. [`.planning/PROJECT.md`](.planning/PROJECT.md) — what this is, stack, locked decisions, constraints.
4. [`.planning/plans/integration-and-auth-architecture.md`](.planning/plans/integration-and-auth-architecture.md)
   — Phase 1.5 suite integration + unified authentication design.
5. [`.planning/discussion-log.md`](.planning/discussion-log.md) — running log of architectural discussions.

This repo is the single source of truth — the remote container is ephemeral, so anything worth keeping must
be committed. (Note: `docs/` referenced by the README is not yet committed; the `.planning/` directory is
the canonical in-repo knowledge base.)

## Current status

Phases 1–5 of 14 complete. **Next: Phase 6 — AWS integration + goAML Web B2B client.** Test suite green.

## Build / run

```bash
docker compose up -d postgres   # + LocalStack from Phase 6 on
./gradlew test                  # Java 21 via Gradle toolchain
./gradlew bootRun               # http://localhost:8080 ; health at /actuator/health
```

## Architectural guardrails (do not break)

- **Schema-per-tenant** isolation must hold across every surface and every auth path.
- goAML is its **own JWT identity authority** — integration auth is **additive** (federated token-exchange),
  never a replacement, and **no external IdP**.
- Regulatory submission is **never silent** — default path is validated draft → **MLRO one-click**.
- FIU B2B credentials are per-tenant machine credentials (AWS Secrets Manager), **separate from user login**.
