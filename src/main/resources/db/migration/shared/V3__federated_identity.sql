-- ============================================================================
-- Phase 1.5 — federated identity & suite integration (shared/public schema).
-- Lets sibling Vyttah services (accounting, screening) exchange their already-
-- authenticated user for a goAML JWT, resolve to the right goAML tenant, and
-- (accounting) opt a tenant into fully-automatic submission.
-- See .planning/plans/integration-and-auth-architecture.md.
-- ============================================================================

-- ---- trusted_service --------------------------------------------------------
-- A sibling service allowed to call the federated token-exchange + integration
-- push endpoints. It proves itself with a short-lived JWT "service assertion"
-- signed by its private key; goAML verifies the signature against public_key_pem
-- (RS256). One registered service per source system (rotate by replacing the row).
CREATE TABLE trusted_service (
    id                   UUID         PRIMARY KEY,
    source_system        VARCHAR(32)  NOT NULL UNIQUE,        -- ACCOUNTING | SCREENING
    description          VARCHAR(255) NOT NULL DEFAULT '',
    public_key_pem       TEXT         NOT NULL,               -- PEM RSA public key for assertion verification
    jit_provisioning     BOOLEAN      NOT NULL DEFAULT false, -- auto-create unknown users on first exchange
    status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | DISABLED
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ---- external_identity ------------------------------------------------------
-- Maps a sibling system's user to a goAML app_user, so a token-exchange resolves
-- "ACCOUNTING user 4711" → a concrete goAML identity (and its tenant + roles).
CREATE TABLE external_identity (
    id                   UUID         PRIMARY KEY,
    source_system        VARCHAR(32)  NOT NULL,               -- ACCOUNTING | SCREENING
    external_user_id     VARCHAR(255) NOT NULL,               -- the user id in the source system
    external_email       VARCHAR(255),
    app_user_id          UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (source_system, external_user_id)
);
CREATE INDEX idx_external_identity_app_user ON external_identity(app_user_id);

-- ---- tenant_external_ref ----------------------------------------------------
-- Resolves a source system's org reference to a goAML tenant. A mapping table
-- (not a column on tenant) because accounting and screening may use different
-- org identifiers for the same tenant.
CREATE TABLE tenant_external_ref (
    id                   UUID         PRIMARY KEY,
    tenant_id            UUID         NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    source_system        VARCHAR(32)  NOT NULL,               -- ACCOUNTING | SCREENING
    external_org_ref     VARCHAR(255) NOT NULL,               -- the org/company id in the source system
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (source_system, external_org_ref)
);
CREATE INDEX idx_tenant_external_ref_tenant ON tenant_external_ref(tenant_id);

-- ---- tenant_goaml_config.auto_submit ----------------------------------------
-- Per-tenant opt-in to fully-automatic FIU submission (no human in the loop) for
-- auto-created drafts. Default false → the safe path (validated draft → MLRO 1-click).
ALTER TABLE tenant_goaml_config ADD COLUMN auto_submit BOOLEAN NOT NULL DEFAULT false;
