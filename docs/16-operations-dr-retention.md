# 16 — Operations: Backup, DR & Data Retention

> **Status: POLICY DRAFT — needs sign-off.** The RPO/RTO targets and the retention periods below are
> sensible defaults proposed by engineering. The retention schedule in particular **must be confirmed with
> legal/compliance and against the UAE FIU / supervisory body's record-keeping rules** before the first real
> filing. Treat the numbers as "agreed unless overridden", not as legal advice.
>
> **Scope:** the production goAML platform — RDS PostgreSQL (schema-per-tenant), S3 attachments, and the
> filed-report / audit records that make this a regulated **system of record**. Companion to the deploy +
> trust-wiring runbook ([`.planning/plans/go-live-integration-runbook.md`](../.planning/plans/go-live-integration-runbook.md))
> and the Helm chart ([`helm/goaml/`](../helm/goaml/)).

---

## 1. Why this exists

goAML files AML reports to the UAE FIU on behalf of client Reporting Entities. That makes it a **regulated
system of record**: the filed XML, its attachments, the submission outcome, and the audit trail of who did
what are records the business may be legally required to retain and to be able to **produce on demand for
years**. A lost database or a deleted attachment is not just an availability incident — it can be a
**compliance breach**. This doc defines how we protect those records and how long we keep them.

---

## 2. RPO / RTO targets (PROPOSED — needs sign-off)

| Metric | Target | Rationale |
|---|---|---|
| **RPO** (max acceptable data loss) | **≤ 5 minutes** | Achieved by RDS automated backups + transaction-log shipping (PITR). A filing in flight at the moment of failure should not be silently lost. |
| **RTO** (max acceptable downtime) | **≤ 1 hour** | Restore an RDS snapshot / PITR to a new instance, re-point the app via Helm/secret, roll pods. Submission is not real-time-critical (the FIU accepts asynchronously), so an hour is tolerable. |
| **Backup retention (operational)** | **≥ 35 days** of automated backups + PITR | Covers "discovered a corruption N days ago" recovery. Distinct from the **records retention** in §6, which is far longer. |

> These are starting points. A regulator or client SLA may demand tighter RTO; revisit per tenant contract.

---

## 3. PostgreSQL backup & restore (RDS)

The database is **Amazon RDS for PostgreSQL** (one instance, schema-per-tenant). Flyway owns the schema
(`ddl-auto: none`).

**Backups to enable (RDS console / IaC):**
- **Automated backups ON**, retention **≥ 35 days** (RDS max is 35; for longer, see §6 snapshot exports).
- **Point-In-Time Recovery (PITR)** — automatic with automated backups; lets you restore to any second
  within the window. This is what delivers the ≤ 5-min RPO.
- **Storage encryption (KMS)** — on (it cannot be enabled after creation; create encrypted).
- **Multi-AZ** for production — automatic failover to the standby, no restore needed for an AZ outage.
- **Deletion protection** ON; **final snapshot** required on delete.
- Optionally copy snapshots to a **second region** for regional-disaster DR (cross-region snapshot copy).

**Restore procedure (PITR — the common case):**
1. RDS → the instance → **Actions → Restore to point in time** → pick the timestamp just before the
   incident → restore to a **new** instance (RDS never restores in place).
2. When the new instance is available, update the app's DB connection:
   - `SPRING_DATASOURCE_URL` (Helm `config.SPRING_DATASOURCE_URL`) → new endpoint, and/or
   - the `SPRING_DATASOURCE_PASSWORD` secret if the restored instance uses different creds.
3. `helm upgrade` (or just edit the ConfigMap + roll the Deployment). Readiness is DB-aware, so pods only go
   Ready once they can reach the restored DB.
4. **Do not run Flyway against the restored DB expecting a downgrade** — Flyway is forward-only (see §7).
   A PITR restore already contains the schema as of that timestamp; the running app's migrations must be
   **≥** that schema version (deploy the matching app image, see §7).

**Snapshot restore (whole-instance loss):** same as above but start from **Restore snapshot** instead of PITR.

---

## 4. S3 attachment durability

Attachments are uploaded through the API to **S3** (bucket `goaml-attachments`, per-tenant prefixes) and
pulled into the submission ZIP at submit time. Limits enforced by the engine: **5 MB/file, 20 MB/report**.

**Bucket configuration to enable:**
- **Versioning ON** — protects against accidental/malicious overwrite or delete; a deleted object leaves a
  delete-marker and the prior version is recoverable.
- **Default encryption (SSE-KMS or SSE-S3)** — on.
- **Block Public Access** — fully on (these are AML attachments; never public).
- **Bucket policy:** access only via the pod's IRSA role (least-privilege: `s3:GetObject`/`PutObject`/
  `ListBucket` on `goaml-attachments/*`). No static keys.
- **MFA-delete** is optional/heavyweight — versioning already covers the common cases.

