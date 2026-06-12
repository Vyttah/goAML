-- ============================================================================
-- C2 (shared enum CHECKs) + C3 (trusted_service.default_role constrained) +
-- A5 (default_role must be a real role, never an accidental MLRO typo).
-- Lives in public. Additive + safe on existing data — only CHECK constraints.
--
-- Every allowed value is grounded in the Java vocabulary, NOT guessed:
--   app_user.status             — V2 ("ACTIVE | DISABLED"); DefaultAdminService.USER_STATUSES = {ACTIVE,DISABLED}
--   tenant.status               — V2 ("ACTIVE | SUSPENDED | DELETED")
--   trusted_service.status      — V3 ("ACTIVE | DISABLED")
--   trusted_service.source_system / external_identity / tenant_external_ref
--                               — SourceSystem enum = {ACCOUNTING, SCREENING}
--   tenant_goaml_config.auth_mode — B2bAuthMode enum = {TOKEN, BASIC}
--   trusted_service.default_role  — role.name seed set (V2): SUPER_ADMIN, TENANT_ADMIN, MLRO, ANALYST
--                                   (NULL allowed → least-privilege ANALYST default in code)
-- Risk note (pre-prod data only): an out-of-set row makes the ALTER fail by design.
-- ============================================================================

-- ---- app_user.status -------------------------------------------------------
ALTER TABLE app_user
    ADD CONSTRAINT app_user_status_chk CHECK (status IN ('ACTIVE','DISABLED'));

-- ---- tenant.status ---------------------------------------------------------
ALTER TABLE tenant
    ADD CONSTRAINT tenant_status_chk CHECK (status IN ('ACTIVE','SUSPENDED','DELETED'));

-- ---- trusted_service.status + source_system --------------------------------
ALTER TABLE trusted_service
    ADD CONSTRAINT trusted_service_status_chk CHECK (status IN ('ACTIVE','DISABLED'));
ALTER TABLE trusted_service
    ADD CONSTRAINT trusted_service_source_system_chk CHECK (source_system IN ('ACCOUNTING','SCREENING'));

-- ---- source_system on the two federated mapping tables ---------------------
ALTER TABLE external_identity
    ADD CONSTRAINT external_identity_source_system_chk CHECK (source_system IN ('ACCOUNTING','SCREENING'));
ALTER TABLE tenant_external_ref
    ADD CONSTRAINT tenant_external_ref_source_system_chk CHECK (source_system IN ('ACCOUNTING','SCREENING'));

-- ---- tenant_goaml_config.auth_mode -----------------------------------------
ALTER TABLE tenant_goaml_config
    ADD CONSTRAINT tenant_goaml_config_auth_mode_chk CHECK (auth_mode IN ('TOKEN','BASIC'));

-- ---- C3/A5: trusted_service.default_role -----------------------------------
-- A column that can grant MLRO must not accept a typo. Constrain it to the seeded
-- role.name set; NULL stays allowed (code maps NULL → ANALYST, the least-privilege
-- default). FK-to-role is impossible here (role.id is the PK, name is only UNIQUE,
-- and a partial value set is fine), so a CHECK against the known names is used.
ALTER TABLE trusted_service
    ADD CONSTRAINT trusted_service_default_role_chk
        CHECK (default_role IS NULL OR default_role IN ('SUPER_ADMIN','TENANT_ADMIN','MLRO','ANALYST'));
