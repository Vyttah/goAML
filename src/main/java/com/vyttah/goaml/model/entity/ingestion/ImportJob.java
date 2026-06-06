package com.vyttah.goaml.model.entity.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A per-tenant report-import job: one row per uploaded file (goAML XML or DPMSR CSV). Tenant-scoped — no
 * {@code @Table} schema, so it resolves via the active tenant's {@code search_path} (like
 * {@link com.vyttah.goaml.model.entity.report.Report}).
 *
 * <p>Processing is synchronous, so the row is written once with its final {@link #status} + tallies + the
 * per-row {@link #results} JSONB array. Following the {@code Report} convention, the JSONB column maps as a
 * {@code String}; the service (de)serializes it with Jackson (to a {@code List<ImportRowResult>}).
 */
@Getter
@Entity
@Table(name = "import_job")
public class ImportJob {

    @Id
    private UUID id;

    @Column(name = "source_type", nullable = false, length = 16)
    private String sourceType;

    @Column(length = 255)
    private String filename;

    @Setter
    @Column(nullable = false, length = 16)
    private String status;

    @Setter
    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Setter
    private int succeeded;

    @Setter
    private int failed;

    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String results;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ImportJob() {}

    public ImportJob(UUID id, String sourceType, String filename, UUID createdBy) {
        this.id = id;
        this.sourceType = sourceType;
        this.filename = filename;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
