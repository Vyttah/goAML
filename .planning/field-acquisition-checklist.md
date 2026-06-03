# Field Acquisition Checklist — UAE FIU goAML (UAT first)

> **Purpose:** the exact files + values to collect from the UAE FIU goAML environment once you have
> **UAT (test) access**, and where each one goes in this repo. Hand these back and the blocked work
> (XSD-first foundation, Phase 6 B2B client, UAE jurisdiction config) unblocks.
>
> Plans that consume these: [plans/xsd-first-foundation.md](plans/xsd-first-foundation.md) (XSD + lookups),
> [docs/10 B2B protocol](../docs/10-b2b-submission-protocol.md) (creds + URLs),
> [docs/05 §6 jurisdiction](../docs/05-engine.md) (report codes + threshold).
>
> ⚠️ **Secrets rule:** B2B usernames/passwords/keys are **NEVER committed to this repo.** They go to AWS
> Secrets Manager (prod) or a local `.env`/`*.key` (dev, already git-ignored). Only **non-secret config**
> (base URLs, rentity_id, schema version) gets recorded in-repo. See §5.

---

## 1. Prerequisites

- The reporting entity (Vyttah, or the client RE you're filing for) must be a **registered UAE FIU
  reporting entity**.
- Access to **SACM** (Service Access Control Manager) — the UAE FIU's identity portal.
- A device with **Google Authenticator** (the FIU uses TOTP 2FA).

### ⚠️ Reality check — who can register (learned 2026-06-03)

SACM registration is **not available to a solo developer**. The registration form requires a **real
regulated business**: Entity Name, a **Supervisory Body** (Ministry of Economy for DPMS/DNFBPs, CBUAE
for financial institutions, DFSA/FSRA for free zones), the entity's **registration number with that
supervisory body**, a UAE mobile, and an **authorization PDF** (compliance-officer appointment / trade
licence). goAML access is a **legal/business gate**, not a technical one — do **not** submit fabricated
data to the regulator's portal.

**Implication:** live UAE access (authoritative XSD download, B2B credentials, UAT submission) requires
a real reporting-entity relationship — either an actual **client RE** (gold dealer, exchange house) who
registers and shares access, or Vyttah's regulated client authorizing Vyttah as their reporting agent.

**Dev bootstrap (no portal needed):** build the entire XSD-first foundation against a **generic UNODC
goAML 5.0.x reference XSD** (published openly by some FIU instances), then swap in the authoritative UAE
XSD when a real RE relationship exists. The pipeline is identical; only the file changes. See
[plans/xsd-first-foundation.md §7](plans/xsd-first-foundation.md).

---

## 2. Step-by-step portal procedure (UAT)

> Based on the UAE FIU goAML registration + system guides (links in §7). Menu labels may differ slightly
> by version — record what you actually see.

**Step A — SACM pre-registration.**
Go to the UAE FIU **SACM** site → *Registration* → type *Reporting Entity*. Submit the org +
Compliance-Officer details. You receive a **goAML access User ID** and a **Google Authenticator secret
key**.

**Step B — Select the UAT environment.**
SACM hosts links to both **Production** and **UAT**. Choose **UAT** for everything below — we build and
test against UAT before prod.

**Step C — Complete goAML registration (UAT).**
Log in to the goAML Web portal (`services.uaefiu.gov.ae`, UAT variant) with the User ID + Authenticator
passcode and finish reporting-entity registration.

**Step D — Record your RE identity.**
From your goAML org profile, record the **`rentity_id`** (FIU-assigned RE ID) and any **branch**
identifier(s). → goes into `tenant_goaml_config.rentity_id` and the report header `rentity_branch`.

**Step E — Download the XSD schema(s).**
In goAML Web, find the **schema/XSD download** (usually under a *Help* / *Schema* / *XML* section, or the
"download the XML goAML schema (XSD)" link on the reporting page). Download **all** XSD files —
the report schema **plus any imported/included XSDs** it references. Note the **exact version**
(e.g. 5.0.2 vs 4.0.x). → goes into `src/main/resources/xsd/goaml/<version>/`.

**Step F — Export the lookup tables.**
goAML defines the code lists (countries, currencies, transmodes, funds, item types, etc.). Get them via
the **Lookup Master** screen / schema lookup export (or, for B2B, the **`OdataLookups`** endpoint). We
need the full UAE sets, not the placeholder seeds currently in the repo. → replace
`src/main/resources/lookups/ae/*.json` (and add any missing sets).

**Step G — Obtain B2B web-service credentials + base URLs.**
For programmatic submission (the `b2b/` client), get the **web-service credentials** for the RE and the
**B2B base URL** for **UAT** (and later **prod**). The exact mechanism (web-service user vs cookie auth,
`auth_mode` TOKEN/BASIC) is in the **goAML B2B Developers Guide** — capture which one UAT uses.
→ base URLs go in `tenant_goaml_config.base_url`; **credentials go to Secrets Manager (§5), not git.**

**Step H — Download the rule + report guides.**
Grab the **UAE Business Rejection Rules (BRRs)** doc (linked on the goAML homepage) and the per-report
submission guides you'll support (**DPMSR**, **REAR**, **PNMR**, **FFR**, STR, …). → BRRs drive
`ReportValidator` rules (incl. Emirates-ID/passport); the guides drive report-type coverage.

---

## 3. Artifact table — what / where to get it / where it lands

| # | Artifact | Where in the portal | Lands in repo / system | Needed by |
|---|----------|---------------------|------------------------|-----------|
| 1 | **goAML XSD file(s)** (+ imported XSDs), with exact version | goAML Web → schema/XSD download | `src/main/resources/xsd/goaml/<version>/` | XSD-first foundation (X.1) |
| 2 | **Lookup tables** (countries, currencies, transmode, funds, item types, …) | Lookup Master / `OdataLookups` | `src/main/resources/lookups/ae/*.json` (replace seeds) | engine/lookup; validation |
| 3 | **B2B base URL — UAT** | B2B Developers Guide / portal | `tenant_goaml_config.base_url` (UAT tenant) | Phase 6 `b2b/` |
| 4 | **B2B base URL — Prod** (later) | same | `tenant_goaml_config.base_url` (prod tenant) | Phase 6 `b2b/` |
| 5 | **B2B web-service credentials** (user/pass or cookie) + `auth_mode` | SACM / goAML web-services | **AWS Secrets Manager** (path → `tenant_goaml_config.secrets_path`) — **NOT git** | Phase 6 `TokenManager` |
| 6 | **`rentity_id`** + **branch** | goAML org profile | `tenant_goaml_config.rentity_id`; report `rentity_branch` | header builder |
| 7 | **Allowed report codes for UAE** + the **DPMS threshold** confirmation | report guides / schema | `src/main/resources/jurisdictions/ae.yml` | jurisdiction config |
| 8 | **UAE Business Rejection Rules (BRRs)** doc | goAML homepage link | reference → new `ReportValidator` rules | validation (open item) |
| 9 | **Report submission guides** (DPMSR/REAR/PNMR/FFR/STR) | UAE FIU system guides | reference → report-type coverage | domain/engine |
| 10 | **goAML version UAT accepts** (4.x vs 5.x) | schema header / portal | record in §4 + PROJECT.md | confirms "latest" target |

---

## 4. Values to record (fill in — NON-SECRET only)

```
goAML schema version (UAT):        __________   (e.g. 5.0.2)
goAML schema version (Prod):       __________
B2B base URL (UAT):                __________
B2B base URL (Prod):               __________
auth_mode (TOKEN | BASIC):         __________
rentity_id:                        __________
rentity_branch(es):                __________
allowed report codes (UAE):        __________   (e.g. STR,SAR,AIF,AIFT,ECDD,ECDDT,DPMSR,REAR,PNMR,FFR)
DPMS cash threshold (confirm AED): __________   (currently 55000 in ae.yml)
```

> Once filled, these update `src/main/resources/jurisdictions/ae.yml` and seed the UAT `tenant` +
> `tenant_goaml_config` rows. Do **not** put credentials here.

---

## 5. Secrets handling (do NOT commit)

- **B2B web-service credentials** → **AWS Secrets Manager** (prod/EKS) or a local **`.env`** for dev
  (already git-ignored: `.env`, `*.pem`, `*.key`). The repo stores only the **`secrets_path`** reference
  in `tenant_goaml_config`, never the secret value.
- The **JWT signing key** likewise lives in Secrets Manager (prod) / `GOAML_JWT_SECRET` env (dev).
- If a credential is ever pasted into a file, **do not `git add` it** — `.gitignore` covers the common
  cases but double-check `git status` before committing.

---

## 6. Hand-off → what I do once you provide these

1. **XSD (#1) + lookups (#2)** → execute the XSD-first migration: wire xjc codegen, build the XSD-1.1
   validation gate, re-point the engine, regenerate goldens, expand report types
   ([plans/xsd-first-foundation.md](plans/xsd-first-foundation.md) steps X.2–X.7).
2. **Base URL (#3) + creds (#5) + rentity_id (#6)** → seed a UAT tenant and build/finish the Phase 6
   `b2b/` client + `TokenManager`; do a live dry-run against UAT.
3. **Report codes + threshold (#7)** → update `ae.yml`.
4. **BRRs (#8) + guides (#9)** → extend `ReportValidator` with the real UAE business rules.

**Minimum to unblock the most work:** items **#1 (XSD)** and **#2 (lookups)** — those start the
foundation migration. Items #3/#5/#6 unblock live B2B testing.

---

## 7. Sources

- [UAE FIU — goAML Pre-Registration Guide (RE) v1.0](https://uaefiu.gov.ae/media/kqbizjda/goaml-pre-registration-guide-for-reporting-entities-v1-0-18-03-2022.pdf)
- [UAE FIU — goAML Registration Guide v3.4](https://www.uaefiu.gov.ae/media/ji1o3ko2/goaml-registration-guide-v3-4-18-03-2022.pdf)
- [UAE FIU — System Guides (Report Submission, DPMSR, PNMR & FFR, REAR)](https://www.uaefiu.gov.ae/en/more/knowledge-centre/system-guides/)
- [UAE FIU — goAML FAQs v2.1](https://uaefiu.gov.ae/media/egsdhn0n/goaml-faqs-v2-1-18-apr-2024-002.pdf)
- [UAE FIU eServices portal](https://services.uaefiu.gov.ae/)
- [goAML Schema v5.0.2 description (reference)](https://aml.iq/wp-content/uploads/2023/03/goAMLSchema-v5.0.2.pdf)
