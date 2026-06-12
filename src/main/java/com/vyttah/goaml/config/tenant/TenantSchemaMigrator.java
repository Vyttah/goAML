package com.vyttah.goaml.config.tenant;

import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

/**
 * Brings every existing tenant schema up to the latest per-tenant Flyway version on boot.
 *
 * <p><strong>Why:</strong> a tenant schema is migrated only once — at provisioning
 * ({@code DefaultTenantProvisioningService}). When a later tenant migration ships (e.g. {@code attachment},
 * {@code notification}, {@code import_job}, {@code report.client_metadata}), any tenant provisioned before
 * that release lacks the new table/column and the first request that touches it fails at runtime with
 * {@code relation does not exist}. This runner closes that gap by re-running the SAME programmatic Flyway
 * ({@code classpath:db/migration/tenant}, {@code schemas(<schema>)}) against every ACTIVE tenant schema.
 *
 * <p><strong>When:</strong> it runs as a {@link SmartInitializingSingleton} — after all singletons are
 * constructed but still inside context refresh, i.e. <em>before</em> the embedded web server starts
 * accepting connections. A migration failure propagates out and blocks startup (fail-fast), so the app never
 * serves traffic against a half-migrated tenant.
 *
 * <p><strong>Idempotent + safe with zero tenants:</strong> Flyway no-ops a schema already at the latest
 * version, and an empty tenant list makes the whole sweep a no-op. Gated by
 * {@code goaml.tenant.migrate-on-startup} (default {@code true}; set {@code false} in tests).
 */
@Configuration
@EnableConfigurationProperties(TenantMigrationProperties.class)
public class TenantSchemaMigrator implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaMigrator.class);
    private static final String ACTIVE = "ACTIVE";
    private static final String TENANT_LOCATION = "classpath:db/migration/tenant";

    private final TenantRepository tenantRepository;
    private final DataSource dataSource;
    private final TenantMigrationProperties properties;

    public TenantSchemaMigrator(TenantRepository tenantRepository, DataSource dataSource,
                                TenantMigrationProperties properties) {
        this.tenantRepository = tenantRepository;
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.migrateOnStartup()) {
            log.info("Tenant-schema startup migration disabled (goaml.tenant.migrate-on-startup=false)");
            return;
        }
        migrateAllActiveTenants();
    }

    /**
     * Migrate every ACTIVE tenant schema forward. The tenant list comes from {@code public.tenant} (no tenant
     * context is bound at boot). A failure on any schema aborts startup (fail-fast).
     *
     * @return the number of tenant schemas migrated (for tests / logging)
     */
    public int migrateAllActiveTenants() {
        List<Tenant> tenants = tenantRepository.findByStatus(ACTIVE);
        if (tenants.isEmpty()) {
            log.info("Tenant-schema startup migration: no ACTIVE tenants — nothing to migrate");
            return 0;
        }
        log.info("Tenant-schema startup migration: migrating {} ACTIVE tenant schema(s)", tenants.size());
        int migrated = 0;
        for (Tenant tenant : tenants) {
            try {
                migrateSchema(tenant.getSchemaName());
                migrated++;
            } catch (RuntimeException e) {
                // Fail fast: a tenant left at an older schema state would break at runtime. Surface which
                // tenant failed and block startup rather than serve traffic against it.
                throw new IllegalStateException(
                        "Tenant-schema startup migration failed for tenant " + tenant.getId()
                                + " (" + tenant.getSchemaName() + "): " + e.getMessage(), e);
            }
        }
        log.info("Tenant-schema startup migration: {} schema(s) up to date", migrated);
        return migrated;
    }

    /** Run the per-tenant Flyway against a single tenant schema — same config as provisioning. */
    private void migrateSchema(String schemaName) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .locations(TENANT_LOCATION)
                .load()
                .migrate();
    }
}
