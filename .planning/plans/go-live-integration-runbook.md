# Plan — Go-live integration runbook (accounting + AML screening)

> **Purpose:** the concrete, ordered steps to take the *already-built* Phase 1.5 suite integration from
> "code-complete + tested" to "live, wired to your accounting & AML-screening software."
> Code is done and gated (see [ROADMAP](../ROADMAP.md) Phase 1.5); everything below is **operational** —
> deploy, register trust, implement the sibling-side calls, then submit-config. Contract reference:
> [docs/14-suite-integration.md](../../docs/14-suite-integration.md).

**Legend:** `[goAML]` = do on the goAML side · `[ACCT]` / `[AML]` = do in your accounting / screening app.

---

## Phase A — Deploy goAML so both apps can reach it

1. `[goAML]` Stand up a reachable environment (the Docker image + Helm chart + CD exist; needs an AWS
   account/EKS/ECR or any container host + a Postgres). Provide env: `SPRING_DATASOURCE_*`,
   `GOAML_JWT_SECRET`, and **`GOAML_AUTH_MODE=both`** (native login *and* federated exchange).
2. `[goAML]` Confirm health: `/actuator/health` UP, the SPA loads, `POST /api/v1/auth/login` works for a
   seeded admin.
3. `[goAML]` Create a **tenant** per client Reporting Entity (admin API / provisioning), and at least one
   **MLRO** user per tenant (reporting person + submit authority).

> Standalone-safe: with no `trusted_service` rows and `auth.mode=native`, the integration endpoints are inert.

---

## Phase B — Register trust + tenant mapping (per source system)

Each sibling service signs a short-lived **RS256** JWT ("service assertion") with its **private** key; goAML
verifies it against the registered **public** key. One row per source system.

1. `[ACCT]` / `[AML]` Generate an RSA keypair; keep the private key secret, hand goAML the **public** PEM.
2. `[goAML]` Register the trusted service (repeat with `SCREENING`):

```sql
-- public schema. jit_provisioning=true → unknown federated users are auto-created as ANALYST.
INSERT INTO public.trusted_service (id, source_system, description, public_key_pem, jit_provisioning, status)
VALUES (gen_random_uuid(), 'ACCOUNTING', 'Vyttah accounting-service',
        '-----BEGIN PUBLIC KEY-----
...your accounting service public key...
-----END PUBLIC KEY-----', true, 'ACTIVE');
```

3. `[goAML]` Map each source **org id** → a goAML **tenant** (accounting `companyId`, screening `companyId`):

```sql
INSERT INTO public.tenant_external_ref (id, tenant_id, source_system, external_org_ref)
VALUES (gen_random_uuid(), '<goaml-tenant-uuid>', 'ACCOUNTING', '<accounting-companyId>');
-- and the screening one:
INSERT INTO public.tenant_external_ref (id, tenant_id, source_system, external_org_ref)
VALUES (gen_random_uuid(), '<goaml-tenant-uuid>', 'SCREENING', '<screening-companyId>');
```

4. `[goAML]` (optional) Pre-map known users with `external_identity` (else JIT creates them as ANALYST on
   first exchange; promote to MLRO/TENANT_ADMIN as needed via the admin API).

### The service assertion (what each sibling app mints)
RS256 JWT signed with the registered private key:
- `iss` = `ACCOUNTING` / `SCREENING` · `aud` = **`goaml`** · `exp` ≤ **5 min** after `iat`
- for the token exchange (Model 1): `sub` = external user id, `email`, `org` = the org id
- for the integration push (Model 2 / screening): the same assertion in the **`X-Service-Assertion`** header

---

## Phase C — Wire ACCOUNTING

Two models; use either or both (same report is visible to all tenant users).

**Model 1 — embedded (your UI drives goAML):**
1. `[ACCT]` Exchange a user: `POST /api/v1/auth/federated/token` `{ "sourceSystem":"ACCOUNTING", "assertion":"<jwt>" }` → goAML JWT.
2. `[ACCT]` Use it as `Authorization: Bearer …` against `/api/v1/reports` (create+validate), `/{id}/submit`
   (MLRO), `/{id}`, `/{id}/xml`, `/{id}/status`, and `POST /api/v1/reportability/check`.

