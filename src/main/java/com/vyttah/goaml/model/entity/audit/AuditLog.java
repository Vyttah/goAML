package com.vyttah.goaml.model.entity.audit;

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
 * Per-tenant audit log row.
 *
 * <p>No {@code schema} on {@link Table} — the table is resolved via the current Postgres
 * {@code search_path}, which {@link com.vyttah.goaml.config.tenant.SchemaMultiTenantConnectionProvider}
 * sets to the active tenant's schema. Querying without a tenant bound to
 * {@link com.vyttah.goaml.config.tenant.TenantContext} will hit {@code public.audit_log} (which
 * does not exist) and error — that's intentional: tenant-scoped data must be tenanted.
 */
@Getter
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private UUID id;

    @Setter
    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Setter
    @Column(name = "actor_email")
    private String actorEmail;

    @Column(nullable = false, length = 64)
    private String action;

    @Setter
    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Setter
    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Setter
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column
    private String summary;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    protected AuditLog() {}

    public AuditLog(UUID id, String action, String summary) {
        this.id = id;
        this.action = action;
        this.summary = summary;
    }

    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = OffsetDateTime.now();
    }
}
