package com.vyttah.goaml.service.tenant;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2 sub-step 2.2 verification.
 *
 * <p>Drives {@link TenantProvisioningService} end-to-end against a real Postgres:
 * a successful provision must (a) create a {@code tenant_<id_hex>} schema, (b) run the
 * per-tenant Flyway template (so {@code audit_log} exists), (c) insert the
 * {@code public.tenant} row, and (d) create the initial TENANT_ADMIN user with a
 * BCrypt-hashed password. Duplicate slug/email should be rejected up front.
 */
@SpringBootTest(classes = GoamlApplication.class)
@Testcontainers
class TenantProvisioningServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TenantProvisioningService provisioningService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void provisionCreatesSchemaTenantRowAndAdminUser() {
        TenantProvisioningRequest req = new TenantProvisioningRequest(
                "alpha-jewellers",
                "Alpha Jewellers FZE",
                "AE",
                "admin@alpha.test",
                "Sup3rS3cret!",
                "Alpha", "Admin");

        Tenant tenant = provisioningService.provision(req);

        assertThat(tenant.getId()).isNotNull();
        assertThat(tenant.getSlug()).isEqualTo("alpha-jewellers");
        // Schema name is now derived from the company id (slug), hyphens → underscores.
        assertThat(tenant.getSchemaName()).isEqualTo("tenant_alpha_jewellers");
        assertThat(tenant.getStatus()).isEqualTo("ACTIVE");

        Long auditLogTables = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = ? AND table_name = 'audit_log'
                """, Long.class, tenant.getSchemaName());
        assertThat(auditLogTables).isEqualTo(1L);

        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM public.app_user WHERE email = ?",
                String.class, "admin@alpha.test");
        assertThat(storedHash)
                .as("password must be BCrypt-hashed, never stored plaintext")
                .startsWith("$2");
        assertThat(storedHash).doesNotContain("Sup3rS3cret!");

        Long adminRoleCount = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM public.user_role ur
                  JOIN public.role r ON r.id = ur.role_id
                  JOIN public.app_user u ON u.id = ur.user_id
                 WHERE u.email = ? AND r.name = 'TENANT_ADMIN'
                """, Long.class, "admin@alpha.test");
        assertThat(adminRoleCount).isEqualTo(1L);
    }

    @Test
    void provisionRejectsDuplicateSlug() {
        TenantProvisioningRequest first = new TenantProvisioningRequest(
                "beta-bullion", "Beta Bullion DMCC", "AE",
                "admin1@beta.test", "P@ssw0rd!", "Beta", "One");
        provisioningService.provision(first);

        TenantProvisioningRequest duplicate = new TenantProvisioningRequest(
                "beta-bullion", "Beta Different Name", "AE",
                "admin2@beta.test", "P@ssw0rd!", "Beta", "Two");

        assertThatThrownBy(() -> provisioningService.provision(duplicate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slug already in use");
    }

    @Test
    void provisionRejectsUnknownJurisdiction() {
        TenantProvisioningRequest req = new TenantProvisioningRequest(
                "gamma-gems", "Gamma Gems Co", "ZZ",
                "admin@gamma.test", "P@ssw0rd!", "Gamma", "Admin");

        assertThatThrownBy(() -> provisioningService.provision(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown jurisdiction code");
    }
}