**Model 2 — raw-invoice push (goAML detects + builds):**
1. `[ACCT]` Resolve your masters → codes (ISO country, the `commodityType` enum) **before** pushing.
2. `[ACCT]` `POST /api/v1/integration/accounting/transactions` with `X-Service-Assertion`, body =
   `AccountingTxnPayload` → **202** `{ goamlRef, reportable, reportId, status, reasons }`.
   - Idempotent on `ACC-<companyId>-<documentNumber>` → your **outbox can retry safely**.
   - Status pull: `GET /api/v1/integration/accounting/transactions/{documentNumber}?companyId=`.
3. `[goAML]` Submit gating: set `tenant_goaml_config.auto_submit = true` to auto-submit VALID drafts (FIU
   failure falls back to the MLRO gate); leave `false` (default) for MLRO 1-click. MLROs are notified either way.

---

## Phase D — Wire AML SCREENING

1. `[AML]` Resolve master FKs → ISO/goAML codes **before** pushing (goAML never calls back).
2. `[AML]` `POST /api/v1/integration/screening/subjects` with `X-Service-Assertion`, body =
   `ScreeningPartyPayload` → **202** with the mapped goAML party set. Idempotent on `SCR-<companyId>-<uid>`.
3. `[goAML/SPA]` A user browses the **Screening** page → seeds a DPMSR draft from a subject
   (`POST /api/v1/screening/subjects/{ref}/seed-report`), supplying goods + reporting MLRO.
   - **Entity** customers seed fully VALID reports; **person** customers seed **drafts** an analyst completes
     (goAML requires a full ID document + `tax_reg_number` a screening profile doesn't carry).

---

## Phase E — Enable real FIU submission (per tenant)

1. `[goAML]` Put the tenant's goAML **B2B credentials** in AWS Secrets Manager; set
   `tenant_goaml_config` (`rentity_id`, `base_url`, `secrets_path`, `auth_mode`).
2. `[goAML]` Create the `goaml-attachments` **S3 bucket** (attachments are pulled into the submission ZIP).
3. `[goAML]` (optional email) Verify an **SES** sender and enable `goaml.notifications.email`.
4. Test: create → validate → **submit** (MLRO) → poll status, against the FIU UAT endpoint first.

---

## Phase F — Before you trust it in production

- [ ] **Contract review** with both teams against the live OpenAPI — the DTOs were built to the
      schemas/docs you provided, but goAML hasn't been run against your *live* services yet (tested with
      MockMvc + Testcontainers).
- [ ] Confirm the **accounting `commodityType` → goAML `item_type`** mapping (esp. `METAL→GOLD` is coarse —
      gold/silver/platinum needs a masters *metal type*; add it as an optional payload field if needed).
- [ ] Confirm the **DPMSR CSV template** (provisional) + the **reportability rule** (cash ≥ AED 55,000 +
      precious) with your compliance/FIU sign-off.
- [ ] Decide **`auto_submit`** per tenant (off = safer, MLRO 1-click).
- [ ] Delete the local **`dev/goaml-prePII-backup.bundle`** (contains the old PII; never push it).
- [ ] Add the **git remote + first push** (history is now PII-clean).

---

## Quick reference — the endpoints

| Purpose | Method & path | Auth |
|---|---|---|
| Federated sign-on | `POST /api/v1/auth/federated/token` | service assertion (body) |
| Reportability check | `POST /api/v1/reportability/check` | user JWT |
| Report lifecycle | `… /api/v1/reports` create/validate/submit/status/xml | user JWT (submit=MLRO) |
| Accounting push | `POST /api/v1/integration/accounting/transactions` (+ GET status) | `X-Service-Assertion` |
| Screening push | `POST /api/v1/integration/screening/subjects` (+ GET) | `X-Service-Assertion` |
| Seed from screened subject | `POST /api/v1/screening/subjects/{ref}/seed-report` | user JWT |
