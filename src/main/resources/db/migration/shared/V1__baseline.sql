-- goAML platform — Phase 1 baseline migration.
-- Sole purpose: prove the Flyway shared-schema pipeline runs end-to-end.
-- The real shared schema (tenant, app_user, role, user_role, tenant_goaml_config,
-- jurisdiction, refresh_token, lookup, audit_log) is created in Phase 2.

CREATE TABLE IF NOT EXISTS schema_baseline (
    id          SERIAL       PRIMARY KEY,
    phase       VARCHAR(32)  NOT NULL,
    note        TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO schema_baseline (phase, note)
VALUES ('phase-1', 'Project skeleton: Gradle + Spring Boot 3.3 + Java 21 + Postgres + Flyway + Actuator');
