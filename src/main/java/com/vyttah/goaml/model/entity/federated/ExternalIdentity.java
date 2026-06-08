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
 * Maps a sibling system's user to a goAML {@code app_user} (shared {@code public.external_identity}). A
 * federated token-exchange resolves {@code (source_system, external_user_id)} → this row → the goAML user
 * whose tenant + roles drive the issued JWT. Unique on {@code (source_system, external_user_id)}.
 */
@Getter
@Entity
@Table(name = "external_identity", schema = "public")
public class ExternalIdentity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", nullable = false, length = 32)
    private SourceSystem sourceSystem;

    @Column(name = "external_user_id", nullable = false)
    private String externalUserId;

    @Column(name = "external_email")
    private String externalEmail;

    @Column(name = "app_user_id", nullable = false)
    private UUID appUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ExternalIdentity() {}

    public ExternalIdentity(UUID id, SourceSystem sourceSystem, String externalUserId,
                            String externalEmail, UUID appUserId) {
        this.id = id;
        this.sourceSystem = sourceSystem;
        this.externalUserId = externalUserId;
        this.externalEmail = externalEmail;
        this.appUserId = appUserId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
