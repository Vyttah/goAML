-- ============================================================================
-- Per-tenant in-app notifications (Phase 10.1). Runs against tenant_<uuid_hex> via the
-- programmatic tenant Flyway (TenantProvisioningService) and on app upgrades.
-- No schema qualifier — Flyway's `schemas` setting makes the tenant schema default.
--
-- A notification is the durable record that a report transition (ACCEPTED/REJECTED/FAILED)
-- happened, fanned out to a recipient (report author + tenant MLROs). recipient_user_id is
-- a public.app_user.id; it is NOT FK-constrained here because app_user lives in the shared
-- `public` schema, not the tenant schema. Email (SES) is a best-effort side channel; this
-- row is the source of truth the in-app bell reads.
-- ============================================================================

CREATE TABLE notification (
    id                 UUID         PRIMARY KEY,
    recipient_user_id  UUID         NOT NULL,                 -- public.app_user.id of the recipient
    type               VARCHAR(32)  NOT NULL,                 -- REPORT_ACCEPTED|REPORT_REJECTED|REPORT_FAILED
    report_id          UUID         NULL,                     -- the report whose status changed
    title              VARCHAR(255) NOT NULL,
    body               TEXT         NULL,
    read_at            TIMESTAMPTZ  NULL,                     -- NULL = unread
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notification_recipient ON notification(recipient_user_id, created_at DESC);
CREATE INDEX idx_notification_unread    ON notification(recipient_user_id) WHERE read_at IS NULL;
