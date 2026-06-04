package com.vyttah.goaml.model.entity.goamlconfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-tenant goAML B2B configuration (shared {@code public.tenant_goaml_config}). Credentials themselves
 * live in AWS Secrets Manager — {@link #secretsPath} is the reference; {@link #rentityId} is the FIU-assigned
 * Reporting Entity id used on the report header. Read-mostly; provisioning/admin writes are out of scope here.
 */
@Getter
@Entity
@Table(name = "tenant_goaml_config", schema = "public")
public class TenantGoamlConfig {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "jurisdiction_code", nullable = false, length = 8)
    private String jurisdictionCode;

    @Column(name = "rentity_id", nullable = false)
    private Integer rentityId;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    @Column(name = "secrets_path", nullable = false, length = 512)
    private String secretsPath;

    @Column(name = "auth_mode", nullable = false, length = 16)
    private String authMode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TenantGoamlConfig() {}
}
