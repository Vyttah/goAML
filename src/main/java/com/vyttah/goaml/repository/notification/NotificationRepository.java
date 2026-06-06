package com.vyttah.goaml.repository.notification;

import com.vyttah.goaml.model.entity.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for {@link Notification}. Requires a bound tenant (see {@code ReportRepository}):
 * the table only exists in {@code tenant_<id>} schemas, resolved via the active {@code search_path}.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId);

    List<Notification> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientUserId);

    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);
}
