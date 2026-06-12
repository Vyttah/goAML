-- ============================================================================
-- C2 + C3-adjacent + D6 (tenant side). Runs against tenant_<uuid_hex> via the
-- programmatic tenant Flyway (TenantProvisioningService at provisioning time +
-- TenantSchemaMigrator on boot). No schema qualifier — Flyway's `schemas` setting
-- makes the tenant schema default.
--
-- Additive + safe on existing data: only CHECK constraints, one nullable column,
-- and indexes. The CHECK value sets are grounded in the Java status vocabularies:
--   report.status        — DefaultReportService / DefaultReportReviewService / DefaultSubmissionService
--   submission.status    — DefaultSubmissionService (mapStatus + saveFailed)
--   notification.type    — DefaultNotificationService.templateFor (REPORT_* codes)
--   import_job.status     — DefaultIngestionService ("COMPLETED" | "PARTIAL" | "FAILED")
--   import_job.source_type— V5 comment + the two importers (GOAML_XML | DPMSR_CSV)
-- Risk note (pre-prod data only): if any existing row holds a value outside these
-- sets the ALTER fails — that is the intended guard; the state machines never write
-- such a value, so a fresh/clean tenant migrates cleanly.
-- ============================================================================

-- ---- report.status state machine -------------------------------------------
ALTER TABLE report
    ADD CONSTRAINT report_status_chk CHECK (status IN (
        'DRAFT','VALID','INVALID','PENDING_REVIEW','APPROVED','SUBMITTED','ACCEPTED','REJECTED','FAILED'
    ));

-- ---- submission.status state machine ---------------------------------------
ALTER TABLE submission
    ADD CONSTRAINT submission_status_chk CHECK (status IN (
        'SUBMITTED','ACCEPTED','REJECTED','FAILED'
    ));

-- ---- notification.type ------------------------------------------------------
ALTER TABLE notification
    ADD CONSTRAINT notification_type_chk CHECK (type IN (
        'REPORT_ACCEPTED','REPORT_REJECTED','REPORT_FAILED'
    ));

-- ---- import_job.status + source_type ---------------------------------------
ALTER TABLE import_job
    ADD CONSTRAINT import_job_status_chk CHECK (status IN (
        'COMPLETED','PARTIAL','FAILED'
    ));
ALTER TABLE import_job
    ADD CONSTRAINT import_job_source_type_chk CHECK (source_type IN (
        'GOAML_XML','DPMSR_CSV'
    ));

-- ---- D6: submission auditability — the acting MLRO on the submit path -------
-- The acting user previously lived only in audit_log; for a self-contained compliance
-- record the submission attempt row now carries it too. Nullable: pre-existing rows
-- (and the poller's status-driven updates) leave it NULL.
ALTER TABLE submission ADD COLUMN submitted_by UUID NULL;

-- ---- D6: indexes -----------------------------------------------------------
-- report.created_by / report.reviewed_by back the user hard-delete reference checks
-- and author/reviewer lookups; submission.reportkey backs status polling by FIU handle.
CREATE INDEX idx_report_created_by    ON report(created_by);
CREATE INDEX idx_report_reviewed_by   ON report(reviewed_by);
CREATE INDEX idx_submission_reportkey ON submission(reportkey);
