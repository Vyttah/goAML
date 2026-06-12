package com.vyttah.goaml.service.attachment;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.engine.packaging.PackagingLimits;
import com.vyttah.goaml.integration.aws.S3StorageClient;
import com.vyttah.goaml.model.entity.attachment.Attachment;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.repository.attachment.AttachmentRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.service.audit.AuditService;
import com.vyttah.goaml.service.report.ReportExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Default {@link AttachmentService}. Validates uploads against {@link PackagingLimits#UAE_DEFAULT} up front
 * (fail-fast — the packager re-enforces count + total at submit), stores bytes in S3 <em>before</em>
 * persisting the row (so a failure leaves at worst a harmless orphan object, never a dangling row), and
 * gates all mutations on the report being editable.
 */
@RequiredArgsConstructor
@Service
public class DefaultAttachmentService implements AttachmentService {

    /** Statuses in which a report's attachments may still be changed. */
    private static final Set<String> EDITABLE_STATUSES = Set.of("DRAFT", "VALID", "INVALID");
    private static final PackagingLimits LIMITS = PackagingLimits.UAE_DEFAULT;

    private final ReportRepository reportRepository;
    private final AttachmentRepository attachmentRepository;
    private final S3StorageClient s3StorageClient;
    private final AuditService auditService;
    private final AttachmentScanner attachmentScanner;

    @Override
    public Attachment add(UUID reportId, UUID tenantId, UUID actorUserId,
                          String filename, String contentType, byte[] bytes) {
        Report report = requireReport(reportId);
        requireEditable(report);
        validate(filename, bytes);
        // B15: sniff magic bytes (reject executables + declared-vs-actual mismatch), then run the pluggable
        // AV scanner (no-op unless goaml.attachments.av.enabled=true). Both run BEFORE the bytes are stored,
        // so a renamed binary never reaches S3 or the FIU submission ZIP.
        AttachmentContentInspector.inspect(filename, contentType, bytes);
        attachmentScanner.scan(filename, contentType, bytes);

        UUID attachmentId = UUID.randomUUID();
        String key = s3Key(tenantId, reportId, attachmentId, filename);
        s3StorageClient.put(key, bytes, contentType);

        Attachment attachment = new Attachment(attachmentId, reportId, filename, contentType,
                bytes.length, key, actorUserId);
        attachmentRepository.save(attachment);

        auditService.record("ATTACHMENT.ADD", actorUserId, null, TenantContext.get(),
                "attachment " + filename + " (" + bytes.length + " bytes) added to report " + reportId);
        return attachment;
    }

    @Override
    public List<Attachment> list(UUID reportId) {
        requireReport(reportId);
        return attachmentRepository.findByReportIdOrderByCreatedAt(reportId);
    }

    @Override
    public AttachmentDownload download(UUID reportId, UUID attachmentId) {
        requireReport(reportId);
        Attachment attachment = attachmentRepository.findByIdAndReportId(attachmentId, reportId)
                .orElseThrow(() -> new AttachmentExceptions.AttachmentNotFoundException(
                        "Attachment " + attachmentId + " not found on report " + reportId));
        byte[] bytes = s3StorageClient.fetch(attachment.getS3Key());
        return new AttachmentDownload(attachment.getFilename(), attachment.getContentType(), bytes);
    }

    @Override
    public void remove(UUID reportId, UUID attachmentId, UUID actorUserId) {
        Report report = requireReport(reportId);
        requireEditable(report);

        Attachment attachment = attachmentRepository.findByIdAndReportId(attachmentId, reportId)
                .orElseThrow(() -> new AttachmentExceptions.AttachmentNotFoundException(
                        "Attachment " + attachmentId + " not found on report " + reportId));

        s3StorageClient.delete(attachment.getS3Key());
        attachmentRepository.delete(attachment);

        auditService.record("ATTACHMENT.REMOVE", actorUserId, null, TenantContext.get(),
                "attachment " + attachment.getFilename() + " removed from report " + reportId);
    }

    private Report requireReport(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportExceptions.ReportNotFoundException("Report not found: " + reportId));
    }

    private static void requireEditable(Report report) {
        if (!EDITABLE_STATUSES.contains(report.getStatus())) {
            throw new AttachmentExceptions.ReportNotEditableException(
                    "Report " + report.getId() + " is " + report.getStatus()
                            + " — attachments are frozen once submitted");
        }
    }

    /** Mirror the packager's per-file rules so we reject early instead of at submit time. */
    private static void validate(String filename, byte[] bytes) {
        if (filename == null || filename.isBlank()) {
            throw new AttachmentExceptions.AttachmentRejectedException("Attachment filename is blank");
        }
        if (bytes == null || bytes.length == 0) {
            throw new AttachmentExceptions.AttachmentRejectedException("Attachment " + filename + " is empty");
        }
        if (LIMITS.maxAttachmentBytes() > 0 && bytes.length > LIMITS.maxAttachmentBytes()) {
            throw new AttachmentExceptions.AttachmentRejectedException("Attachment " + filename + " size "
                    + bytes.length + " exceeds max " + LIMITS.maxAttachmentBytes());
        }
        String ext = extensionOf(filename);
        if (!LIMITS.allowedExtensions().isEmpty() && !LIMITS.allowedExtensions().contains(ext)) {
            throw new AttachmentExceptions.AttachmentRejectedException("Attachment " + filename
                    + " has disallowed extension '" + ext + "'");
        }
    }

    private static String s3Key(UUID tenantId, UUID reportId, UUID attachmentId, String filename) {
        String safeName = filename.replaceAll("[/\\\\]", "_");
        return "tenants/" + tenantId + "/reports/" + reportId + "/" + attachmentId + "-" + safeName;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1
                ? ""
                : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
