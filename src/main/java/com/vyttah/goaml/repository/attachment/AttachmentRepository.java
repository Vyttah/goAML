package com.vyttah.goaml.repository.attachment;

import com.vyttah.goaml.model.entity.attachment.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for {@link Attachment}. Requires a bound tenant (see {@code ReportRepository}).
 */
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findByReportIdOrderByCreatedAt(UUID reportId);

    Optional<Attachment> findByIdAndReportId(UUID id, UUID reportId);

    /** B6 delete-guard: did this user upload any attachment in the current tenant? */
    boolean existsByUploadedBy(UUID uploadedBy);
}
