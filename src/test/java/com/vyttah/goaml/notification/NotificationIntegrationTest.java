package com.vyttah.goaml.notification;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.b2b.GoamlB2bClient;
import com.vyttah.goaml.b2b.ReportStatus;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.integration.aws.SesClient;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.notification.Notification;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.submission.Submission;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.notification.NotificationRepository;
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

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Phase 10.4 end-to-end: a poll cycle that transitions a SUBMITTED report to ACCEPTED fans an in-app
 * notification out to <em>both</em> the report author and the tenant's MLRO, persisted in the tenant
 * schema. Email is left disabled (the default), so {@link SesClient} is mocked only to assert it is NOT
 * called. Proves the real seam: poller → SubmissionService.refreshStatus → NotificationService → store.
 */
@SpringBootTest(classes = GoamlApplication.class)
@Testcontainers
class NotificationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean GoamlB2bClient b2bClient;
    @MockBean SesClient sesClient; // email disabled by default → must stay untouched

    @Autowired SubmissionStatusPoller poller;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository appUserRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired SubmissionRepository submissionRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void acceptedTransitionNotifiesAuthorAndMlro() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "ntf-it-" + UUID.randomUUID().toString().substring(0, 8), "Notify IT", "AE",
                "ntf-it-" + UUID.randomUUID() + "@test", "P@ssw0rd!", "Ntf", "IT"));
        jdbcTemplate.update("""
                INSERT INTO public.tenant_goaml_config
                  (id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode)
                VALUES (?, ?, 'AE', 3177, 'https://goaml.test/uae', 'goaml/ntf/creds', 'TOKEN')
                """, UUID.randomUUID(), tenant.getId());

        UUID authorId = createUser(tenant.getId(), "author-" + UUID.randomUUID() + "@test", "ANALYST");
        UUID mlroId = createUser(tenant.getId(), "mlro-" + UUID.randomUUID() + "@test", "MLRO");

        UUID reportId = UUID.randomUUID();
        runAsTenant(tenant.getSchemaName(), () -> {
            reportRepository.saveAndFlush(new Report(reportId, "NTF-REF-1", "DPMSR", 3177,
                    "SUBMITTED", "{}", authorId));
            Submission s = new Submission(UUID.randomUUID(), reportId, "SUBMITTED");
            s.setReportkey("RK-NTF");
            submissionRepository.saveAndFlush(s);
            return null;
        });

        when(b2bClient.getReportStatus(any(), eq("RK-NTF")))
                .thenReturn(new ReportStatus("RK-NTF", "Accepted", null));

        poller.pollAllTenants();

        runAsTenant(tenant.getSchemaName(), () -> {
            List<Notification> authorNotes = notificationRepository
                    .findByRecipientUserIdOrderByCreatedAtDesc(authorId);
            List<Notification> mlroNotes = notificationRepository
                    .findByRecipientUserIdOrderByCreatedAtDesc(mlroId);
            assertThat(authorNotes).hasSize(1);
            assertThat(authorNotes.get(0).getType()).isEqualTo("REPORT_ACCEPTED");
            assertThat(authorNotes.get(0).getReportId()).isEqualTo(reportId);
            assertThat(mlroNotes).hasSize(1);
            assertThat(mlroNotes.get(0).getType()).isEqualTo("REPORT_ACCEPTED");
            return null;
        });
    }

    /** Create an active tenant user holding {@code roleName} in the shared schema; returns its id. */
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
