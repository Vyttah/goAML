# Discussion log

A running record of architectural discussions and the decisions they produced. Started in-repo on
2026-06-03; **Phases 1–5 predate this log** and are captured in git history + [`ROADMAP.md`](ROADMAP.md).

---

## 2026-06-03 — Suite positioning, Phase 1.5 integration, unified auth

**Topic 1 — Suite architecture.** How does goAML relate to the two sibling systems (Accounting/ERP on
RabbitMQ, and AML Screening for sanctions + KYC)? **Decision:** goAML is its own dedicated microservice,
sellable standalone and runnable inside the suite. Clear bounded contexts; **reportability detection lives
in goAML**. Transports: RabbitMQ for accounting transaction events, REST for screening party/KYC.

**Topic 2 — Standalone vs. Phase 1.5 features.** The standalone core (Phases 6–14) is built first under
native auth to make goAML sellable on its own. The suite **integration** (ingestion + auto-reporting) is
**Phase 1.5**, scheduled *after* the standalone core despite the label, because it depends on the engine,
the B2B client, and submission existing. Auto-reporting default = accounting event → validated draft →
**MLRO one-click**; full auto-submit is a per-tenant opt-in.

**Topic 3 — Unified authentication.** Three apps have separate logins today; we want one experience without
a heavyweight IdP, and without disturbing goAML's working JWT/RBAC/schema-per-tenant. **Decision:** goAML
stays its **own identity authority** — siblings authenticate their own user, then call a goAML
**token-exchange** endpoint (server-to-server trust) to mint a standard goAML JWT. Three sub-answers:
(a) goAML owns its JWT, no external IdP; (b) `goaml.auth.mode = native | federated | both` per deployment;
(c) FIU B2B submission credentials are a **separate** per-tenant machine credential (AWS Secrets Manager),
never user login. Three on-ramps converge on the existing JWT so all downstream RBAC/tenant routing is
unchanged.

Full design: [`plans/integration-and-auth-architecture.md`](plans/integration-and-auth-architecture.md).
