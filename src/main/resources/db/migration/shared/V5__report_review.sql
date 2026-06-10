-- Phase D.2 — per-tenant review gate.
-- When review_required is true, a VALID report must pass an MLRO review (VALID → PENDING_REVIEW → APPROVED)
-- before it can be submitted to the FIU. Default false keeps standalone tenants on the existing
-- VALID → SUBMITTED path (opt-in everywhere: a TENANT_ADMIN turns it on per tenant).
ALTER TABLE tenant_goaml_config ADD COLUMN review_required BOOLEAN NOT NULL DEFAULT false;
