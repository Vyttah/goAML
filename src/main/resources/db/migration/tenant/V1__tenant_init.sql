-- ============================================================================
-- Per-tenant schema template. Run by TenantProvisioningService at provisioning
-- time against `tenant_<uuid_hex>` (and again on app upgrades for every tenant
-- schema). The CURRENT schema is determined by Flyway's `schemas` setting on
-- the programmatic Flyway instance — no schema qualifier needed below.
-- ============================================================================

-- ---- audit_log --------------------------------------------------------------
-- Per-tenant immutable event log. Written by AuditInterceptor on every
-- security/state-changing action (login, report build/validate/submit/delete,
-- credential change, user invite). Phase 2 baseline; later phases add columns.
CREATE TABLE audit_log (
    id              UUID         PRIMARY KEY,
    actor_user_id   UUID         NULL,                          -- nullable: anonymous/system actions
    actor_email     VARCHAR(255) NULL,
    action          VARCHAR(64)  NOT NULL,                       -- e.g. 'USER.LOGIN', 'REPORT.SUBMIT'
    entity_type     VARCHAR(64)  NULL,
    entity_id       VARCHAR(64)  NULL,
    correlation_id  VARCHAR(64)  NULL,                           -- request/trace correlation
    summary         TEXT         NULL,                           -- before/after diff or freeform
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_log_action     ON audit_log(action);
CREATE INDEX idx_audit_log_occurred_at ON audit_log(occurred_at);
