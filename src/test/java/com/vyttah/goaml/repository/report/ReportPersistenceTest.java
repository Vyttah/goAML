package com.vyttah.goaml.repository.report;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.submission.Submission;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.submission.SubmissionRepository;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 7.1 persistence proof (Testcontainers): the tenant `report`/`submission` tables exist and round-trip
 * (JSONB input, status, FK, unique entity_reference), and the shared `tenant_goaml_config` reads back via
 * {@link TenantGoamlConfigRepository}.
 */
@SpringBootTest(classes = GoamlApplication.class)
@Testcontainers
class ReportPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TenantProvisioningService provisioningService;
    @Autowired ReportRepository reportRepository;
    @Autowired SubmissionRepository submissionRepository;
    @Autowired TenantGoamlConfigRepository tenantGoamlConfigRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void reportAndSubmissionRoundTripInTenantSchema() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "rep-a", "Report Tenant A", "AE", "rep-a@test", "P@ssw0rd!", "Rep", "A"));

        UUID reportId = UUID.randomUUID();
        runAsTenant(tenant.getSchemaName(), () -> {
            Report report = new Report(reportId, "PAY-0349-INV-0224", "DPMSR", 3177,
                    "VALID", "{\"reason\":\"cash gold sale\"}", UUID.randomUUID());
            report.setReportXml("<report><report_code>DPMSR</report_code></report>");
            report.setValidationErrors("[]");
            reportRepository.saveAndFlush(report);

            Submission submission = new Submission(UUID.randomUUID(), reportId, "SUBMITTED");
            submission.setReportkey("RK-123");
            submissionRepository.saveAndFlush(submission);
            return null;
        });

        runAsTenant(tenant.getSchemaName(), () -> {
            Report read = reportRepository.findById(reportId).orElseThrow();
            assertThat(read.getEntityReference()).isEqualTo("PAY-0349-INV-0224");
            assertThat(read.getReportCode()).isEqualTo("DPMSR");
            assertThat(read.getStatus()).isEqualTo("VALID");
            assertThat(read.getInput()).contains("reason").contains("cash gold sale");
            assertThat(read.getReportXml()).contains("DPMSR");

            List<Submission> subs = submissionRepository.findByReportIdOrderBySubmittedAtDesc(reportId);
            assertThat(subs).hasSize(1);
            assertThat(subs.get(0).getReportkey()).isEqualTo("RK-123");
            return null;
        });
    }

    @Test
    void duplicateEntityReferenceViolatesUniqueConstraint() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "rep-b", "Report Tenant B", "AE", "rep-b@test", "P@ssw0rd!", "Rep", "B"));

        runAsTenant(tenant.getSchemaName(), () -> {
            reportRepository.saveAndFlush(new Report(UUID.randomUUID(), "DUP-001", "DPMSR", 3177,
                    "VALID", "{}", null));
            assertThatThrownBy(() -> reportRepository.saveAndFlush(
                    new Report(UUID.randomUUID(), "DUP-001", "DPMSR", 3177, "VALID", "{}", null)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            return null;
        });
    }

    @Test
    void tenantGoamlConfigReadsBack() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "rep-c", "Report Tenant C", "AE", "rep-c@test", "P@ssw0rd!", "Rep", "C"));

        jdbcTemplate.update("""
                INSERT INTO public.tenant_goaml_config
                  (id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode)
                VALUES (?, ?, 'AE', 3177, 'https://goaml.test/uae', 'goaml/rep-c/creds', 'TOKEN')
                """, UUID.randomUUID(), tenant.getId());

        TenantGoamlConfig config = tenantGoamlConfigRepository.findByTenantId(tenant.getId()).orElseThrow();
        assertThat(config.getRentityId()).isEqualTo(3177);
        assertThat(config.getBaseUrl()).isEqualTo("https://goaml.test/uae");
        assertThat(config.getSecretsPath()).isEqualTo("goaml/rep-c/creds");
        assertThat(config.getAuthMode()).isEqualTo("TOKEN");
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
