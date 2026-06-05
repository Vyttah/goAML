package com.vyttah.goaml.model.dto.attachment;

import com.vyttah.goaml.model.entity.attachment.Attachment;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Web response DTO for a report attachment (metadata only — the bytes stay in S3). Keeps the controller
 * from returning the JPA entity directly.
 */
public record AttachmentView(UUID id, UUID reportId, String filename, String contentType,
                             long sizeBytes, OffsetDateTime createdAt) {

    public static AttachmentView from(Attachment a) {
        return new AttachmentView(a.getId(), a.getReportId(), a.getFilename(), a.getContentType(),
                a.getSizeBytes(), a.getCreatedAt());
    }
}
