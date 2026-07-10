-- ============================================================================
-- Email uniqueness becomes PER-TENANT.
--
-- Before: app_user.email was globally UNIQUE (one email = one human across the
-- whole platform). We now scope identity to a tenant so the same email can be a
-- user of two different client tenants — login therefore takes a company id
-- (tenant.slug) to disambiguate. See DefaultAuthService.
--
-- Postgres note: a plain UNIQUE(tenant_id, email) will NOT stop duplicate
-- platform (SUPER_ADMIN) emails, because those rows have tenant_id IS NULL and
-- NULLs are distinct under a composite unique. Two partial indexes express the
-- real rule cleanly:
--   * tenant users  — email unique WITHIN a tenant
--   * platform users — email stays globally unique among the NULL-tenant rows
--
-- Safe on existing data: email was globally unique, so no per-tenant collision
-- can already exist.
-- ============================================================================

-- Drop the implicit UNIQUE constraint created by `email ... UNIQUE` in V2.
ALTER TABLE app_user DROP CONSTRAINT app_user_email_key;

-- Tenant users: (tenant_id, email) unique for the non-NULL-tenant rows.
CREATE UNIQUE INDEX app_user_email_tenant_uk
    ON app_user (tenant_id, email) WHERE tenant_id IS NOT NULL;

-- Platform (SUPER_ADMIN) users: email globally unique among the NULL-tenant rows.
CREATE UNIQUE INDEX app_user_email_global_uk
    ON app_user (email) WHERE tenant_id IS NULL;
