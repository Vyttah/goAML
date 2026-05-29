package com.vyttah.goaml.persistence.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-tenant audit log row.
 *
 * <p>No {@code schema} on {@link Table} — the table is resolved via the current Postgres
 * {@code search_path}, which {@link com.vyttah.goaml.tenant.SchemaMultiTenantConnectionProvider}
 * sets to the active tenant's schema. Querying without a tenant bound to
 * {@link com.vyttah.goaml.tenant.TenantContext} will hit {@code public.audit_log} (which
 * does not exist) and error — that's intentional: tenant-scoped data must be tenanted.
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column
    private String summary;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    protected AuditLogEntity() {}

    public AuditLogEntity(UUID id, String action, String summary) {
        this.id = id;
        this.action = action;
        this.summary = summary;
    }

    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getCorrelationId() { return correlationId; }
    public String getSummary() { return summary; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }

    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
}
