-- ============================================================================
-- Z1 (goAML ↔ ERP wiring, step 16, decisions D41/D42): the per-tenant review
-- gate is gone. A report now goes VALID → SUBMITTING → SUBMITTED directly; the
-- two review statuses, the reviewer columns and the "draft awaiting review"
-- notification no longer exist anywhere in the code.
--
-- Order matters: the data collapse runs BEFORE each CHECK rewrite, otherwise
-- the new constraint fails validation on the rows that still carry the old
-- values. V11 is already applied in dev DBs — recreate rather than edit.
-- ============================================================================

-- 1. Collapse the stored review statuses onto VALID (a report that was awaiting
--    or had passed review is, without the gate, simply a validated report).
UPDATE report SET status = 'VALID' WHERE status IN ('PENDING_REVIEW', 'APPROVED');

-- 2. The reviewer trail goes with the gate (V7 added the three columns; the V9
--    index idx_report_reviewed_by is dropped together with its column).
ALTER TABLE report
    DROP COLUMN reviewed_by,
    DROP COLUMN reviewed_at,
    DROP COLUMN review_remark;

-- 3. report.status vocabulary without the two review values (V11's list minus
--    PENDING_REVIEW / APPROVED; SUBMITTING stays — it is the submit claim).
ALTER TABLE report DROP CONSTRAINT report_status_chk;
ALTER TABLE report
    ADD CONSTRAINT report_status_chk CHECK (status IN (
        'DRAFT','VALID','INVALID','SUBMITTING','SUBMITTED','ACCEPTED','REJECTED','FAILED'
    ));

-- 4. The "validated draft awaits MLRO review" ping is no longer written
--    (DefaultNotificationService.notifyDraftAwaitingReview deleted). Remove the
--    rows first, then the value from the type vocabulary (V10's list minus it).
DELETE FROM notification WHERE type = 'REPORT_PENDING_REVIEW';

ALTER TABLE notification DROP CONSTRAINT notification_type_chk;
ALTER TABLE notification
    ADD CONSTRAINT notification_type_chk CHECK (type IN (
        'REPORT_ACCEPTED','REPORT_REJECTED','REPORT_FAILED'
    ));
