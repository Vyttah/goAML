package com.vyttah.goaml.model.entity.report;

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
 * A stored goAML report for the current tenant. Tenant-scoped — no {@code @Table} schema, so it resolves via
 * the active tenant's {@code search_path} (see
 * {@link com.vyttah.goaml.config.tenant.SchemaMultiTenantConnectionProvider}).
 *
 * <p>Content is kept as the structured {@link #input} (JSONB), the marshalled goAML {@link #reportXml}
 * snapshot, and metadata — the XSD-generated model is the structure authority, so there is no normalized
 * report tree. The JSONB columns map as {@code String} via {@link JdbcTypeCode}; the service (de)serializes
 * them with Jackson. (This is the persistence aggregate — distinct from the JAXB
 * {@code com.vyttah.goaml.domain.generated.Report}.)
 */
@Getter
@Entity
@Table(name = "report")
public class Report {

    @Id
    private UUID id;

    @Column(name = "entity_reference", nullable = false, unique = true, length = 255)
    private String entityReference;

    @Column(name = "report_code", nullable = false, length = 16)
    private String reportCode;

    @Column(name = "rentity_id", nullable = false)
    private Integer rentityId;

    @Setter
    @Column(nullable = false, length = 16)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String input;

    @Setter
    @Column(name = "report_xml")
    private String reportXml;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_errors", columnDefinition = "jsonb")
    private String validationErrors;

    @Setter
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Setter
    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Setter
    @Column(name = "review_remark")
    private String reviewRemark;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Report() {}

    public Report(UUID id, String entityReference, String reportCode, Integer rentityId,
                  String status, String input, UUID createdBy) {
        this.id = id;
        this.entityReference = entityReference;
        this.reportCode = reportCode;
        this.rentityId = rentityId;
        this.status = status;
        this.input = input;
        this.createdBy = createdBy;
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
