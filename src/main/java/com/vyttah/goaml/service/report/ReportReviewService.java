package com.vyttah.goaml.service.report;

import com.vyttah.goaml.model.entity.report.Report;

import java.util.List;
import java.util.UUID;

/**
 * Phase D.2 — the goAML report <strong>review gate</strong> (opt-in per tenant via
 * {@code tenant_goaml_config.review_required}). A VALID report must pass an MLRO review before it can be
 * submitted to the FIU:
 *
 * <pre>VALID --submitForReview--&gt; PENDING_REVIEW --approve--&gt; APPROVED --(MLRO submit)--&gt; SUBMITTED
 *                                        \--reject--&gt; VALID</pre>
 *
 * <p>Maker ≠ checker is enforced at the role boundary (create/submit-for-review = ANALYST or MLRO; approve/
 * reject = MLRO) and the submit gate ({@link com.vyttah.goaml.service.submission.SubmissionService}) requires
 * {@code APPROVED} when review is enabled. When the tenant has review disabled these operations are rejected
 * (the report stays on the direct VALID → SUBMITTED path).
 */
public interface ReportReviewService {

    /** {@code VALID → PENDING_REVIEW}. Requires the tenant's review gate to be enabled. */
    ReviewResult submitForReview(UUID reportId, UUID tenantId, UUID actorUserId, String remark);

    /** {@code PENDING_REVIEW → APPROVED}, recording the reviewer + remark. */
    ReviewResult approve(UUID reportId, UUID tenantId, UUID actorUserId, String remark);

    /** {@code PENDING_REVIEW → VALID}, recording the reviewer + remark (the maker can fix and resubmit). */
    ReviewResult reject(UUID reportId, UUID tenantId, UUID actorUserId, String remark);

    /** Reports currently awaiting review ({@code PENDING_REVIEW}) for the active tenant — the review queue. */
    List<Report> reviewQueue();
}
