package com.vyttah.goaml.model.dto.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.vyttah.goaml.b2b.ReportStatus;
import com.vyttah.goaml.engine.validation.ValidationMessage;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.service.report.ReportDetail;
import com.vyttah.goaml.service.report.ReportResult;
import com.vyttah.goaml.service.report.ReviewResult;
import com.vyttah.goaml.service.submission.SubmissionResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Web response DTOs for the report API (keeps controllers from returning entities/service types directly).
 */
public final class ReportResponses {

    private ReportResponses() {}

    /** Result of creating/validating a report. */
    public record CreateReportResponse(UUID reportId, String status, List<ValidationMessage> validationMessages) {
        public static CreateReportResponse from(ReportResult r) {
            return new CreateReportResponse(r.reportId(), r.status(), r.validationMessages());
        }
    }

    /** A report summary for list/get. */
    public record ReportView(UUID id, String entityReference, String reportCode, String status,
                             Integer rentityId, OffsetDateTime createdAt) {
        public static ReportView from(Report r) {
            return new ReportView(r.getId(), r.getEntityReference(), r.getReportCode(),
                    r.getStatus(), r.getRentityId(), r.getCreatedAt());
        }
    }

    /** Result of submitting a report to the FIU. */
    public record SubmissionView(UUID submissionId, String reportKey, String status) {
        public static SubmissionView from(SubmissionResult r) {
            return new SubmissionView(r.submissionId(), r.reportKey(), r.status());
        }
    }

    /** Latest FIU status for a report. */
    public record StatusView(String reportKey, String status, String errors) {
        public static StatusView from(ReportStatus s) {
            return new StatusView(s.reportKey(), s.status(), s.errors());
        }
    }

    /** Outcome of a review-stage transition (Phase D.2): the new status + the recorded reviewer/remark. */
    public record ReviewView(UUID reportId, String status, UUID reviewedBy,
                             OffsetDateTime reviewedAt, String remark) {
        public static ReviewView from(ReviewResult r) {
            return new ReviewView(r.reportId(), r.status(), r.reviewedBy(), r.reviewedAt(), r.remark());
        }
    }

    /**
     * Full read view of a report (Phase D.3): summary + the stored filing {@code input} (as a JSON tree the
     * SPA renders into typed sections), the persisted validation messages, the review trail, and whether the
     * marshalled XML is available.
     */
    public record ReportDetailView(UUID id, String entityReference, String reportCode, String status,
                                   Integer rentityId, OffsetDateTime createdAt, JsonNode input,
                                   List<ValidationMessage> validationMessages, UUID reviewedBy,
                                   OffsetDateTime reviewedAt, String reviewRemark, boolean hasXml,
                                   JsonNode clientMetadata) {
        public static ReportDetailView from(ReportDetail d) {
            return new ReportDetailView(d.id(), d.entityReference(), d.reportCode(), d.status(), d.rentityId(),
                    d.createdAt(), d.input(), d.validationMessages(), d.reviewedBy(), d.reviewedAt(),
                    d.reviewRemark(), d.hasXml(), d.clientMetadata());
        }
    }
}
