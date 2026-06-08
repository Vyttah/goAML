package com.vyttah.goaml.model.entity.federated;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resolves a source system's org reference to a goAML tenant (shared {@code public.tenant_external_ref}).
 * A mapping table rather than a column on {@code tenant} because accounting and screening may use different
 * org identifiers for the same tenant. Unique on {@code (source_system, external_org_ref)}.
 */
@Getter
@Entity
@Table(name = "tenant_external_ref", schema = "public")
public class TenantExternalRef {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", nullable = false, length = 32)
    private SourceSystem sourceSystem;

    @Column(name = "external_org_ref", nullable = false)
    private String externalOrgRef;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TenantExternalRef() {}

    public TenantExternalRef(UUID id, UUID tenantId, SourceSystem sourceSystem, String externalOrgRef) {
        this.id = id;
        this.tenantId = tenantId;
        this.sourceSystem = sourceSystem;
        this.externalOrgRef = externalOrgRef;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
