package com.vyttah.goaml.model.entity.federated;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A sibling service registered as allowed to call the federated token-exchange and integration push
 * endpoints (shared {@code public.trusted_service}). It authenticates with a short-lived JWT "service
 * assertion" signed by its private key; goAML verifies the signature against {@link #publicKeyPem} (RS256).
 *
 * <p>{@link #jitProvisioning} controls whether an unknown external user is auto-created on first exchange.
 */
@Getter
@Entity
@Table(name = "trusted_service", schema = "public")
public class TrustedService {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", nullable = false, unique = true, length = 32)
    private SourceSystem sourceSystem;

    @Setter
    @Column(nullable = false)
    private String description;

    @Setter
    @Column(name = "public_key_pem", nullable = false)
    private String publicKeyPem;

    @Setter
    @Column(name = "jit_provisioning", nullable = false)
    private boolean jitProvisioning;

    @Setter
    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TrustedService() {}

    public TrustedService(UUID id, SourceSystem sourceSystem, String description,
                          String publicKeyPem, boolean jitProvisioning, String status) {
        this.id = id;
        this.sourceSystem = sourceSystem;
        this.description = description == null ? "" : description;
        this.publicKeyPem = publicKeyPem;
        this.jitProvisioning = jitProvisioning;
        this.status = status;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
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
