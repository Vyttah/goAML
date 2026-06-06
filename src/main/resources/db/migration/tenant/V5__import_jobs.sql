-- ============================================================================
-- Per-tenant report-import jobs (Phase 11.1). Runs against tenant_<uuid_hex> via the
-- programmatic tenant Flyway (TenantProvisioningService) and on app upgrades.
-- No schema qualifier — Flyway's `schemas` setting makes the tenant schema default.
--
-- One row per uploaded file (goAML XML or DPMSR CSV). Processing is synchronous, so the
-- row is written once with its final status + tallies + per-row `results` JSONB array
-- ([{row, entityReference, status, reportId, errors[]}, ...]). The created reports are
-- normal `report` rows; this table is the durable import record + row-level error report.
-- ============================================================================

CREATE TABLE import_job (
    id           UUID         PRIMARY KEY,
    source_type  VARCHAR(16)  NOT NULL,                 -- GOAML_XML | DPMSR_CSV
    filename     VARCHAR(255) NULL,                     -- original upload filename
    status       VARCHAR(16)  NOT NULL,                 -- COMPLETED | PARTIAL | FAILED
    total_rows   INTEGER      NOT NULL DEFAULT 0,
    succeeded    INTEGER      NOT NULL DEFAULT 0,
    failed       INTEGER      NOT NULL DEFAULT 0,
    results      JSONB        NULL,                     -- [{row,entityReference,status,reportId,errors[]}]
    created_by   UUID         NULL,                     -- app_user.id of the uploader
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_import_job_created_at ON import_job(created_at);
