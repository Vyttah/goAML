-- ============================================================================
-- S2 — report-status outbox (shared/public schema).
-- goAML ↔ ERP wiring, Phase 2, decision D22: every review / submission
-- transition of a report belonging to a tenant that the ERP files into writes a
-- row here, inside the same work as the status change. ReportStatusPublisher
-- then pushes due rows to the AML platform, which forwards them to the ERP's
-- accounting service (S3 → S5). At-least-once: a row is only marked sent on a
-- 2xx, so a lost acknowledgement means a redelivery, which the receivers
-- de-duplicate (AML on event_id, accounting on occurred_at).
--
-- Shared, not per-tenant, for the same reason tenant_external_ref is: the
-- publisher is one background sweep across every tenant, and a per-schema table
-- would make it N queries over N search_path switches. tenant_id carries the
-- ownership instead.
--
-- Deliberately NOT a foreign key to public.tenant: V2__shared_core.sql:55 shows
-- what a non-cascading FK to that table costs (app_user_tenant_id_fkey is the
-- one FK without ON DELETE CASCADE, and it is why a leftover tenant breaks
-- TenantSchemaMigratorTest's deletes — BLOCKERS.md §F0). An outbox row is a
-- record of something that already happened; it must not be able to block a
-- tenant delete, and it is equally useless once its tenant is gone.
-- ============================================================================
CREATE TABLE report_status_outbox (
    id               UUID PRIMARY KEY,
    tenant_id        UUID         NOT NULL,         -- the goAML tenant whose report transitioned
    report_id        UUID         NOT NULL,         -- report.id, in that tenant's schema
    entity_reference VARCHAR(255) NOT NULL,         -- report.entity_reference; ACC-<erpCompanyId>-<docNo> when the ERP filed it
    status           VARCHAR(32)  NOT NULL,         -- the TRANSITION: PENDING_REVIEW | APPROVED | REJECTED | SUBMITTED | FAILED | ACCEPTED
    remark           TEXT,                          -- the reviewer's remark, or the FIU's error text; the ERP shows it verbatim
    actor_user_id    UUID,                          -- who caused it; null for a background poll (the FIU answered, nobody clicked)
    occurred_at      TIMESTAMPTZ  NOT NULL,         -- when the transition happened; the receivers ORDER by this, not by arrival
    attempts         INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ,                   -- null = due now; set by the backoff after a failure
    sent_at          TIMESTAMPTZ,                   -- non-null = delivered and done; the publisher never looks at it again
    last_error       TEXT,                          -- why the last attempt failed, for the operator
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- The publisher's only query: undelivered rows that are due, oldest transition first. Partial, because a
-- delivered row is dead weight in this index and the table is append-only — in a busy tenant the delivered
-- rows will vastly outnumber the pending ones.
CREATE INDEX idx_report_status_outbox_due
    ON report_status_outbox (next_attempt_at NULLS FIRST, occurred_at)
    WHERE sent_at IS NULL;

-- For the operator answering "what happened to this filing?" from the goAML side.
CREATE INDEX idx_report_status_outbox_reference ON report_status_outbox (entity_reference);
