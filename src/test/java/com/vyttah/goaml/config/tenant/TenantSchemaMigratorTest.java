package com.vyttah.goaml.config.tenant;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A2 verification: the boot-time tenant-schema migrator brings an existing tenant schema — provisioned
 * before a later tenant migration — up to the latest version, and is a no-op with zero tenants.
 *
 * <p>The "old schema state" is simulated by migrating a fresh tenant schema only up to {@code target=7}
 * (so it lacks V8's {@code report.client_metadata} column), inserting its {@code public.tenant} row, then
 * running the migrator and asserting V8 landed. We bypass {@code DevDataSeeder} and start with no tenants so
 * the assertions are deterministic.
 */
@SpringBootTest(classes = GoamlApplication.class,
        properties = "goaml.tenant.migrate-on-startup=false") // we drive the migrator explicitly in the test
@Testcontainers
class TenantSchemaMigratorTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TenantSchemaMigrator migrator;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    /**
     * Both phases run in one method so the initial zero-tenant state is deterministic (tests share one
     * Postgres container, so a second method couldn't assume an empty {@code tenant} table).
     */
    @Test
    void zeroTenantsIsNoOpThenAnOlderTenantSchemaIsMigratedForward() {
        // Phase 1 — zero ACTIVE tenants: the sweep must not throw and must migrate nothing.
        tenantRepository.deleteAll();
        assertThat(migrator.migrateAllActiveTenants())
                .as("a boot with no tenants is a safe no-op")
                .isZero();

        // Phase 2 — a tenant schema frozen at an older state (V1..V7 only — no V8 client_metadata column).
        UUID id = UUID.randomUUID();
        String schema = "tenant_" + id.toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA \"" + schema + "\"");
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration/tenant")
                .target("7")
                .load()
                .migrate();
        assertThat(columnExists(schema, "report", "client_metadata"))
                .as("precondition: the older schema lacks the V8 column")
                .isFalse();

        // Register it as an ACTIVE tenant (the migrator enumerates public.tenant by status).
        tenantRepository.save(new Tenant(id, "older-" + id, "Older Tenant", "AE", schema, "ACTIVE"));

        // Run the boot-time sweep — it brings the existing schema up to the latest tenant migration.
        int migrated = migrator.migrateAllActiveTenants();

        assertThat(migrated).isGreaterThanOrEqualTo(1);
        assertThat(columnExists(schema, "report", "client_metadata"))
                .as("the migrator brought the existing tenant schema up to V8")
                .isTrue();
    }

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    TenantMigrationProperties properties;

    /**
     * Failure isolation (default mode): one tenant whose migration fails (a non-empty schema with no Flyway
     * history → Flyway refuses) must not stop the other tenants from being migrated — it is logged, counted
     * on the failure metric, and skipped. With {@code fail-fast=true} the same situation aborts the sweep.
     */
    @Test
    void aFailingTenantIsSkippedCountedAndDoesNotBlockTheOthersUnlessFailFast() {
        tenantRepository.deleteAll();

        // BAD tenant: a pre-existing non-empty schema without Flyway history → migrate() throws.
        UUID badId = UUID.randomUUID();
        String badSchema = "tenant_" + badId.toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA \"" + badSchema + "\"");
        jdbc.execute("CREATE TABLE \"" + badSchema + "\".legacy_junk (id INT)");
        tenantRepository.save(new Tenant(badId, "bad-" + badId, "Bad Tenant", "AE", badSchema, "ACTIVE"));

        // GOOD tenant: a fresh schema Flyway can migrate from scratch.
        UUID goodId = UUID.randomUUID();
        String goodSchema = "tenant_" + goodId.toString().replace("-", "");
        tenantRepository.save(new Tenant(goodId, "good-" + goodId, "Good Tenant", "AE", goodSchema, "ACTIVE"));

        int migrated = migrator.migrateAllActiveTenants();

        assertThat(migrated).as("the good tenant still migrated").isEqualTo(1);
        assertThat(columnExists(goodSchema, "report", "client_metadata")).isTrue();
        assertThat(meterRegistry.get("goaml.tenant.migration.failures")
                .tag("tenant", badId.toString()).counter().count())
                .as("the failed tenant fired the alert metric").isEqualTo(1.0);

        // fail-fast mode restores the old all-or-nothing boot behavior.
        TenantSchemaMigrator failFast = new TenantSchemaMigrator(
                tenantRepository, dataSource, properties, meterRegistry, true);
        assertThatThrownBy(failFast::migrateAllActiveTenants)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(badId.toString());

        tenantRepository.deleteAll();
    }

    private boolean columnExists(String schema, String table, String column) {
        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """, Long.class, schema, table, column);
        return count != null && count > 0;
    }
}
