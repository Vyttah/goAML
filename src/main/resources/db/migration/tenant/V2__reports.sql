-- ============================================================================
-- Per-tenant report storage (Phase 7.1). Runs against tenant_<uuid_hex> via the
-- programmatic tenant Flyway (TenantProvisioningService) and on app upgrades.
-- No schema qualifier — Flyway's `schemas` setting makes the tenant schema default.
--
-- Report content is stored as the structured input (JSONB) + the marshalled goAML
-- XML snapshot + metadata, rather than a normalized report tree: the XSD-generated
-- model is the structure authority, so a relational mirror would only drift.
-- ============================================================================

-- ---- report -----------------------------------------------------------------
CREATE TABLE report (
    id                 UUID         PRIMARY KEY,
    entity_reference   VARCHAR(255) NOT NULL UNIQUE,          -- per-tenant idempotency key
    report_code        VARCHAR(16)  NOT NULL,                 -- DPMSR (others later)
    rentity_id         INTEGER      NOT NULL,                 -- FIU-assigned RE id (from tenant_goaml_config)
    status             VARCHAR(16)  NOT NULL DEFAULT 'DRAFT', -- DRAFT|VALID|INVALID|SUBMITTED|ACCEPTED|REJECTED|FAILED
    input              JSONB        NOT NULL,                 -- the report input the engine built from
    report_xml         TEXT         NULL,                     -- marshalled goAML XML snapshot
    validation_errors  JSONB        NULL,                     -- [{severity,path,code,message}, ...]
    created_by         UUID         NULL,                     -- app_user.id of the author
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_report_status     ON report(status);
CREATE INDEX idx_report_created_at ON report(created_at);

-- ---- submission -------------------------------------------------------------
-- One row per submission attempt to the FIU. reportkey is the FIU's handle, used
-- to poll status (the async poller lands in Phase 9; Phase 7 refreshes on demand).
CREATE TABLE submission (
    id            UUID         PRIMARY KEY,
    report_id     UUID         NOT NULL REFERENCES report(id) ON DELETE CASCADE,
    reportkey     VARCHAR(128) NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'SUBMITTED', -- SUBMITTED|ACCEPTED|REJECTED|FAILED
    errors        TEXT         NULL,
    submitted_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_submission_report ON submission(report_id);
