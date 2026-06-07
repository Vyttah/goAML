---
name: goaml
description: >-
  Build, validate, preview, and track UAE FIU goAML AML reports (starting with DPMSR — precious-metals
  dealer reports) through the goAML platform's MCP tools. Use whenever the user wants to create, check,
  inspect the XML of, or look up a goAML / DPMSR report, or asks about goAML report types, jurisdictions,
  or AML reporting to the UAE FIU. Teaches the report shapes, UAE rules (AED 55,000 cash threshold), the
  required fields, and the safe build→validate→preview→create→submit workflow.
---

# goAML AML reporting

You drive the **goAML platform** — a multi-tenant service that builds, validates, and submits AML reports to
the **UAE FIU** — through its MCP tools (server name `goaml`). You never touch the regulator directly; you
call tools, and the platform enforces tenancy, RBAC, validation, and audit.

## First, always
1. Call `goaml_whoami` to confirm **which tenant** and **what role** you are connected as. Every action runs
   as that tenant; you cannot see or affect another tenant. Roles: `ANALYST` (build/validate/read),
   `MLRO` (also submit — money-laundering reporting officer), `TENANT_ADMIN`, `SUPER_ADMIN`.
2. If unsure what a report type needs, call `goaml_describe_report_type` (and `goaml_list_lookups`) **before**
   building — don't guess at codes or shape.

## The golden rule (this is AML — non-negotiable)
**Never submit a report to the FIU without explicit human confirmation, and never submit one that hasn't
validated clean.** The safe path is: build → `goaml_validate_dpmsr` → fix every ERROR → `goaml_preview_dpmsr_xml`
(show the human the exact XML) → `goaml_create_dpmsr` (saves a draft) → only then, with the user's explicit
go-ahead and the MLRO role, submit. Submission tools are added separately and are MLRO-gated and dry-run-first.

## Report shapes (key concept)
goAML reports are one of two shapes:
- **Activity-shaped** (goods/parties, **no** `<transaction>` block): `DPMSR`, `SAR`, `AIF`, `ECDD`.
- **Transaction-shaped** (one or more `<transaction>`): `STR`, `AIFT`, `ECDDT`.

**DPMSR is activity-shaped.** It describes precious-metals/stones dealing — a set of **parties** (the
customer: a person or an entity) and a set of **goods** (the metals/stones sold) — with **no transactions**.

## DPMSR — what a valid report needs (UAE)
DPMSR = *Dangerous (Designated) Precious Metals & Stones Report*, filed by dealers when a cash dealing is
**≥ AED 55,000**. A DPMSR request (`goaml_validate_dpmsr` / `goaml_create_dpmsr` input) has:

- `entityReference` — **mandatory, unique per tenant**. It is the idempotency key; reusing one is rejected.
- `submissionDate` — mandatory, ISO-8601 (e.g. `2026-06-02T12:00:00Z`).
- `reportingPerson` — mandatory; the dealer's reporting officer (`firstName`, `lastName`, …).
- `parties` — **at least one**. Each party is **either** an `entity` (company: `name`, …) **or** a `person`
  (`firstName`, `lastName`, …) — exactly one of the two, never both.
- `goods` — **at least one**. Each: `itemType` (e.g. `GOLD`), `estimatedValue` (mandatory), `currencyCode`
  (defaults to `AED`), plus optional make/description/size/registrationDate.
- Optional: `reason`, `action`, `indicators`, `location`, `fiuRefNumber` (only for follow-up codes).

**UAE rules the validator enforces:** local currency must be **AED**; the **sum of AED-priced goods**
`estimatedValue` must meet the **AED 55,000** threshold (goods priced in another currency aren't summed and
raise a warning to check manually); at least one report indicator; the parties/goods minimums above.

## Tools
**Reference (any authenticated role):**
- `goaml_whoami` — your tenant + roles. `goaml_ping` — liveness.
- `goaml_list_jurisdictions` — FIUs, their accepted report codes, currency, DPMS threshold.
- `goaml_list_lookup_sets` / `goaml_list_lookups` — valid codes (currencies, countries, …). **Use the exact
  codes these return** so the report passes validation.
- `goaml_describe_report_type` — a code's shape + which fields it conditionally requires.

**DPMSR lifecycle:**
- `goaml_validate_dpmsr` — build + validate **without saving**; returns `VALID`/`INVALID` + messages. (ANALYST/MLRO)
- `goaml_preview_dpmsr_xml` — the exact goAML XML that *would* be submitted, **without saving**. (ANALYST/MLRO)
- `goaml_create_dpmsr` — saves a **draft** (does **not** submit). (ANALYST/MLRO)
- `goaml_list_reports` / `goaml_get_report` — stored reports + status. (ANALYST/MLRO/TENANT_ADMIN)

## Reading validation messages
Each message has `severity` (ERROR or WARNING), `path` (where, e.g. `report.activity.goods_services[0].estimated_value`),
`code` (e.g. `MANDATORY`, `DPMS_THRESHOLD`, `LOOKUP`, `CONSTRAINT`, `CURRENCY_MISMATCH`), and a `message`.
**A report is only valid when there are no ERROR messages.** Translate them into plain fixes for the user
(e.g. "the gold's value is below the AED 55,000 reporting threshold"), apply the fix, and re-validate. WARNINGs
don't block but should be surfaced.

## Typical flow
1. `goaml_whoami` → confirm tenant/role.
2. `goaml_describe_report_type DPMSR` + `goaml_list_lookups` for any codes you'll use.
3. Gather the parties + goods from the user; assemble the DPMSR request.
4. `goaml_validate_dpmsr` → fix every ERROR, re-validate until VALID.
5. `goaml_preview_dpmsr_xml` → show the human the XML.
6. `goaml_create_dpmsr` → save the draft; report back the id + status.
7. Submission is a separate, explicit, MLRO-confirmed step — never automatic.
