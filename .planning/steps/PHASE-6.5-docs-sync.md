# Phase 6.5 — Docs & planning sync (close out Phase 6)

> **Status: ✅ DONE (2026-06-05).**
> Part of [../plans/phase-6-aws-and-b2b.md](../plans/phase-6-aws-and-b2b.md). Docs-only — no code changes.

---

## 1. Goal & why

6.1–6.3 built the AWS Secrets Manager seam, the Redis token cache, and the goAML B2B client. The `docs/`
and `.planning/` files still describe these as *future* (Phase 6 "next", b2b/integration "⚠️ planned").
6.5 brings them in line with the code, and reconciles two intentional deviations from the original plan so
the docs don't mislead a reviewer:

1. our wrapper is **`GoamlSecretsClient`**, not `SecretsManagerClient` (clash with the SDK type);
2. Phase 6 built **Secrets Manager only** (S3 → Phase 8, SES → Phase 10), not all three AWS clients.

## 2. Doc-by-doc

| Doc | Change |
|---|---|
| `docs/10-b2b-submission-protocol.md` | "not implemented yet" → built; class table uses `GoamlSecretsClient`, `TokenManager` (Redis cache), `RestGoamlB2bClient`; add the HTTP/1.1 note + wire-detail caveat. |
| `docs/09-build-order-and-roadmap.md` | Phase 6 row → ✅ (Secrets-only; S3/SES deferred); drop "no XSD gate" leftover if any; Phase 7 = next. |
| `docs/02-system-architecture.md` | `integration/aws` + `b2b` packages now ✅ (was ⚠️ Phase 6). |
| `docs/03-tech-stack-and-local-dev.md` | Stack/deps: AWS SDK v2, Redis, WireMock, JaCoCo; build recipe `docker compose up -d postgres localstack redis`; coverage gate note. |
| `docs/11-glossary.md` | Add/confirm: `GoamlSecretsClient`, `TokenManager`, `SqlAuthCookie` (built), Redis, JaCoCo. |
| `CLAUDE.md` | Build/test recipe → add `localstack redis`. |
| `.planning/ROADMAP.md` | Phase 6 ✅; Phase 7 next. |
| `.planning/STATE.md` | Current position → Phase 6 done; next = Phase 7; note the conditional-IT + JaCoCo test setup. |
| `.planning/plans/phase-6-aws-and-b2b.md` | Fill the Outcome section. |

## 3. Guardrails
Code is the source of truth; no over-claiming (Phase 6 built the client as a tested library — it is **not**
wired to an HTTP endpoint; that's Phase 7). Verify with a stale-grep over `docs/` for "Phase 6 … next" and
the old `SecretsManagerClient` wrapper name.

## 4. Verification
Stale-grep clean; `git status` shows only docs/planning; no `src/`/`build.gradle` change. (No build needed —
docs only — but the suite remains green from 6.3.)

---

## Outcome

Updated `docs/{02,03,09,10,11}.md`, `CLAUDE.md`, `.planning/{ROADMAP,STATE}.md`, and the phase-6 plan
outcome. Phase 6 now reads as **done** everywhere; `b2b`/`integration.aws` marked ✅; the
`GoamlSecretsClient` rename + Secrets-only scope + Redis cache + HTTP/1.1 + JaCoCo gate are reflected; the
build recipe is `docker compose up -d postgres localstack redis`. STATE's next action = **Phase 7**.

**Verified:** stale-grep over `docs/` for `Phase 6 … next` / the old `SecretsManagerClient` wrapper name /
"⚠️ Phase 6" returns clean (only legitimate historical/plan references remain); `git status` shows
docs/planning only — no `src/` or `build.gradle` change. Suite remains green from 6.3.

**Phase 6 is closed.** Next: Phase 7 (persistence + service + web REST).
