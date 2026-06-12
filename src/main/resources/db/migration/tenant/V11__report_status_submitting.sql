-- ============================================================================
-- Submit race fix: DefaultSubmissionService now claims a report atomically
-- (UPDATE … SET status='SUBMITTING' WHERE status=<VALID|APPROVED>) before the
-- ZIP is built / the FIU is called, so two concurrent submits can no longer
-- both reach the FIU. 'SUBMITTING' is a short-lived in-flight state (restored
-- to the prior status on any failure; moved to 'SUBMITTED' on success), but
-- the report.status CHECK runs on every write, so the vocabulary must include
-- it. V9 is already applied in dev DBs — recreate rather than edit.
-- ============================================================================

ALTER TABLE report DROP CONSTRAINT report_status_chk;
ALTER TABLE report
    ADD CONSTRAINT report_status_chk CHECK (status IN (
        'DRAFT','VALID','INVALID','PENDING_REVIEW','APPROVED','SUBMITTING','SUBMITTED',
        'ACCEPTED','REJECTED','FAILED'
    ));
