package com.vyttah.goaml.scheduler;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.b2b.GoamlB2bClient;
import com.vyttah.goaml.b2b.ReportStatus;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.submission.Submission;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.repository.submission.SubmissionRepository;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Phase 9.4 end-to-end: the poller refreshes FIU status across tenants over real Testcontainers Postgres,
 * with the goAML B2B client mocked. Proves cross-tenant discovery + per-tenant schema routing + that the
 * status transition is actually persisted (and isolated per tenant). Invokes the poll cycle directly (not
 * the timer).
 */
@SpringBootTest(classes = GoamlApplication.class)
@Testcontainers
class SubmissionStatusPollerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean GoamlB2bClient b2bClient;

    @Autowired SubmissionStatusPoller poller;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired ReportRepository reportRepository;
    @Autowired SubmissionRepository submissionRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void pollTransitionsSubmittedReportsPerTenant() {
        Seeded a = seedTenant("poll-a", "RK-A");
        Seeded b = seedTenant("poll-b", "RK-B");

        when(b2bClient.getReportStatus(any(), eq("RK-A"))).thenReturn(new ReportStatus("RK-A", "Accepted", null));
        when(b2bClient.getReportStatus(any(), eq("RK-B"))).thenReturn(new ReportStatus("RK-B", "Rejected", "bad field"));

        PollSummary summary = poller.pollAllTenants();

        // both tenants scanned, both reports polled successfully
        assertThat(summary.reportsPolled()).isGreaterThanOrEqualTo(2);
        assertThat(summary.succeeded()).isGreaterThanOrEqualTo(2);
        assertThat(summary.skipped()).isZero();

        // tenant A → ACCEPTED, tenant B → REJECTED, persisted in their own schemas
        assertReportStatus(a, "ACCEPTED");
        assertReportStatus(b, "REJECTED");
    }

    private void assertReportStatus(Seeded seeded, String expected) {
        runAsTenant(seeded.schema, () -> {
            Report r = reportRepository.findById(seeded.reportId).orElseThrow();
            assertThat(r.getStatus()).isEqualTo(expected);
            Submission s = submissionRepository.findByReportIdOrderBySubmittedAtDesc(seeded.reportId).get(0);
            assertThat(s.getStatus()).isEqualTo(expected);
            return null;
        });
    }

    private Seeded seedTenant(String slug, String reportKey) {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                slug + "-" + UUID.randomUUID().toString().substring(0, 8), "Poll " + slug, "AE",
                slug + "-" + UUID.randomUUID() + "@test", "P@ssw0rd!", "Pol", "L"));
        jdbcTemplate.update("""
                INSERT INTO public.tenant_goaml_config
                  (id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode)
                VALUES (?, ?, 'AE', 3177, 'https://goaml.test/uae', 'goaml/poll/creds', 'TOKEN')
                """, UUID.randomUUID(), tenant.getId());

        UUID reportId = UUID.randomUUID();
        runAsTenant(tenant.getSchemaName(), () -> {
            reportRepository.saveAndFlush(new Report(reportId, "POLL-" + reportKey, "DPMSR", 3177,
                    "SUBMITTED", "{}", null));
            Submission s = new Submission(UUID.randomUUID(), reportId, "SUBMITTED");
            s.setReportkey(reportKey);
            submissionRepository.saveAndFlush(s);
            return null;
        });
        return new Seeded(tenant.getSchemaName(), reportId);
    }

    private static <T> T runAsTenant(String tenant, Supplier<T> body) {
        TenantContext.set(tenant);
        try {
            return body.get();
        } finally {
            TenantContext.clear();
        }
    }

    private record Seeded(String schema, UUID reportId) {}
}
