# State — read this first

## Current status

- **Phases 1–5 of 14 complete** (skeleton → multi-tenancy + JWT/RBAC/audit → domain JAXB POJOs → engine
  builders/marshaller/zip/goldens → validation + UAE jurisdiction + lookups).
- **Test suite green.**
- **Next concrete step:** **Phase 6 — AWS integration + goAML Web B2B client.**

## Recent decisions (this session, 2026-06-03)

Recorded the suite positioning, the **Phase 1.5** integration design, and a **unified authentication**
model — see [`PROJECT.md`](PROJECT.md) (Key decisions) and
[`plans/integration-and-auth-architecture.md`](plans/integration-and-auth-architecture.md). Headlines:

- goAML stays its **own microservice** and its **own JWT identity authority**; siblings get a goAML token
  via a **federated token-exchange** endpoint. No external IdP.
- Three auth on-ramps converge on the existing standard JWT → downstream RBAC/tenant routing unchanged.
- `goaml.auth.mode = native | federated | both` makes one binary serve standalone + suite.
- Accounting events → validated draft → **MLRO one-click**; full auto-submit is per-tenant opt-in.
- FIU B2B creds are per-tenant Secrets Manager machine credentials, **separate from user login**.

## Pending / open

- **XSD-first foundation** — the immediate engineering priority, tracked as a **separate plan that has not
  yet been authored/committed in this repo**. Reconcile with the developer's local canonical copy before
  starting Phase 6.
- **Phase 1.5 build** — designed (see plan doc) but not started; scheduled after the standalone core.
- **`docs/` is not committed in this clone.** The README historically links to `docs/README.md` and
  `docs/00-implementation-plan.md`; those live only on the developer's machine for now. The in-repo
  knowledge base is this `.planning/` directory plus root `CLAUDE.md`.

## Map of the knowledge base

- [`STATE.md`](STATE.md) — you are here: status + next step + recent decisions.
- [`ROADMAP.md`](ROADMAP.md) — phase build order and status.
- [`PROJECT.md`](PROJECT.md) — what this is, stack, locked decisions, constraints.
- [`plans/integration-and-auth-architecture.md`](plans/integration-and-auth-architecture.md) — Phase 1.5
  integration + unified auth design.
- [`discussion-log.md`](discussion-log.md) — running log of architectural discussions.
