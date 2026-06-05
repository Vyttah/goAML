package com.vyttah.goaml.repository;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9.1: the discovery queries the poller relies on — {@code ReportRepository.findByStatus}
 * (tenant-scoped) and {@code TenantRepository.findByStatus} (shared `public`).
 */
@SpringBootTest(classes = GoamlApplication.class)
@Testcontainers
class SchedulerQueriesTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TenantProvisioningService provisioningService;
    @Autowired ReportRepository reportRepository;
    @Autowired TenantRepository tenantRepository;

    @Test
    void reportFindByStatusReturnsOnlyMatchingInTenant() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "sched-a", "Sched Tenant A", "AE", "sched-a@test", "P@ssw0rd!", "Sch", "A"));

        runAsTenant(tenant.getSchemaName(), () -> {
            reportRepository.saveAndFlush(new Report(UUID.randomUUID(), "S-1", "DPMSR", 3177, "SUBMITTED", "{}", null));
            reportRepository.saveAndFlush(new Report(UUID.randomUUID(), "S-2", "DPMSR", 3177, "SUBMITTED", "{}", null));
            reportRepository.saveAndFlush(new Report(UUID.randomUUID(), "V-1", "DPMSR", 3177, "VALID", "{}", null));
            return null;
        });

        runAsTenant(tenant.getSchemaName(), () -> {
            List<Report> submitted = reportRepository.findByStatus("SUBMITTED");
            assertThat(submitted).hasSize(2)
                    .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo("SUBMITTED"));
            assertThat(reportRepository.findByStatus("ACCEPTED")).isEmpty();
            return null;
        });
    }

    @Test
    void tenantFindByStatusListsActiveTenants() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "sched-b", "Sched Tenant B", "AE", "sched-b@test", "P@ssw0rd!", "Sch", "B"));

        List<Tenant> active = tenantRepository.findByStatus("ACTIVE");
        assertThat(active).extracting(Tenant::getId).contains(tenant.getId());
        assertThat(active).allSatisfy(t -> assertThat(t.getStatus()).isEqualTo("ACTIVE"));
        assertThat(tenantRepository.findByStatus("DELETED")).extracting(Tenant::getId).doesNotContain(tenant.getId());
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
