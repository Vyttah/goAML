package com.vyttah.goaml.service.submission;

import com.vyttah.goaml.b2b.B2bAuthMode;
import com.vyttah.goaml.b2b.B2bTenantConfig;
import com.vyttah.goaml.b2b.GoamlB2bClient;
import com.vyttah.goaml.b2b.ReportStatus;
import com.vyttah.goaml.b2b.error.B2bAuthException;
import com.vyttah.goaml.b2b.error.B2bTransportException;
import com.vyttah.goaml.b2b.error.B2bValidationException;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.engine.packaging.Attachment;
import com.vyttah.goaml.engine.packaging.PackagingException;
import com.vyttah.goaml.engine.packaging.PackagingLimits;
import com.vyttah.goaml.engine.packaging.ReportZipPackager;
import com.vyttah.goaml.integration.aws.S3AccessException;
import com.vyttah.goaml.integration.aws.S3StorageClient;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.submission.Submission;
import com.vyttah.goaml.repository.attachment.AttachmentRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.repository.submission.SubmissionRepository;
import com.vyttah.goaml.service.audit.AuditService;
import com.vyttah.goaml.service.report.ReportExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Default {@link SubmissionService}. Guards the report is {@code VALID}, resolves the tenant's B2B config,
 * packages the stored XML, and submits via {@link GoamlB2bClient}. FIU outcomes map to the submission/report
 * status; a 400 rejection keeps the report editable, while auth/transport failures are transient.
 */
@RequiredArgsConstructor
@Service
public class DefaultSubmissionService implements SubmissionService {

    private final ReportRepository reportRepository;
    private final SubmissionRepository submissionRepository;
    private final TenantGoamlConfigRepository configRepository;
    private final AttachmentRepository attachmentRepository;
    private final GoamlB2bClient b2bClient;
    private final ReportZipPackager packager;
    private final S3StorageClient s3StorageClient;
    private final AuditService auditService;

    @Override
    public SubmissionResult submit(UUID reportId, UUID tenantId, UUID actorUserId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportExceptions.ReportNotFoundException("Report not found: " + reportId));
        if (!"VALID".equals(report.getStatus())) {
            throw new SubmissionExceptions.ReportNotSubmittableException(
                    "Report " + reportId + " is " + report.getStatus() + " — must be VALID to submit");
        }

        B2bTenantConfig cfg = b2bConfig(tenantId);
        List<Attachment> attachments = loadAttachments(reportId);
        byte[] zip;
        try {
            zip = packager.zip(report.getReportXml().getBytes(StandardCharsets.UTF_8),
                    report.getEntityReference() + ".xml", attachments, PackagingLimits.UAE_DEFAULT);
        } catch (PackagingException e) {
            throw new SubmissionExceptions.SubmissionPackagingException(
                    "Report " + reportId + " + attachments exceed packaging limits: " + e.getMessage(), e);
        }

        Submission submission = new Submission(UUID.randomUUID(), reportId, "SUBMITTED");
        try {
            String reportKey = b2bClient.postReport(cfg, zip, report.getEntityReference() + ".zip");
            submission.setReportkey(reportKey);
            submissionRepository.save(submission);

            report.setStatus("SUBMITTED");
            reportRepository.save(report);

            auditService.record("REPORT.SUBMIT", actorUserId, null, TenantContext.get(),
                    "report " + report.getEntityReference() + " submitted, reportkey=" + reportKey);
            return new SubmissionResult(submission.getId(), reportKey, "SUBMITTED");
        } catch (B2bValidationException e) {
            saveFailed(submission, e.responseBody());
            throw new SubmissionExceptions.SubmissionRejectedException(
                    "FIU rejected report " + reportId, e.responseBody());
        } catch (B2bAuthException | B2bTransportException e) {
            saveFailed(submission, e.getMessage());
            throw new SubmissionExceptions.SubmissionTransportException(
                    "Submission transport/auth failure for report " + reportId, e);
        }
    }

    @Override
    public ReportStatus refreshStatus(UUID reportId, UUID tenantId) {
        reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportExceptions.ReportNotFoundException("Report not found: " + reportId));

        Submission latest = submissionRepository.findByReportIdOrderBySubmittedAtDesc(reportId).stream()
                .filter(s -> s.getReportkey() != null)
                .findFirst()
                .orElseThrow(() -> new SubmissionExceptions.ReportNotSubmittableException(
                        "Report " + reportId + " has no submission to poll"));

        ReportStatus status = b2bClient.getReportStatus(b2bConfig(tenantId), latest.getReportkey());

        String mapped = mapStatus(status.status());
        latest.setStatus(mapped);
        latest.setErrors(status.errors());
        submissionRepository.save(latest);

        reportRepository.findById(reportId).ifPresent(r -> {
            r.setStatus(mapped);
            reportRepository.save(r);
        });
        return status;
    }

    /** Pull each stored attachment's bytes from S3 into a packaging {@link Attachment}. */
    private List<Attachment> loadAttachments(UUID reportId) {
        try {
            return attachmentRepository.findByReportIdOrderByCreatedAt(reportId).stream()
                    .map(a -> new Attachment(a.getFilename(), s3StorageClient.fetch(a.getS3Key()),
                            a.getContentType()))
                    .toList();
        } catch (S3AccessException e) {
            throw new SubmissionExceptions.SubmissionTransportException(
                    "Failed to load attachments from S3 for report " + reportId, e);
        }
    }

    private B2bTenantConfig b2bConfig(UUID tenantId) {
        TenantGoamlConfig config = configRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new SubmissionExceptions.TenantConfigMissingException(
                        "No tenant_goaml_config for tenant " + tenantId));
        return new B2bTenantConfig(tenantId.toString(), config.getBaseUrl(), config.getSecretsPath(),
                B2bAuthMode.valueOf(config.getAuthMode()));
    }

    private void saveFailed(Submission submission, String errors) {
        submission.setStatus("FAILED");
        submission.setErrors(errors);
        submissionRepository.save(submission);
    }

    /** Map a FIU status string to our report/submission status vocabulary. */
    private static String mapStatus(String fiuStatus) {
        if (fiuStatus == null) {
            return "SUBMITTED";
        }
        String s = fiuStatus.toLowerCase();
        if (s.contains("accept")) {
            return "ACCEPTED";
        }
        if (s.contains("reject")) {
            return "REJECTED";
        }
        return "SUBMITTED";
    }
}
