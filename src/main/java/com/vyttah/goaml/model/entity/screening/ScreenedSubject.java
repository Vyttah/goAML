package com.vyttah.goaml.model.entity.screening;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A reusable goAML view of a customer the AML screening software pushed (Phase 1.5c). Tenant-scoped (no
 * {@code @Table} schema) — resolves via the active tenant's {@code search_path}, exactly like
 * {@link com.vyttah.goaml.model.entity.notification.Notification}.
 *
 * <p>The already-resolved {@code ScreeningPartyPayload} is stored verbatim as {@link #payloadJson} (JSONB,
 * mapped as {@code String} via {@link JdbcTypeCode}; the service (de)serializes), so a DPMSR draft can be
 * seeded from it later by re-running {@code ScreeningPartyMapper}. {@link #externalRef}
 * ({@code SCR-<companyId>-<customerUid>}) is unique → the screening push is idempotent (upsert).
 */
@Getter
@Entity
@Table(name = "screened_subject")
public class ScreenedSubject {

    @Id
    private UUID id;

    @Column(name = "external_ref", nullable = false, unique = true, length = 255)
    private String externalRef;

    @Setter
    @Column(name = "subject_type", nullable = false, length = 16)
    private String subjectType;

    @Setter
    @Column(name = "display_name", nullable = false, length = 512)
    private String displayName;

    @Setter
    @Column(name = "risk_flag", nullable = false)
    private boolean riskFlag;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ScreenedSubject() {}

    public ScreenedSubject(UUID id, String externalRef, String subjectType, String displayName,
                           boolean riskFlag, String payloadJson) {
        this.id = id;
        this.externalRef = externalRef;
        this.subjectType = subjectType;
        this.displayName = displayName;
        this.riskFlag = riskFlag;
        this.payloadJson = payloadJson;
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
