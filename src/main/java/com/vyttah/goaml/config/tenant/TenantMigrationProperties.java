package com.vyttah.goaml.config.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tenant-schema lifecycle settings bound from {@code goaml.tenant.*}.
 *
 * @param migrateOnStartup when {@code true} (the default), every ACTIVE tenant schema is migrated forward
 *                         through the per-tenant Flyway on boot (before the app serves traffic) so a tenant
 *                         provisioned before a later migration is brought up to date. Set {@code false} in
 *                         tests that don't want the boot-time sweep.
 */
@ConfigurationProperties("goaml.tenant")
public record TenantMigrationProperties(@DefaultValue("true") boolean migrateOnStartup) {
}
