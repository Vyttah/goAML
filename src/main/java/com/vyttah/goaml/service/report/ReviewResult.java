package com.vyttah.goaml.service.report;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The outcome of a review-stage transition (Phase D.2): the report id, its new status, and the reviewer +
 * remark recorded on the report (null reviewer/timestamp for {@code submitForReview}, which only moves the
 * report into the queue).
 */
public record ReviewResult(UUID reportId, String status, UUID reviewedBy,
                           OffsetDateTime reviewedAt, String remark) {
}
