package com.vyttah.goaml.model.entity.goamlconfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-tenant goAML B2B configuration (shared {@code public.tenant_goaml_config}). Credentials themselves
 * live in AWS Secrets Manager — {@link #secretsPath} is the reference; {@link #rentityId} is the FIU-assigned
 * Reporting Entity id used on the report header. Read-mostly at submit time; the admin API (Phase 13.2)
 * upserts the config fields.
 */
@Getter
@Entity
@Table(name = "tenant_goaml_config", schema = "public")
public class TenantGoamlConfig {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Setter
    @Column(name = "jurisdiction_code", nullable = false, length = 8)
    private String jurisdictionCode;

    @Setter
    @Column(name = "rentity_id", nullable = false)
    private Integer rentityId;

    @Setter
    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Setter
    @Column(name = "secrets_path", nullable = false, length = 512)
    private String secretsPath;

    @Setter
    @Column(name = "auth_mode", nullable = false, length = 16)
    private String authMode;

    /**
     * Phase 1.5 — per-tenant opt-in to fully-automatic FIU submission of auto-created drafts (no human in
     * the loop). Default {@code false}: the safe path is validated draft → MLRO one-click submit.
     */
    @Setter
    @Column(name = "auto_submit", nullable = false)
    private boolean autoSubmit;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TenantGoamlConfig() {}

    /** New config for a tenant; fields are then set via the setters before save (admin upsert). */
    public TenantGoamlConfig(UUID id, UUID tenantId) {
        this.id = id;
        this.tenantId = tenantId;
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
}
