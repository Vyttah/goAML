# Phase E — Live FIU submission go-live checklist (DPMSR deal flow)

> The one piece of the suite that is **not** code: a real DPMSR, built by the suite, **accepted by the live
> UAE FIU**. Everything up to the submit call is built + tested (Phases C + D); this checklist is the
> operational/external path to a first real acceptance. Companion to
> [`go-live-integration-runbook.md`](go-live-integration-runbook.md) (which covers deploy + trust wiring).
>
> **Order:** Prereqs (external, the real blocker) → per-tenant FIU config → dry-run on UAT → first live
> submission via the deal flow → acceptance → production cutover. Do **UAT before prod**.

---

## 0. What "done" looks like (acceptance criteria)

- [ ] A DPMSR created from an AML deal is **submitted to the FIU UAT endpoint** and comes back **Accepted**
      (a `reportkey` is recorded on the `submission` row; `report.status` polls to `ACCEPTED`).
- [ ] The same XML is **viewable + downloadable in both apps** (goAML SPA + AML filing console).
- [ ] Repeated for the **prod** FIU endpoint with the client's real Reporting Entity.

---

## 1. Prerequisites — register the Reporting Entity (EXTERNAL — this is the actual gate)

These are **not** in our control; they come from the client + the UAE FIU. Nothing below can be tested live
until these exist.

- [ ] **Client is a registered Reporting Entity (RE)** with the UAE FIU on the **goAML web portal**
      (precious-metals dealer / DPMS sector). Obtain the org's **`rentity_id`** (the FIU organisation id).
- [ ] **goAML Web B2B (system-to-system) access enabled** for that RE — request/confirm via the FIU/SACM
      registration. B2B is separate from the interactive web login.
- [ ] **B2B credentials** issued for the RE: depending on the endpoint's auth mode, either a **token** or a
      **username/password** (maps to `B2bAuthMode.TOKEN` / `BASIC`).
- [ ] **FIU endpoint URLs** for **UAT (test)** and **prod** B2B.
- [ ] Confirm the **reportability rule** (cash ≥ **AED 55,000**, precious metals, not exempt) and the **lookup
      code sets** (item types/status/indicators) against the FIU's current reference data — our lookups are
      **provisional** until the real FIU exports land. (➡ tracked in the runbook Phase F.)
- [ ] (If the third-party `USG…` DPMSR sample matters) resolve whether the FIU **accepts** the
      `employer_address_id`-in-director shape our XSD gate rejects — determines if the gate must relax.

> ⚠️ **FIU credentials are per-tenant and live in AWS Secrets Manager — never in code, env, or git.** They are
> distinct from any user login.

---

## 2. Per-tenant FIU config (goAML side — once prereqs are in hand)

- [ ] Put the RE's B2B credentials in **AWS Secrets Manager** at a per-tenant path (e.g.
      `goaml/<tenant>/fiu-creds`); store the token, or `{username,password}` for BASIC.
- [ ] Set the tenant's **`tenant_goaml_config`** row (Admin UI or SQL):
      - `rentity_id` = the FIU org id (must be **positive** — a `0`/missing id → report builds **INVALID**).
      - `base_url` = the FIU **UAT** endpoint first (swap to prod at cutover).
      - `secrets_path` = the Secrets Manager path above.
      - `auth_mode` = `TOKEN` or `BASIC` (match the FIU).
      - `jurisdiction_code` = `AE`.
      - `review_required` = per policy (Phase D.2 — `true` to force the goAML MLRO review gate before submit).
      - `auto_submit` = **`false`** recommended (MLRO 1-click; `true` only once UAT is trusted).
- [ ] Create the **`goaml-attachments` S3 bucket** (attachments are pulled into the submission ZIP at submit).
- [ ] (Optional) Verify an **SES** sender + enable `goaml.notifications.email` for MLRO notifications.
- [ ] Verify the **AWS IAM/IRSA** the pod runs as can read that Secrets Manager path + the S3 bucket.

---

## 3. Dry run on UAT (no real money, test endpoint)

- [ ] Confirm `base_url` points at **UAT**.
- [ ] Build a representative DPMSR (via the AML deal flow, §4) and **submit** — expect the FIU UAT to
      **Accept** it. If **Rejected**, read the FIU error body (surfaced as `422` + `fiuError`) and fix the
      data/lookups; re-submit (idempotent on `entity_reference`).
- [ ] Poll status (`GET /api/v1/reports/{id}/status`) until it settles (`ACCEPTED` / `REJECTED`).

---

## 4. First live submission — the end-to-end deal flow (both planes)

This exercises everything Phases C + D built. Both approval gates are workflow stages (no segregation-of-duties).

**AML cockpit (`Frontend_Customer` → "goAML Filing"):**
1. [ ] Pick the customer, enter the deal (item type / status / value ≥ threshold / ≥1 indicator / reason),
       **Create deal** → `DRAFT`.
2. [ ] In the deals table: **Submit for approval** → `PENDING_APPROVAL` → **Approve** → `APPROVED`.
3. [ ] **File to goAML** (enabled only when `APPROVED`) → the deal is pushed; goAML builds the DPMSR and
       returns a report id + `VALID`/`INVALID`. Status → `FILED` (or `FAILED` if `INVALID` — fix + refile).

**goAML (SPA, the system of record):**
4. [ ] The report appears. If `review_required=true`: **Review queue** → open → **Approve**
       (`VALID → PENDING_REVIEW → APPROVED`).
5. [ ] **MLRO submit** to the FIU → poll status → **Accepted**. (`auto_submit=true` would do steps 4–5
       automatically — keep off until UAT is trusted.)

**Verify (end-state parity):**
6. [ ] Open the report in the goAML SPA: the **Transaction & Report** view shows the full filing + the review
       trail + the XML.
7. [ ] In the AML console: the row shows the FIU/approval status, **Download XML** works, and (if
       `NEXT_PUBLIC_GOAML_WEB_URL` set) **Open in goAML** deep-links the report.

---

## 5. Production cutover

- [ ] Contract review of both apps against the **live** OpenAPI (DTOs were built to docs, not yet run against
      live FIU — only MockMvc + Testcontainers so far).
- [ ] Swap each tenant's `base_url` from UAT → **prod**; confirm prod B2B creds in Secrets Manager.
- [ ] Decide `auto_submit` per tenant (default off).
- [ ] Merge the AML `feature/goaml-integration` branch (Phase C + D.1 + D.4) into the AML mainline; deploy.
- [ ] Monitor the first real filings (`/actuator/prometheus`, submission/poll logs, MLRO notifications).

---

## Rollback / safety

- **Standalone-safe:** with no `trusted_service` registered and `auth.mode=native`, the integration endpoints
  are inert — a goAML deployment behaves exactly as standalone.
- **Idempotent submit:** retries are safe — duplicate protection is on `entity_reference` /
  `FIL-<companyId>-<filingRef>`.
- **Gate off:** set `review_required=false` / `auto_submit=false` to fall back to the simplest path
  (build → MLRO 1-click submit) without redeploying.
- A `FAILED`/`REJECTED` report does not block others; fix the data and refile under a new reference.
