package com.vyttah.goaml.model.entity.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A per-tenant in-app notification: the durable record that a report transition
 * ({@code ACCEPTED}/{@code REJECTED}/{@code FAILED}) was fanned out to a recipient.
 *
 * <p>Tenant-scoped (no {@code @Table} schema) — resolves via the active tenant's {@code search_path},
 * exactly like {@link com.vyttah.goaml.model.entity.audit.AuditLog}. The {@link #recipientUserId} is a
 * {@code public.app_user.id}; it is intentionally not a JPA association because {@code app_user} lives in
 * the shared {@code public} schema, not the tenant schema.
 */
@Getter
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "report_id")
    private UUID reportId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column
    private String body;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Notification() {}

    public Notification(UUID id, UUID recipientUserId, String type, UUID reportId,
                        String title, String body) {
        this.id = id;
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.reportId = reportId;
        this.title = title;
        this.body = body;
    }

    /** Mark this notification read (idempotent — the first read wins the timestamp). */
    public void markRead() {
        if (readAt == null) {
            readAt = OffsetDateTime.now();
        }
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
