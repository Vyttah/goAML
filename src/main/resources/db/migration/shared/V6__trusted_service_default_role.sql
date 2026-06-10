-- ============================================================================
-- goAML-as-AML-microservice (G1.3) — per-trusted-service default provisioning role.
-- When a federated user is JIT-provisioned (POST /api/v1/auth/federated/token), the
-- registered source may declare the goAML role its users land with. NULL keeps the
-- prior least-privilege default (ANALYST), so existing services are unchanged.
-- The AML screening cockpit registers MLRO so a cockpit user can both CREATE and
-- APPROVE/SUBMIT reports — the cockpit treats approval as a workflow stage, not
-- segregation-of-duties (see Phase D.1). See plans/goaml-as-aml-microservice.md.
-- ============================================================================
ALTER TABLE trusted_service ADD COLUMN default_role VARCHAR(32);