**Lifecycle policy (PROPOSED — align to §6 retention):**
- Transition objects to **S3 Standard-IA** after ~90 days (attachments are rarely re-read after submit).
- Optionally transition to **Glacier / Glacier Deep Archive** after ~1 year for cold retention.
- **Expire noncurrent (old) versions** after a bounded window (e.g. 90 days) so versioning doesn't grow
  unbounded — but keep current versions for the full **records-retention** period in §6.
- **Do not** add a blanket object-expiration that would delete an attachment still inside its retention
  window. Expiration of the *record* is a deliberate disposal step (§6), not a lifecycle convenience.

---

## 5. Tenant-schema export / restore

Because isolation is **schema-per-tenant** (`tenant_<uuid>`), a single tenant can be exported or restored
without touching others — useful for offboarding, a per-tenant legal hold/export request, or recovering one
tenant from a logical error.

**Export one tenant (logical):**
```bash
# Dump just the tenant's schema (data + DDL). Run from a host with network access to RDS.
pg_dump "$SPRING_DATASOURCE_URL" \
  --schema='tenant_<uuid>' \
  --no-owner --no-privileges \
  -Fc -f tenant_<uuid>_$(date +%Y%m%d).dump
```
Also export the tenant's `public`-schema rows it depends on (its `tenant`, `tenant_goaml_config`,
`tenant_external_ref`, `tenant_goaml_person`, users) if a full standalone restore is needed.

**Restore one tenant into a target DB:**
```bash
pg_restore --no-owner --no-privileges \
  -d "$TARGET_DATASOURCE_URL" \
  tenant_<uuid>_YYYYMMDD.dump
```
> The schema name is embedded in the dump. To restore *as a different* tenant id you must rename the schema
> and re-key the `public` rows — non-trivial; prefer restoring under the original id.

**Encrypt exports at rest** (they contain PII + filed reports) and treat them with the same retention/disposal
rules as the live data (§6). Never leave a tenant dump on a developer laptop — see the dev foot-gun note in §8.

---

## 6. Records-retention schedule (PROPOSED — needs legal/FIU sign-off)

> **UAE AML record-keeping is typically ≥ 5 years** from the end of the business relationship or the date of
> the transaction/report (and a supervisor may extend it). The exact trigger, period, and disposal method
> **must be confirmed with legal/compliance + the relevant UAE supervisory body / FIU** before relying on
> this table. Until confirmed, **err on the side of keeping** records.

| Record class | Where it lives | Proposed retention | Disposal after retention |
|---|---|---|---|
| **Filed report XML** (the submitted goAML report) | `report.report_xml` (tenant schema) | **≥ 5 years** from submission/acceptance | Purge the XML + the report row (or anonymise), logged. |
| **Report attachments** | S3 `goaml-attachments/<tenant>/…` | **≥ 5 years** (match the report) | Delete the object + all noncurrent versions, logged. |
| **Submission records** (reportkey, FIU status, outcome) | `submission` (tenant schema) | **≥ 5 years** | Purge with the report. |
| **Audit log** (who created/approved/submitted, login events) | `audit_log` (tenant schema) | **≥ 5 years** (compliance evidence — often the *longest*) | Purge oldest-first after the window. |
| **In-app notifications** | `notification` (tenant schema) | **Operational only — ~90 days** | Routine cleanup; not a regulated record. |
| **Import jobs / row results** | `import_job` (tenant schema) | **~1 year** (operational/troubleshooting) | Routine cleanup. |
| **Tenant config / FIU creds** | `tenant_goaml_config` + Secrets Manager | **Life of the tenant + retention tail** | Revoke FIU creds on offboarding; keep config metadata until records expire. |
| **DB backups / PITR** | RDS | **35 days operational** (§3) — *not* the records archive | Rolls off automatically. |
| **Long-term records archive** | RDS snapshot exports to S3 (Parquet) and/or S3 attachment cold storage | **= the records period (≥ 5y)** | Deliberate disposal, logged. |

**Implementation notes / gaps to close before this is real:**
- There is **no automated retention/disposal job today.** Retention is currently "keep everything" by default
  (safe). A scheduled disposal job (per-tenant, dry-run-first, audited) is a **future work item** — until it
  exists, disposal is manual and must be logged.
- **Legal hold overrides disposal:** if a tenant/record is under investigation, exclude it from any disposal
  job. The disposal job must support a per-tenant/per-record hold flag.
- RDS automated backups (35 days) satisfy *recovery*, not *retention*. For the ≥ 5-year archive use periodic
  **snapshot exports to S3** (RDS → Export to S3, Parquet) on a quarterly/annual cadence, encrypted, lifecycle
  to Glacier.

---

## 7. Flyway migration rollback stance

**Flyway is forward-only.** There are no `undo`/down migrations, and `ddl-auto: none` means Hibernate never
alters DDL. The rollback story is therefore:

- **A bad migration is fixed with a *new* forward migration**, never by editing or deleting a committed one
  (editing a applied migration breaks Flyway's checksum and the app won't start).
- **If a deploy with a bad migration is caught before/at rollout:** roll the *application* back
  (`helm rollback`, see the runbook), but note the schema change may already be applied — a pure app rollback
  does **not** revert DDL. If the new column/table is additive and the old app ignores it, the old app still
  runs (this is the common, safe case — prefer additive migrations).
