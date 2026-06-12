package com.vyttah.goaml.repository.notification;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.notification.Notification;
import com.vyttah.goaml.model.entity.tenant.Tenant;
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
 * Phase 10.1 persistence proof (Testcontainers): the tenant {@code notification} table exists and
 * round-trips, the recipient queries return rows newest-first, the unread filter excludes read rows, and
 * {@code findByIdAndRecipientUserId} scopes to the owner. Notifications are per-tenant (resolved via the
 * active {@code search_path}); the recipient id references {@code public.app_user} but is not FK-bound.
 */
@SpringBootTest(classes = GoamlApplication.class)
@Testcontainers
class NotificationPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TenantProvisioningService provisioningService;
    @Autowired NotificationRepository notificationRepository;

    @Test
    void notificationsRoundTripAndQueryByRecipientWithUnreadFilter() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "ntf-a", "Notification Tenant A", "AE", "ntf-a@test", "P@ssw0rd!", "Ntf", "A"));

        UUID recipient = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID readId = UUID.randomUUID();
        UUID unreadId = UUID.randomUUID();

        runAsTenant(tenant.getSchemaName(), () -> {
            // one read, one unread for the recipient; one for a different user
            Notification read = new Notification(readId, recipient, "REPORT_ACCEPTED", reportId,
                    "Report accepted", "Your report was accepted by the FIU.");
            read.markRead();
            notificationRepository.saveAndFlush(read);
            notificationRepository.saveAndFlush(new Notification(unreadId, recipient, "REPORT_REJECTED",
                    reportId, "Report rejected", "The FIU rejected your report."));
            notificationRepository.saveAndFlush(new Notification(UUID.randomUUID(), other, "REPORT_FAILED",
                    reportId, "Submission failed", "A transport error occurred."));
            // V10: REPORT_PENDING_REVIEW (the MLRO awaiting-review ping) must pass the type CHECK — the
            // V9 constraint omitted it, so this insert used to violate the constraint.
            notificationRepository.saveAndFlush(new Notification(UUID.randomUUID(), other,
                    "REPORT_PENDING_REVIEW", reportId, "Report awaiting submission",
                    "A validated draft awaits MLRO review."));
            return null;
        });

        runAsTenant(tenant.getSchemaName(), () -> {
            List<Notification> all = notificationRepository
                    .findByRecipientUserIdOrderByCreatedAtDesc(recipient);
            assertThat(all).hasSize(2).extracting(Notification::getRecipientUserId)
                    .containsOnly(recipient);

            List<Notification> unread = notificationRepository
                    .findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(recipient);
            assertThat(unread).extracting(Notification::getId).containsExactly(unreadId);

            assertThat(notificationRepository.findByIdAndRecipientUserId(readId, recipient)).isPresent();
            // not the owner → not found for them
            assertThat(notificationRepository.findByIdAndRecipientUserId(readId, other)).isEmpty();
            return null;
        });
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
