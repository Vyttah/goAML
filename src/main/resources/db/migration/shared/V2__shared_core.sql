-- ============================================================================
-- Phase 2 — shared/admin schema for the goAML platform.
-- Lives in PostgreSQL's `public` schema; one row per platform-wide concept.
-- Per-tenant data (reports, submissions, attachments, audit, notifications)
-- lives in a `tenant_<uuid>` schema created on tenant provisioning — see
-- db/migration/tenant/V1__tenant_init.sql.
-- ============================================================================

-- ---- jurisdictions ----------------------------------------------------------
-- One row per FIU instance the platform can target. Only UAE ships in v1.
CREATE TABLE jurisdiction (
    code             VARCHAR(8)  PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    currency_code    VARCHAR(3)   NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO jurisdiction (code, name, currency_code) VALUES
    ('AE', 'United Arab Emirates', 'AED');

-- ---- tenants ----------------------------------------------------------------
-- A client Reporting Entity (RE). Each tenant gets its own Postgres schema
-- (tenant_<id_hex>) provisioned via TenantProvisioningService.
CREATE TABLE tenant (
    id               UUID         PRIMARY KEY,
    slug             VARCHAR(64)  NOT NULL UNIQUE,
    name             VARCHAR(255) NOT NULL,
    jurisdiction_code VARCHAR(8)  NOT NULL REFERENCES jurisdiction(code),
    schema_name      VARCHAR(80)  NOT NULL UNIQUE,
    status           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SUSPENDED | DELETED
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_tenant_status ON tenant(status);

-- ---- RBAC roles -------------------------------------------------------------
-- Four fixed roles for the platform. New roles are platform releases, not data.
CREATE TABLE role (
    id               SMALLSERIAL  PRIMARY KEY,
    name             VARCHAR(32)  NOT NULL UNIQUE,
    description      VARCHAR(255) NOT NULL
);

INSERT INTO role (name, description) VALUES
    ('SUPER_ADMIN',  'Vyttah platform administrator (cross-tenant)'),
    ('TENANT_ADMIN', 'Administrator within a single client tenant'),
    ('MLRO',         'Money Laundering Reporting Officer — can submit reports'),
    ('ANALYST',      'Can build and validate reports; cannot submit');

-- ---- users ------------------------------------------------------------------
-- tenant_id is NULL for SUPER_ADMIN platform users; NOT NULL for tenant users.
-- email is globally unique (one human identity = one row).
CREATE TABLE app_user (
    id               UUID         PRIMARY KEY,
    tenant_id        UUID         NULL REFERENCES tenant(id),
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(255) NOT NULL,  -- BCrypt
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NOT NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | DISABLED
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login_at    TIMESTAMPTZ  NULL
);
CREATE INDEX idx_app_user_tenant ON app_user(tenant_id);

-- ---- user_role (m:n) --------------------------------------------------------
CREATE TABLE user_role (
    user_id          UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_id          SMALLINT     NOT NULL REFERENCES role(id),
    PRIMARY KEY (user_id, role_id)
);

-- ---- tenant_goaml_config ----------------------------------------------------
-- Per-tenant goAML B2B configuration. Credentials themselves live in AWS Secrets
-- Manager — `secrets_path` is the reference. rentity_id is the FIU-assigned RE id.
CREATE TABLE tenant_goaml_config (
    id               UUID         PRIMARY KEY,
    tenant_id        UUID         NOT NULL UNIQUE REFERENCES tenant(id) ON DELETE CASCADE,
    jurisdiction_code VARCHAR(8)  NOT NULL REFERENCES jurisdiction(code),
    rentity_id       INTEGER      NOT NULL,
    base_url         VARCHAR(512) NOT NULL,
    secrets_path     VARCHAR(512) NOT NULL,
    auth_mode        VARCHAR(16)  NOT NULL DEFAULT 'TOKEN',  -- TOKEN | BASIC
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ---- refresh_token ----------------------------------------------------------
-- Stored as a hash; the raw token only ever travels in the user's cookie/body.
CREATE TABLE refresh_token (
    id               UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash       VARCHAR(255) NOT NULL UNIQUE,
    issued_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMPTZ  NOT NULL,
    revoked_at       TIMESTAMPTZ  NULL
);
CREATE INDEX idx_refresh_token_user ON refresh_token(user_id);
