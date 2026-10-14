-- ============================================================================
-- Z1 (goAML ↔ ERP wiring, step 16, decisions D41/D42): the per-tenant review
-- gate is gone — shared-schema half. The tenant half is tenant/V12.
--
-- report_status_outbox.status carries no CHECK (V10 documented the vocabulary
-- in a column comment only), so there is no constraint to rewrite here; the
-- comment is refreshed and the rows that recorded review transitions removed.
-- Delivered or not, a PENDING_REVIEW / APPROVED event describes a transition
-- that no longer exists; the receivers (AML, ERP) drop the same values.
-- ============================================================================

DELETE FROM report_status_outbox WHERE status IN ('PENDING_REVIEW', 'APPROVED');

COMMENT ON COLUMN report_status_outbox.status IS
    'the TRANSITION: SUBMITTED | FAILED | ACCEPTED | REJECTED (review transitions removed in V11 / D41)';

COMMENT ON COLUMN report_status_outbox.remark IS
    'the FIU''s error text; the ERP shows it verbatim';

-- The per-tenant opt-in flag (shared V5) — nothing reads it any more.
ALTER TABLE tenant_goaml_config DROP COLUMN review_required;
