package com.vyttah.goaml.service.attachment;

import com.vyttah.goaml.model.entity.attachment.Attachment;

import java.util.List;
import java.util.UUID;

/**
 * Manages supporting-document attachments on a report for the current tenant. Bytes live in S3
 * (per-tenant/per-report key prefixes); this service owns the metadata + the upload/remove lifecycle.
 * Attachments are mutable only while the report is editable (DRAFT/VALID/INVALID); once submitted they
 * are frozen. The submit path ({@code SubmissionService}) reads the rows and pulls the bytes back into
 * the ZIP.
 */
public interface AttachmentService {

    /**
     * Validate (extension/size against {@code PackagingLimits.UAE_DEFAULT}), store the bytes in S3, and
     * persist an attachment row.
     *
     * @throws com.vyttah.goaml.service.report.ReportExceptions.ReportNotFoundException if the report is absent
     * @throws AttachmentExceptions.ReportNotEditableException                          if the report is not editable
     * @throws AttachmentExceptions.AttachmentRejectedException                         if the upload fails validation
     */
    Attachment add(UUID reportId, UUID tenantId, UUID actorUserId,
                   String filename, String contentType, byte[] bytes);

    /**
     * List a report's attachments (oldest first).
     *
     * @throws com.vyttah.goaml.service.report.ReportExceptions.ReportNotFoundException if the report is absent
     */
    List<Attachment> list(UUID reportId);

    /**
     * Fetch an attachment's bytes (from S3) plus the metadata needed to serve a download. Allowed in any
     * report status (a submitted/frozen report's evidence is still retrievable).
     *
     * @throws com.vyttah.goaml.service.report.ReportExceptions.ReportNotFoundException if the report is absent
     * @throws AttachmentExceptions.AttachmentNotFoundException                         if the attachment is absent
     */
    AttachmentDownload download(UUID reportId, UUID attachmentId);

    /** An attachment's bytes + the metadata a controller needs to set Content-Type / filename. */
    record AttachmentDownload(String filename, String contentType, byte[] bytes) {}

    /**
     * Delete an attachment (the S3 object then the row).
     *
     * @throws com.vyttah.goaml.service.report.ReportExceptions.ReportNotFoundException if the report is absent
     * @throws AttachmentExceptions.ReportNotEditableException                          if the report is not editable
     * @throws AttachmentExceptions.AttachmentNotFoundException                         if the attachment is absent
     */
    void remove(UUID reportId, UUID attachmentId, UUID actorUserId);
}
