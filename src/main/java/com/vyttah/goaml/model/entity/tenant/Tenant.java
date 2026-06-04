package com.vyttah.goaml.model.entity.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A client Reporting Entity (RE) onboarded onto the platform.
 *
 * <p>Each tenant has its own Postgres schema named {@code tenant_<id_hex>}, created by
 * {@code TenantProvisioningService}. The {@link #schemaName} is the routing key used by
 * {@link com.vyttah.goaml.config.tenant.SchemaMultiTenantConnectionProvider}.
 */
@Entity
@Table(name = "tenant", schema = "public")
public class Tenant {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(name = "jurisdiction_code", nullable = false, length = 8)
    private String jurisdictionCode;

    @Column(name = "schema_name", nullable = false, unique = true, length = 80)
    private String schemaName;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Tenant() {}

    public Tenant(UUID id, String slug, String name, String jurisdictionCode,
                        String schemaName, String status) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.jurisdictionCode = jurisdictionCode;
        this.schemaName = schemaName;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getJurisdictionCode() { return jurisdictionCode; }
    public String getSchemaName() { return schemaName; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
