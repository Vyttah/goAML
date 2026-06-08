package com.vyttah.goaml.service.notification;

import com.vyttah.goaml.config.notification.NotificationProperties;
import com.vyttah.goaml.integration.aws.SesAccessException;
import com.vyttah.goaml.integration.aws.SesClient;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.notification.Notification;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link NotificationService}. Resolves recipients (report author + tenant MLROs), writes one
 * in-app {@link Notification} per recipient under the bound tenant, then — if email is enabled — sends a
 * plain-text SES email per recipient. Email is best-effort: a per-recipient {@link SesAccessException} is
 * logged and swallowed so the in-app rows (the durable record) and the other recipients are unaffected.
 */
@Service
@RequiredArgsConstructor
public class DefaultNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(DefaultNotificationService.class);

    private static final String ACTIVE = "ACTIVE";
    private static final String MLRO = "MLRO";

    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;
    private final SesClient sesClient;
    private final NotificationProperties properties;

    @Override
    public void notifyReportTransition(Report report, String newStatus, UUID tenantId) {
        Template tpl = templateFor(newStatus, report.getEntityReference());
        if (tpl == null) {
            return; // not a notifiable transition (e.g. SUBMITTED/DRAFT) — no-op
        }
        fanOut(report, tenantId, tpl);
    }

    @Override
    public void notifyDraftAwaitingReview(Report report, UUID tenantId) {
        fanOut(report, tenantId, new Template("REPORT_PENDING_REVIEW", "Report awaiting submission",
                "Report " + report.getEntityReference() + " is a validated draft awaiting MLRO review and "
                        + "submission to the FIU."));
    }

    /** Resolve recipients, write the durable in-app rows, then (gated, best-effort) email each recipient. */
    private void fanOut(Report report, UUID tenantId, Template tpl) {
        Map<UUID, AppUser> recipients = resolveRecipients(report, tenantId);
        if (recipients.isEmpty()) {
            log.debug("No recipients for report {} ({})", report.getEntityReference(), tpl.type());
            return;
        }

        // 1) in-app rows first — the durable record (requires the caller's bound TenantContext)
        for (AppUser user : recipients.values()) {
            notificationRepository.save(new Notification(UUID.randomUUID(), user.getId(),
                    tpl.type(), report.getId(), tpl.title(), tpl.body()));
        }

        // 2) email second, gated + best-effort (one failure never blocks the rest or the in-app rows)
        if (properties.email() != null && properties.email().enabled()) {
            for (AppUser user : recipients.values()) {
                String email = user.getEmail();
                if (email == null || email.isBlank()) {
                    continue;
                }
                try {
                    sesClient.send(email, tpl.title(), tpl.body());
                } catch (SesAccessException e) {
                    log.warn("Failed to email {} for report {} ({}): {}",
                            email, report.getEntityReference(), tpl.type(), e.getMessage());
                }
            }
        }
    }

    @Override
    public List<Notification> list(UUID recipientUserId, boolean unreadOnly) {
        return unreadOnly
                ? notificationRepository.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(recipientUserId)
                : notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId);
    }

    @Override
    public Notification markRead(UUID recipientUserId, UUID notificationId) {
        Notification n = notificationRepository.findByIdAndRecipientUserId(notificationId, recipientUserId)
                .orElseThrow(() -> new NotificationExceptions.NotificationNotFoundException(
                        "Notification " + notificationId + " not found"));
        n.markRead();
        return notificationRepository.save(n);
    }

    /** Author (if set) + the tenant's active MLROs, de-duplicated by user id (author first). */
    private Map<UUID, AppUser> resolveRecipients(Report report, UUID tenantId) {
        Map<UUID, AppUser> byId = new LinkedHashMap<>();
        if (report.getCreatedBy() != null) {
            appUserRepository.findById(report.getCreatedBy())
                    .ifPresent(author -> byId.put(author.getId(), author));
        }
        for (AppUser mlro : appUserRepository.findByTenantIdAndStatusAndRoles_Name(tenantId, ACTIVE, MLRO)) {
            byId.putIfAbsent(mlro.getId(), mlro);
        }
        return byId;
    }

    private static Template templateFor(String status, String ref) {
        return switch (status) {
            case "ACCEPTED" -> new Template("REPORT_ACCEPTED", "Report accepted",
                    "Report " + ref + " was accepted by the FIU.");
            case "REJECTED" -> new Template("REPORT_REJECTED", "Report rejected",
                    "Report " + ref + " was rejected by the FIU. Review the errors and resubmit.");
            case "FAILED" -> new Template("REPORT_FAILED", "Submission failed",
                    "Submission of report " + ref + " failed (transport/auth). It can be retried.");
            default -> null;
        };
    }

    private record Template(String type, String title, String body) {}
}
