-- ============================================================================
-- Per-tenant screened subjects (Phase 1.5c). Runs against tenant_<uuid_hex> via the
-- programmatic tenant Flyway (TenantProvisioningService) and on app upgrades.
-- No schema qualifier — Flyway's `schemas` setting makes the tenant schema default.
--
-- A screened_subject is a reusable goAML view of a customer the AML screening software pushed
-- (POST /api/v1/integration/screening/subjects): the already-resolved ScreeningPartyPayload is
-- stored verbatim as JSONB so a report can be seeded from it later (the parties are re-derived via
-- ScreeningPartyMapper at seed time). external_ref = SCR-<companyId>-<customerUid> is the
-- idempotency key, so the screening side can re-push safely (upsert).
-- ============================================================================

CREATE TABLE screened_subject (
    id             UUID         PRIMARY KEY,
    external_ref   VARCHAR(255) NOT NULL UNIQUE,             -- SCR-<companyId>-<customerUid>
    subject_type   VARCHAR(16)  NOT NULL,                    -- NATURAL | LEGAL
    display_name   VARCHAR(512) NOT NULL,                    -- customer name (for listing)
    risk_flag      BOOLEAN      NOT NULL DEFAULT FALSE,      -- sanctions screening risk flagged
    payload_json   JSONB        NOT NULL,                    -- the resolved ScreeningPartyPayload
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_screened_subject_created_at ON screened_subject(created_at DESC);
CREATE INDEX idx_screened_subject_risk       ON screened_subject(risk_flag) WHERE risk_flag = TRUE;
