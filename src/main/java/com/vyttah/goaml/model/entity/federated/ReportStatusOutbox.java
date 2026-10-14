package com.vyttah.goaml.model.entity.federated;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One report-status transition waiting to reach the AML platform (shared {@code public.report_status_outbox};
 * goAML ↔ ERP wiring, Phase 2 / S2, decision D22).
 *
 * <p>Written by {@code ReportStatusOutboxService} next to the status change itself and drained by
 * {@code ReportStatusPublisher}. Explicitly {@code schema = "public"} — like {@link TenantExternalRef} — so a
 * hook running with a tenant schema bound in {@code TenantContext} still writes to the shared table.
 *
 * <p>{@link #getStatus()} is <strong>the transition that happened</strong>, which is not always the report's
 * resulting status: a submission the FIU refuses is restored to {@code VALID} so it can be reworked, yet the
 * event says {@code REJECTED}. The ERP needs to know what happened to its filing, not what goAML's work queue
 * looks like afterwards.
 */
@Getter
@Setter
@Entity
@Table(name = "report_status_outbox", schema = "public")
public class ReportStatusOutbox {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "entity_reference", nullable = false, length = 255)
    private String entityReference;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(nullable = false)
    private int attempts;

    /** Null means due now. */
    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    /** Non-null means delivered; the publisher never considers the row again. */
    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ReportStatusOutbox() {}

    public ReportStatusOutbox(UUID id, UUID tenantId, UUID reportId, String entityReference, String status,
                              String remark, UUID actorUserId, OffsetDateTime occurredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.reportId = reportId;
        this.entityReference = entityReference;
        this.status = status;
        this.remark = remark;
        this.actorUserId = actorUserId;
        this.occurredAt = occurredAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (occurredAt == null) occurredAt = createdAt;
    }

    /** Delivered: stop considering this row, and clear the last failure so the record is not misleading. */
    public void markSent(OffsetDateTime at) {
        this.sentAt = at;
        this.lastError = null;
        this.nextAttemptAt = null;
    }

    /** One failed attempt: count it, remember why, and come back no earlier than {@code retryAt}. */
    public void markFailed(String error, OffsetDateTime retryAt) {
        this.attempts = this.attempts + 1;
        this.lastError = error;
        this.nextAttemptAt = retryAt;
    }
}
