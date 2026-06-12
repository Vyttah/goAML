-- ============================================================================
-- Fix: V9's notification.type CHECK omitted 'REPORT_PENDING_REVIEW', which
-- DefaultNotificationService.notifyDraftAwaitingReview writes (the MLRO "a
-- validated draft awaits one-click submit" ping) — so that insert violated the
-- constraint on any V9-migrated tenant. Recreate the CHECK with the complete
-- type vocabulary the code writes (DefaultNotificationService: templateFor +
-- notifyDraftAwaitingReview). V9 is already applied in dev DBs, so this ships
-- as a new migration rather than an edit.
-- ============================================================================

ALTER TABLE notification DROP CONSTRAINT notification_type_chk;
ALTER TABLE notification
    ADD CONSTRAINT notification_type_chk CHECK (type IN (
        'REPORT_ACCEPTED','REPORT_REJECTED','REPORT_FAILED','REPORT_PENDING_REVIEW'
    ));
