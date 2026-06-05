package com.vyttah.goaml.config.tenant;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.model.entity.audit.AuditLog;
import com.vyttah.goaml.repository.audit.AuditLogRepository;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves schema-per-tenant isolation through the JPA path: writes via tenant A's context
 * land in {@code tenant_A.audit_log} and are invisible to a session bound to tenant B.
 * If this passes, {@link TenantContext} →
 * {@link TenantIdentifierResolver} → {@link SchemaMultiTenantConnectionProvider} is wired
 * correctly end-to-end and the rest of the platform can rely on it.
 */
@SpringBootTest(classes = GoamlApplication.class)
@Testcontainers
class TenantIsolationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TenantProvisioningService provisioningService;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Test
    void perTenantWritesAreInvisibleToOtherTenants() {
        Tenant tenantA = provisioningService.provision(new TenantProvisioningRequest(
                "iso-a", "Isolation Tenant A", "AE",
                "a@iso.test", "P@ssw0rd!", "Iso", "A"));
        Tenant tenantB = provisioningService.provision(new TenantProvisioningRequest(
                "iso-b", "Isolation Tenant B", "AE",
                "b@iso.test", "P@ssw0rd!", "Iso", "B"));

        // Write two rows under tenant A's schema.
        runAsTenant(tenantA.getSchemaName(), () -> {
            auditLogRepository.save(new AuditLog(UUID.randomUUID(), "TEST.WRITE", "alpha-1"));
            auditLogRepository.save(new AuditLog(UUID.randomUUID(), "TEST.WRITE", "alpha-2"));
            return null;
        });

        long countA = runAsTenant(tenantA.getSchemaName(), auditLogRepository::count);
        long countB = runAsTenant(tenantB.getSchemaName(), auditLogRepository::count);

        assertThat(countA)
                .as("tenant A must see its own rows")
                .isEqualTo(2L);
        assertThat(countB)
                .as("tenant B must NOT see tenant A's rows — schema isolation is the whole point")
                .isEqualTo(0L);
    }

    private static <T> T runAsTenant(String tenant, Supplier<T> body) {
        TenantContext.set(tenant);
        try {
            return body.get();
        } finally {
            TenantContext.clear();
        }
    }
}
