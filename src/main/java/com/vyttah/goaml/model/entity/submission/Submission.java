package com.vyttah.goaml.model.entity.submission;

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
 * One submission attempt of a {@link com.vyttah.goaml.model.entity.report.Report} to the FIU. Tenant-scoped
 * (no {@code @Table} schema). {@link #reportkey} is the FIU's handle, used to poll status (the async poller
 * is Phase 9; Phase 7 refreshes on demand).
 */
@Getter
@Entity
@Table(name = "submission")
public class Submission {

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Setter
    @Column(length = 128)
    private String reportkey;

    @Setter
    @Column(nullable = false, length = 16)
    private String status;

    @Setter
    @Column
    private String errors;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Submission() {}

    public Submission(UUID id, UUID reportId, String status) {
        this.id = id;
        this.reportId = reportId;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (submittedAt == null) submittedAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
