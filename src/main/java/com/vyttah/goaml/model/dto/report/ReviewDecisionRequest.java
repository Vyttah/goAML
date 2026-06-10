package com.vyttah.goaml.model.dto.report;

/**
 * Optional body for a review-stage action (Phase D.2): a free-text remark. Optional for submit-for-review and
 * approve; required (non-blank) for reject — enforced in the service. The whole body may be omitted.
 */
public record ReviewDecisionRequest(String remark) {
}
