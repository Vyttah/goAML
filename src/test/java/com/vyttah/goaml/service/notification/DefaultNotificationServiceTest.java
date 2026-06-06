package com.vyttah.goaml.service.notification;

import com.vyttah.goaml.config.notification.NotificationProperties;
import com.vyttah.goaml.integration.aws.SesAccessException;
import com.vyttah.goaml.integration.aws.SesClient;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.notification.Notification;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.notification.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link DefaultNotificationService}: repos + {@link SesClient} mocked. Covers recipient
 * resolution (author + MLROs, de-duped), the in-app-then-email ordering, the email gate, best-effort email,
 * the no-op transitions, and list/markRead scoping.
 */
class DefaultNotificationServiceTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final SesClient sesClient = mock(SesClient.class);

    private final UUID tenantId = UUID.randomUUID();

    private DefaultNotificationService service(boolean emailEnabled) {
        return new DefaultNotificationService(notificationRepository, appUserRepository, sesClient,
                new NotificationProperties(new NotificationProperties.Email(emailEnabled, "no-reply@goaml.test")));
    }

    private AppUser user(UUID id, String email) {
        return new AppUser(id, tenantId, email, "hash", "First", "Last", "ACTIVE");
    }

    private Report report(UUID createdBy) {
        return new Report(UUID.randomUUID(), "DPMSR-REF-1", "DPMSR", 3177, "SUBMITTED", "{}", createdBy);
    }

    @Test
    void notifiesAuthorAndMlrosDedupedWritingInAppRows() {
        UUID authorId = UUID.randomUUID();
        UUID otherMlroId = UUID.randomUUID();
        AppUser author = user(authorId, "author@tenant.test");        // also an MLRO
        AppUser otherMlro = user(otherMlroId, "mlro2@tenant.test");
        Report report = report(authorId);

        when(appUserRepository.findById(authorId)).thenReturn(Optional.of(author));
        when(appUserRepository.findByTenantIdAndStatusAndRoles_Name(tenantId, "ACTIVE", "MLRO"))
                .thenReturn(List.of(author, otherMlro));

        service(false).notifyReportTransition(report, "ACCEPTED", tenantId);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getRecipientUserId)
                .containsExactlyInAnyOrder(authorId, otherMlroId); // author de-duped to one
        assertThat(saved.getAllValues()).extracting(Notification::getType).containsOnly("REPORT_ACCEPTED");
        assertThat(saved.getAllValues()).extracting(Notification::getReportId).containsOnly(report.getId());
        verifyNoInteractions(sesClient); // email disabled
    }

    @Test
    void sendsEmailPerRecipientWhenEnabled() {
        UUID mlroId = UUID.randomUUID();
        when(appUserRepository.findByTenantIdAndStatusAndRoles_Name(tenantId, "ACTIVE", "MLRO"))
                .thenReturn(List.of(user(mlroId, "mlro@tenant.test")));

        service(true).notifyReportTransition(report(null), "REJECTED", tenantId);

        verify(notificationRepository, times(1)).save(any());
        verify(sesClient).send(eq("mlro@tenant.test"), eq("Report rejected"), anyString());
    }

    @Test
    void skipsEmailWhenDisabledButStillWritesInApp() {
        UUID mlroId = UUID.randomUUID();
        when(appUserRepository.findByTenantIdAndStatusAndRoles_Name(tenantId, "ACTIVE", "MLRO"))
                .thenReturn(List.of(user(mlroId, "mlro@tenant.test")));

        service(false).notifyReportTransition(report(null), "FAILED", tenantId);

        verify(notificationRepository, times(1)).save(any());
        verifyNoInteractions(sesClient);
    }

    @Test
    void emailFailureIsSwallowedAndOtherRecipientsStillEmailed() {
        AppUser a = user(UUID.randomUUID(), "a@tenant.test");
        AppUser b = user(UUID.randomUUID(), "b@tenant.test");
        when(appUserRepository.findByTenantIdAndStatusAndRoles_Name(tenantId, "ACTIVE", "MLRO"))
                .thenReturn(List.of(a, b));
        doThrow(new SesAccessException("boom")).when(sesClient).send(eq("a@tenant.test"), anyString(), anyString());

        // must NOT throw — best-effort
        service(true).notifyReportTransition(report(null), "ACCEPTED", tenantId);

        verify(notificationRepository, times(2)).save(any()); // both in-app rows written
        verify(sesClient).send(eq("a@tenant.test"), anyString(), anyString());
        verify(sesClient).send(eq("b@tenant.test"), anyString(), anyString()); // continued past the failure
    }

    @Test
    void nonNotifiableStatusIsNoOp() {
        service(true).notifyReportTransition(report(UUID.randomUUID()), "SUBMITTED", tenantId);

        verifyNoInteractions(notificationRepository, appUserRepository, sesClient);
    }

    @Test
    void noRecipientsIsNoOp() {
        when(appUserRepository.findByTenantIdAndStatusAndRoles_Name(tenantId, "ACTIVE", "MLRO"))
                .thenReturn(List.of());

        service(true).notifyReportTransition(report(null), "ACCEPTED", tenantId);

        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(sesClient);
    }

    @Test
    void blankRecipientEmailIsSkippedForSend() {
        AppUser withEmail = user(UUID.randomUUID(), "ok@tenant.test");
        AppUser noEmail = user(UUID.randomUUID(), "  ");
        when(appUserRepository.findByTenantIdAndStatusAndRoles_Name(tenantId, "ACTIVE", "MLRO"))
                .thenReturn(List.of(withEmail, noEmail));

        service(true).notifyReportTransition(report(null), "ACCEPTED", tenantId);

        verify(notificationRepository, times(2)).save(any());   // both get an in-app row
        verify(sesClient).send(eq("ok@tenant.test"), anyString(), anyString());
        verify(sesClient, never()).send(eq("  "), anyString(), anyString());
    }

    @Test
    void nullEmailConfigSkipsEmailGracefully() {
        UUID mlroId = UUID.randomUUID();
        when(appUserRepository.findByTenantIdAndStatusAndRoles_Name(tenantId, "ACTIVE", "MLRO"))
                .thenReturn(List.of(user(mlroId, "mlro@tenant.test")));
        DefaultNotificationService svc = new DefaultNotificationService(
                notificationRepository, appUserRepository, sesClient, new NotificationProperties(null));

        svc.notifyReportTransition(report(null), "ACCEPTED", tenantId); // must not NPE

        verify(notificationRepository, times(1)).save(any());
        verifyNoInteractions(sesClient);
    }

    @Test
    void authorNotFoundFallsBackToMlrosOnly() {
        UUID missingAuthorId = UUID.randomUUID();
        UUID mlroId = UUID.randomUUID();
        when(appUserRepository.findById(missingAuthorId)).thenReturn(Optional.empty());
        when(appUserRepository.findByTenantIdAndStatusAndRoles_Name(tenantId, "ACTIVE", "MLRO"))
                .thenReturn(List.of(user(mlroId, "mlro@tenant.test")));

        service(false).notifyReportTransition(report(missingAuthorId), "ACCEPTED", tenantId);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getRecipientUserId()).isEqualTo(mlroId);
    }

    @Test
    void listRoutesUnreadFilter() {
        UUID userId = UUID.randomUUID();
        DefaultNotificationService svc = service(false);

        svc.list(userId, true);
        verify(notificationRepository).findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId);

        svc.list(userId, false);
        verify(notificationRepository).findByRecipientUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void markReadMarksOwnNotification() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        Notification n = new Notification(notifId, userId, "REPORT_ACCEPTED", UUID.randomUUID(), "t", "b");
        when(notificationRepository.findByIdAndRecipientUserId(notifId, userId)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notification result = service(false).markRead(userId, notifId);

        assertThat(result.getReadAt()).isNotNull();
        verify(notificationRepository).save(n);
    }

    @Test
    void markReadOnAnothersNotificationThrows() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        when(notificationRepository.findByIdAndRecipientUserId(notifId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(false).markRead(userId, notifId))
                .isInstanceOf(NotificationExceptions.NotificationNotFoundException.class);
    }
}