- **If the migration corrupted/destroyed data:** this is a **restore** event, not a rollback — PITR to just
  before the migration ran (§3), then redeploy the *known-good* app image whose migrations match that schema
  version. Do not let a newer app run migrations against the restored DB until the bad migration is replaced
  by a corrected forward one.
- **Tenant migrations:** these run per tenant schema. (Note: the audit flagged that tenant Flyway historically
  ran only at provisioning — a separate finding (A2) adds a startup runner that migrates all ACTIVE tenant
  schemas before serving traffic. DR planning should assume that runner exists: a restored DB will be
  migrated up to the running app's version on boot, gated by readiness.)

**Operational rule:** prefer **additive, backward-compatible** migrations (add column/table, backfill, switch
reads later) so an app rollback never strands the schema.

---

## 8. Local-dev / seeding foot-guns (operational hygiene)

- **Pre-PII backup bundle:** `dev/goaml-prePII-backup.bundle` contains pre-purge real PII and **must be
  deleted by the user** (never pushed). It is *not* a DR artifact — do not treat it as a backup.
- **Tenant dumps on laptops:** §5 exports contain PII + filed reports. Encrypt them and delete after use.
- **Dev-seeder `rentity_id` foot-gun:** the dev seeder (`config/dev/DevDataSeeder`) creates a demo tenant but
  **no `tenant_goaml_config` row**, so a fresh environment has `rentity_id = 0` → every report builds
  **INVALID** until someone seeds a config row with a **positive** `rentity_id`. This recurs every fresh env
  and silently looks like a data bug. **Mitigation (owned by the Java/G3 agent — see the remediation progress
  note):** either seed a *loudly-marked placeholder* `tenant_goaml_config` (e.g. `rentity_id` with an obvious
  non-real marker value) **or** log a startup `WARN` when an ACTIVE tenant lacks a goAML config. Until then,
  remember to set a real `rentity_id` per the go-live checklist before expecting VALID reports.

---

## 9. Poller topology (operational — duplicate-polling avoidance)

The submission-status poller (Phase 9, `SubmissionStatusPoller`) has **no distributed lock** by design. It is
only safe to run on **exactly one process**. The default Helm install runs the main Deployment with
`replicaCount: 2` and an HPA (min 2) — so if the poller ran on every replica, each would call the FIU per
cycle (rate-limit / IP-reputation risk with a regulator) and fire **duplicate** author + MLRO notifications.

**Recommended production topology (multi-replica): a dedicated single-replica poller.**
- Set `poller.dedicated: true` in Helm values. This:
  - sets `GOAML_SCHEDULER_ENABLED=false` on the main (request-serving, autoscaled) Deployment, and
  - renders a **separate single-replica poller Deployment** (no HPA) with `GOAML_SCHEDULER_ENABLED=true`.
  - The Service routes only to `goaml.io/role=web` pods, so the poller pod takes no user/API traffic.
- The app honours `GOAML_SCHEDULER_ENABLED` to gate the `@Scheduled` poll loop.

**Single-replica install (e.g. small/dev):** leave `poller.dedicated: false`, set `replicaCount: 1`, and
`autoscaling.enabled: false`. The single pod polls; no duplication possible.

> Alternative (not chosen here, heavier): a Postgres-backed distributed lock (ShedLock) so any replica can
> poll safely. The dedicated-poller topology avoids that dependency and is the infra-side fix.

---

## 10. Pre-go-live operations checklist

- [ ] RDS: automated backups ≥ 35 days, PITR on, storage-encrypted, Multi-AZ (prod), deletion protection on.
- [ ] (Prod DR) Cross-region snapshot copy configured.
- [ ] S3 `goaml-attachments`: versioning on, default encryption on, Block Public Access on, IRSA-scoped policy.
- [ ] S3 lifecycle policy applied (IA/Glacier transitions; bounded noncurrent-version expiry) — without
      expiring records inside their retention window.
- [ ] Retention schedule (§6) **signed off by legal/compliance** against UAE AML record-keeping rules.
- [ ] A documented, tested **restore drill** (PITR → new instance → re-point app → readiness UP) run at least
      once before relying on it.
- [ ] Tenant export/restore (§5) validated on a throwaway tenant.
- [ ] Poller topology chosen (`poller.dedicated: true` for multi-replica) and verified one poller runs.
- [ ] `dev/goaml-prePII-backup.bundle` deleted.

---

**See also:** [`.planning/plans/go-live-integration-runbook.md`](../.planning/plans/go-live-integration-runbook.md)
(deploy + rollback + FIU-outage), [`.planning/plans/phase-e-fiu-go-live-checklist.md`](../.planning/plans/phase-e-fiu-go-live-checklist.md)
(per-tenant FIU config), [`helm/goaml/values.yaml`](../helm/goaml/values.yaml) (`scheduler` / `poller` /
`prometheusRule` / `serviceMonitor` keys).
