-- ============================================================================
-- Per-tenant report attachments (Phase 8.2). Runs against tenant_<uuid_hex> via the
-- programmatic tenant Flyway (TenantProvisioningService) and on app upgrades.
-- No schema qualifier — Flyway's `schemas` setting makes the tenant schema default.
--
-- Only metadata + the S3 object key live here; the bytes live in S3 (goaml.aws.s3.bucket)
-- under a per-tenant/per-report key prefix. At submit time the bytes are pulled from S3
-- into the submission ZIP (within PackagingLimits.UAE_DEFAULT: 5 MB/file, 20 MB/ZIP).
-- ============================================================================

CREATE TABLE attachment (
    id            UUID         PRIMARY KEY,
    report_id     UUID         NOT NULL REFERENCES report(id) ON DELETE CASCADE,
    filename      VARCHAR(255) NOT NULL,                 -- original upload filename
    content_type  VARCHAR(255) NULL,                     -- declared MIME type
    size_bytes    BIGINT       NOT NULL,                 -- byte length (<= 5 MB per PackagingLimits)
    s3_key        VARCHAR(1024) NOT NULL,                -- tenants/{tenantId}/reports/{reportId}/{id}-{filename}
    uploaded_by   UUID         NULL,                     -- app_user.id of the uploader
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_attachment_report ON attachment(report_id);
