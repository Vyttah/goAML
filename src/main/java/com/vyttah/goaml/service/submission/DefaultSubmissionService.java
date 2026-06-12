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
import com.vyttah.goaml.engine.validation.ValidationResult;
import com.vyttah.goaml.engine.validation.XsdSchemaValidator;
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
import com.vyttah.goaml.service.notification.NotificationService;
import com.vyttah.goaml.service.report.ReportExceptions;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(DefaultSubmissionService.class);

    private final ReportRepository reportRepository;
    private final SubmissionRepository submissionRepository;
    private final TenantGoamlConfigRepository configRepository;
    private final AttachmentRepository attachmentRepository;
    private final GoamlB2bClient b2bClient;
    private final ReportZipPackager packager;
    private final S3StorageClient s3StorageClient;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final XsdSchemaValidator xsdSchemaValidator;

    @Override
    public SubmissionResult submit(UUID reportId, UUID tenantId, UUID actorUserId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportExceptions.ReportNotFoundException("Report not found: " + reportId));
        // Phase D.2: when the tenant's review gate is on, the report must be APPROVED (VALID → PENDING_REVIEW →
        // APPROVED); otherwise the direct path applies and VALID is submittable.
        String required = reviewRequired(tenantId) ? "APPROVED" : "VALID";
        if (!required.equals(report.getStatus())) {
            throw new SubmissionExceptions.ReportNotSubmittableException(
                    "Report " + reportId + " is " + report.getStatus() + " — must be " + required + " to submit");
        }

        // Atomic submit-claim (CAS) BEFORE any packaging or FIU traffic: only one caller can flip
        // required → SUBMITTING; a concurrent second submit sees update-count 0 and gets a 409 instead of
        // sending a duplicate report to the FIU. Every failure path below restores the claimed status.
        if (reportRepository.claimForSubmission(reportId, required) == 0) {
            throw new SubmissionExceptions.ReportNotSubmittableException(
                    "Report " + reportId + " is already being submitted (concurrent submit) or its status moved");
        }

        try {
            return doSubmit(report, required, reportId, tenantId, actorUserId);
        } catch (RuntimeException e) {
            // Restore the pre-claim status so the report stays actionable (editable / re-submittable).
            // SUBMITTED is only persisted on success inside doSubmit, so any exception means not submitted.
            restoreStatus(report, required);
            throw e;
        }
    }

    private SubmissionResult doSubmit(Report report, String required, UUID reportId, UUID tenantId,
                                      UUID actorUserId) {
        // C9 defense-in-depth: re-validate the persisted XML against the XSD before it can leave for the FIU.
        // The XSD gate already runs at every write of report_xml, so this is normally a no-op — but it means
        // any future code path that wrote bad XML can never reach the regulator. A missing XML body is the
        // same conflict (handled inside revalidateStoredXml), never an NPE.
        String storedXml = report.getReportXml();
        byte[] xmlBytes = storedXml == null ? null : storedXml.getBytes(StandardCharsets.UTF_8);
        revalidateStoredXml(reportId, xmlBytes);

        B2bTenantConfig cfg = b2bConfig(tenantId);
        List<Attachment> attachments = loadAttachments(reportId);
        byte[] zip;
        try {
            zip = packager.zip(xmlBytes,
                    report.getEntityReference() + ".xml", attachments, PackagingLimits.UAE_DEFAULT);
        } catch (PackagingException e) {
            throw new SubmissionExceptions.SubmissionPackagingException(
                    "Report " + reportId + " + attachments exceed packaging limits: " + e.getMessage(), e);
        }

        Submission submission = new Submission(UUID.randomUUID(), reportId, "SUBMITTED");
        submission.setSubmittedBy(actorUserId);
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
            safeNotify(report, "REJECTED", tenantId);
            throw new SubmissionExceptions.SubmissionRejectedException(
                    "FIU rejected report " + reportId, e.responseBody());
        } catch (B2bAuthException | B2bTransportException e) {
            saveFailed(submission, e.getMessage());
            safeNotify(report, "FAILED", tenantId);
            throw new SubmissionExceptions.SubmissionTransportException(
                    "Submission transport/auth failure for report " + reportId, e);
        }
    }

    /** Undo the SUBMITTING claim after a failed submit — best-effort, never masks the original failure. */
    private void restoreStatus(Report report, String status) {
        try {
            report.setStatus(status);
            reportRepository.save(report);
        } catch (RuntimeException e) {
            log.error("Failed to restore report {} to {} after a failed submit (left SUBMITTING): {}",
                    report.getId(), status, e.getMessage());
        }
    }

    @Override
    public ReportStatus refreshStatus(UUID reportId, UUID tenantId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportExceptions.ReportNotFoundException("Report not found: " + reportId));
        String oldStatus = report.getStatus();

        Submission latest = submissionRepository.findByReportIdOrderBySubmittedAtDesc(reportId).stream()
                .filter(s -> s.getReportkey() != null)
                .findFirst()
                .orElseThrow(() -> new SubmissionExceptions.ReportNotSubmittableException(
                        "Report " + reportId + " has no submission to poll"));

        ReportStatus status = b2bClient.getReportStatus(b2bConfig(tenantId), latest.getReportkey());

        String mapped = mapStatus(status.status());

        // Terminal-state guard: a report the FIU already ACCEPTED/REJECTED must never be knocked back to
        // SUBMITTED by an unknown/in-flight FIU string on a later poll — keep the terminal outcome.
        if (isTerminal(oldStatus) && "SUBMITTED".equals(mapped)) {
            log.warn("Report {} is already {} — ignoring non-terminal FIU status '{}'",
                    reportId, oldStatus, status.status());
            return status;
        }

        latest.setStatus(mapped);
        latest.setErrors(status.errors());
        submissionRepository.save(latest);

        report.setStatus(mapped);
        reportRepository.save(report);

        // Notify only on a genuine transition to a terminal FIU outcome (no-op poll → no notification).
        if (!mapped.equals(oldStatus)) {
            safeNotify(report, mapped, tenantId);
        }
        return status;
    }

    /** True for the FIU-outcome end states a later poll must never regress. */
    private static boolean isTerminal(String reportStatus) {
        return "ACCEPTED".equals(reportStatus) || "REJECTED".equals(reportStatus);
    }

    @Override
    public String postMessage(String message, UUID tenantId, UUID actorUserId) {
        B2bTenantConfig cfg = b2bConfig(tenantId);
        try {
            String response = b2bClient.postMessage(cfg, message);
            // Record the action but not the message body (it may carry sensitive case detail).
            auditService.record("FIU.MESSAGE", actorUserId, null, TenantContext.get(),
                    "posted FIU MessageBoard message (" + message.length() + " chars)");
            return response;
        } catch (B2bAuthException | B2bTransportException e) {
            throw new SubmissionExceptions.SubmissionTransportException(
                    "Failed to post FIU message for tenant " + tenantId, e);
        }
    }

    /**
     * Fan a transition out to notifications — best-effort. A notification/email failure must never roll
     * back the (already-persisted) status change or abort the caller (a poll cycle or a request), so any
     * error is logged and swallowed. Mirrors the poller's "never throw out" discipline.
     */
    private void safeNotify(Report report, String status, UUID tenantId) {
        try {
            notificationService.notifyReportTransition(report, status, tenantId);
        } catch (RuntimeException e) {
            log.warn("Notification for report {} ({}) failed: {}",
                    report.getEntityReference(), status, e.getMessage());
        }
    }

    /**
     * C9: re-run the XSD validator on the persisted report XML; refuse to submit if it no longer conforms.
     * Cheap (schema compiled once) and a pure read — never mutates the report.
     */
    private void revalidateStoredXml(UUID reportId, byte[] xmlBytes) {
        if (xmlBytes == null || xmlBytes.length == 0) {
            throw new SubmissionExceptions.StoredXmlInvalidException(
                    "Report " + reportId + " has no stored XML to submit");
        }
        ValidationResult result = xsdSchemaValidator.validate(xmlBytes);
        if (!result.isValid()) {
            throw new SubmissionExceptions.StoredXmlInvalidException(
                    "Report " + reportId + " stored XML failed XSD re-validation at submit: " + result.errors());
        }
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

    /** Phase D.2: whether this tenant requires the MLRO review gate before a report can be submitted. */
    private boolean reviewRequired(UUID tenantId) {
        return configRepository.findByTenantId(tenantId)
                .map(TenantGoamlConfig::isReviewRequired)
                .orElse(false);
    }

    private void saveFailed(Submission submission, String errors) {
        submission.setStatus("FAILED");
        submission.setErrors(errors);
        submissionRepository.save(submission);
    }

    /**
     * Map a FIU status string to our report/submission status vocabulary — an exact whitelist of the known
     * goAML Web statuses, never substring matching (the old {@code contains("accept")} mapped
     * "Not Accepted" to ACCEPTED). Unknown strings keep the report SUBMITTED (so polling continues) and are
     * logged at WARN for triage.
     */
    private static String mapStatus(String fiuStatus) {
        if (fiuStatus == null) {
            return "SUBMITTED";
        }
        return switch (fiuStatus.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "accepted" -> "ACCEPTED";
            case "rejected" -> "REJECTED";
            // Known in-flight goAML Web pipeline states — still awaiting the FIU outcome.
            case "uploaded", "processing", "processed", "validated", "submitted", "under review",
                 "transferred to fiu" -> "SUBMITTED";
            default -> {
                log.warn("Unknown FIU report status '{}' — keeping SUBMITTED", fiuStatus);
                yield "SUBMITTED";
            }
        };
    }
}
