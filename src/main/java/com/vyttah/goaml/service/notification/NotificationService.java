package com.vyttah.goaml.service.notification;

import com.vyttah.goaml.model.entity.notification.Notification;
import com.vyttah.goaml.model.entity.report.Report;

import java.util.List;
import java.util.UUID;

/**
 * Fans report status transitions out to recipients (Phase 10): writes per-tenant in-app notifications and,
 * when enabled, dispatches SES email. Also serves a user's own notification list / read state.
 *
 * <p>Implemented by {@link DefaultNotificationService}. Recipient resolution reads {@code public.app_user};
 * in-app writes require a bound tenant {@link com.vyttah.goaml.config.tenant.TenantContext} (the caller —
 * the submission service or the request filter — owns the binding).
 */
public interface NotificationService {

    /**
     * Notify the report's author + the tenant's active MLROs that {@code report} transitioned to
     * {@code newStatus}. Only {@code ACCEPTED}/{@code REJECTED}/{@code FAILED} produce a notification;
     * other statuses are a no-op. In-app rows are always written; email only if
     * {@code goaml.notifications.email.enabled}. Email failures are swallowed (best-effort) — the in-app
     * row is the durable record.
     */
    void notifyReportTransition(Report report, String newStatus, UUID tenantId);

    /** A recipient's notifications, newest first; {@code unreadOnly} restricts to unread. */
    List<Notification> list(UUID recipientUserId, boolean unreadOnly);

    /**
     * Mark one of the recipient's own notifications read.
     *
     * @throws NotificationExceptions.NotificationNotFoundException if it isn't the recipient's notification
     */
    Notification markRead(UUID recipientUserId, UUID notificationId);
}
