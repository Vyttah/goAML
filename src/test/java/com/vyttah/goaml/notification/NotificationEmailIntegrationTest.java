package com.vyttah.goaml.notification;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.b2b.GoamlB2bClient;
import com.vyttah.goaml.b2b.ReportStatus;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.integration.aws.SesClient;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.submission.Submission;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.repository.submission.SubmissionRepository;
import com.vyttah.goaml.scheduler.SubmissionStatusPoller;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 10.4: with {@code goaml.notifications.email.enabled=true}, the same poll-driven transition also
 * dispatches an SES email per recipient. The {@link SesClient} is mocked (no real SES); this proves the
 * config gate wires through end-to-end (the deterministic per-branch coverage is in
 * {@code DefaultNotificationServiceTest}).
 */
@SpringBootTest(classes = GoamlApplication.class, properties = "goaml.notifications.email.enabled=true")
@Testcontainers
class NotificationEmailIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean GoamlB2bClient b2bClient;
    @MockBean SesClient sesClient;

    @Autowired SubmissionStatusPoller poller;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository appUserRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired SubmissionRepository submissionRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void acceptedTransitionEmailsRecipientsWhenEnabled() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "eml-it-" + UUID.randomUUID().toString().substring(0, 8), "Email IT", "AE",
                "eml-it-" + UUID.randomUUID() + "@test", "P@ssw0rd!", "Eml", "IT"));
        jdbcTemplate.update("""
                INSERT INTO public.tenant_goaml_config
                  (id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode)
                VALUES (?, ?, 'AE', 3177, 'https://goaml.test/uae', 'goaml/eml/creds', 'TOKEN')
                """, UUID.randomUUID(), tenant.getId());

        String mlroEmail = "mlro-" + UUID.randomUUID() + "@test";
        UUID mlroId = createUser(tenant.getId(), mlroEmail, "MLRO");

        UUID reportId = UUID.randomUUID();
        runAsTenant(tenant.getSchemaName(), () -> {
            reportRepository.saveAndFlush(new Report(reportId, "EML-REF-1", "DPMSR", 3177,
                    "SUBMITTED", "{}", null));   // no author → MLRO is the only recipient
            Submission s = new Submission(UUID.randomUUID(), reportId, "SUBMITTED");
            s.setReportkey("RK-EML");
            submissionRepository.saveAndFlush(s);
            return null;
        });

        when(b2bClient.getReportStatus(any(), eq("RK-EML")))
                .thenReturn(new ReportStatus("RK-EML", "Accepted", null));

        poller.pollAllTenants();

        verify(sesClient, times(1)).send(eq(mlroEmail), eq("Report accepted"), anyString());
    }

    private UUID createUser(UUID tenantId, String email, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        AppUser user = new AppUser(UUID.randomUUID(), tenantId, email, "hash", "First", "Last", "ACTIVE");
        user.addRole(role);
        appUserRepository.saveAndFlush(user);
        return user.getId();
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
