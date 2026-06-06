package com.vyttah.goaml.model.dto.notification;

import com.vyttah.goaml.model.entity.notification.Notification;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API view of an in-app {@link Notification} (Phase 10). {@code readAt == null} means unread.
 */
public record NotificationView(UUID id, String type, UUID reportId, String title, String body,
                               OffsetDateTime readAt, OffsetDateTime createdAt) {

    public static NotificationView from(Notification n) {
        return new NotificationView(n.getId(), n.getType(), n.getReportId(), n.getTitle(), n.getBody(),
                n.getReadAt(), n.getCreatedAt());
    }
}
