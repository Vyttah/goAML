package com.vyttah.goaml.model.entity.attachment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A supporting-document attachment on a {@link com.vyttah.goaml.model.entity.report.Report}. Tenant-scoped
 * (no {@code @Table} schema) — resolves via the active tenant's {@code search_path}.
 *
 * <p>Only metadata + the {@link #s3Key S3 object key} are stored here; the bytes live in S3
 * ({@code goaml.aws.s3.bucket}). At submit time the bytes are pulled back via
 * {@link com.vyttah.goaml.integration.aws.S3StorageClient} into the submission ZIP.
 *
 * <p><b>Name-clash note:</b> this JPA aggregate is distinct from the engine value record
 * {@code com.vyttah.goaml.engine.packaging.Attachment} (which carries the actual bytes for packaging).
 */
@Getter
@Entity
@Table(name = "attachment")
public class Attachment {

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "s3_key", nullable = false, length = 1024)
    private String s3Key;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Attachment() {}

    public Attachment(UUID id, UUID reportId, String filename, String contentType,
                      long sizeBytes, String s3Key, UUID uploadedBy) {
        this.id = id;
        this.reportId = reportId;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.s3Key = s3Key;
        this.uploadedBy = uploadedBy;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
