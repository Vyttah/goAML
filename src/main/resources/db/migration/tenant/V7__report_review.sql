-- Phase D.2 — report review metadata. Records who approved/rejected a report in the goAML review stage and
-- their remark, so the system-of-record (and the D.3 "see it all" view) shows the full review trail.
-- Status itself stays on report.status (VALID → PENDING_REVIEW → APPROVED → SUBMITTED).
ALTER TABLE report ADD COLUMN reviewed_by    uuid;
ALTER TABLE report ADD COLUMN reviewed_at    timestamptz;
ALTER TABLE report ADD COLUMN review_remark  text;
