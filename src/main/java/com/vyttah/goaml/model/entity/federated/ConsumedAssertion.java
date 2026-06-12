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

/**
 * A service assertion that has already been consumed (shared {@code public.consumed_assertion}). Recording
 * the assertion's {@code jti} on first verification gives single-use semantics: a second presentation of the
 * same {@code jti} before its {@code exp} is a replay and is rejected. Persisted so the guard survives
 * restarts and holds across replicas.
 *
 * @see com.vyttah.goaml.security.ServiceCredentialValidator
 */
@Getter
@Entity
@Table(name = "consumed_assertion", schema = "public")
public class ConsumedAssertion {

    @Id
    @Column(name = "jti", nullable = false, length = 255)
    private String jti;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", nullable = false, length = 32)
    private SourceSystem sourceSystem;

    @Column(name = "consumed_at", nullable = false)
    private OffsetDateTime consumedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected ConsumedAssertion() {}

    public ConsumedAssertion(String jti, SourceSystem sourceSystem, OffsetDateTime expiresAt) {
        this.jti = jti;
        this.sourceSystem = sourceSystem;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        if (consumedAt == null) consumedAt = OffsetDateTime.now();
    }
}
