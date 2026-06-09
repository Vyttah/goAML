-- ============================================================================
-- Per-tenant goAML reporting person (the MLRO). Shared schema, alongside
-- tenant_goaml_config — same access pattern (resolved by tenant_id, read at
-- report-build time). Phase A of the suite-cockpit plan.
--
-- The reporting person (goAML <reporting_person>) is the filing MLRO. Storing it
-- as a tenant default lets goAML auto-inject it into every report so callers
-- (the AML cockpit, CSV import, accounting/screening feeds) need not send it —
-- mirrors LexAML's "GoAML Person" application setting.
--
-- Multiple rows may be kept per tenant (e.g. rotating MLROs), but at most ONE is
-- active at a time (the partial unique index), and the active one is the default.
-- ============================================================================

CREATE TABLE tenant_goaml_person (
    id          UUID         PRIMARY KEY,
    tenant_id   UUID         NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    first_name  VARCHAR(255) NOT NULL,
    last_name   VARCHAR(255) NOT NULL,
    gender      VARCHAR(8)   NULL,                    -- goAML gender_type: M | F | -
    ssn         VARCHAR(64)  NULL,
    id_number   VARCHAR(64)  NULL,
    nationality VARCHAR(8)   NULL,                    -- ISO country code
    email       VARCHAR(255) NULL,
    occupation  VARCHAR(255) NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- At most one active reporting person per tenant (the default that gets injected).
CREATE UNIQUE INDEX uq_tenant_goaml_person_active
    ON tenant_goaml_person(tenant_id) WHERE is_active;

CREATE INDEX idx_tenant_goaml_person_tenant ON tenant_goaml_person(tenant_id);
