package com.vyttah.goaml.service.report;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Default {@link ReportReviewService}. Drives the per-tenant review gate by transitioning
 * {@link Report#getStatus()} and recording the reviewer + remark; the submit gate
 * ({@link com.vyttah.goaml.service.submission.SubmissionService}) reads the resulting status. Audit is recorded
 * for every transition (the audit service manages its own tenant context).
 */
@RequiredArgsConstructor
@Service
public class DefaultReportReviewService implements ReportReviewService {

    static final String VALID = "VALID";
    static final String PENDING_REVIEW = "PENDING_REVIEW";
    static final String APPROVED = "APPROVED";

    private final ReportRepository reportRepository;
    private final TenantGoamlConfigRepository configRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public ReviewResult submitForReview(UUID reportId, UUID tenantId, UUID actorUserId, String remark) {
        if (!reviewRequired(tenantId)) {
            throw new ReportExceptions.InvalidReviewStateException(
                    "Review is not enabled for this tenant — report " + reportId + " submits directly when VALID");
        }
        Report report = require(reportId);
        if (!VALID.equals(report.getStatus())) {
            throw new ReportExceptions.InvalidReviewStateException(
                    "Report " + reportId + " is " + report.getStatus() + " — must be VALID to submit for review");
        }
        report.setStatus(PENDING_REVIEW);
        report.setReviewRemark(remark);
        report.setReviewedBy(null);
        report.setReviewedAt(null);
        reportRepository.save(report);

        audit("REPORT.REVIEW_SUBMIT", actorUserId, report, "submitted for review");
        return result(report);
    }

    @Override
    @Transactional
    public ReviewResult approve(UUID reportId, UUID tenantId, UUID actorUserId, String remark) {
        Report report = requirePendingReview(reportId);
        // A5 segregation-of-duties: the approver must differ from the author. One person creating AND
        // approving collapses maker-checker — the whole point of the review gate.
        if (actorUserId != null && actorUserId.equals(report.getCreatedBy())) {
            throw new ReportExceptions.SelfApprovalNotAllowedException(
                    "Report " + reportId + " cannot be approved by its author — a different reviewer must approve");
        }
        report.setStatus(APPROVED);
        report.setReviewedBy(actorUserId);
        report.setReviewedAt(OffsetDateTime.now());
        report.setReviewRemark(remark);
        reportRepository.save(report);

        audit("REPORT.REVIEW_APPROVE", actorUserId, report, "approved");
        return result(report);
    }

    @Override
    @Transactional
    public ReviewResult reject(UUID reportId, UUID tenantId, UUID actorUserId, String remark) {
        if (remark == null || remark.isBlank()) {
            throw new IllegalArgumentException("A rejection remark is required");
        }
        Report report = requirePendingReview(reportId);
        report.setStatus(VALID);
        report.setReviewedBy(actorUserId);
        report.setReviewedAt(OffsetDateTime.now());
        report.setReviewRemark(remark);
        reportRepository.save(report);

        audit("REPORT.REVIEW_REJECT", actorUserId, report, "rejected: " + remark);
        return result(report);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Report> reviewQueue() {
        return reportRepository.findByStatus(PENDING_REVIEW);
    }

    private Report requirePendingReview(UUID reportId) {
        Report report = require(reportId);
        if (!PENDING_REVIEW.equals(report.getStatus())) {
            throw new ReportExceptions.InvalidReviewStateException(
                    "Report " + reportId + " is " + report.getStatus() + " — only a PENDING_REVIEW report can be "
                            + "approved or rejected");
        }
        return report;
    }

    private Report require(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportExceptions.ReportNotFoundException("Report not found: " + reportId));
    }

    private boolean reviewRequired(UUID tenantId) {
        return configRepository.findByTenantId(tenantId)
                .map(TenantGoamlConfig::isReviewRequired)
                .orElse(false);
    }

    private void audit(String action, UUID actorUserId, Report report, String detail) {
        auditService.record(action, actorUserId, null, TenantContext.get(),
                "report " + report.getEntityReference() + " " + detail);
    }

    private static ReviewResult result(Report report) {
        return new ReviewResult(report.getId(), report.getStatus(), report.getReviewedBy(),
                report.getReviewedAt(), report.getReviewRemark());
    }
}
