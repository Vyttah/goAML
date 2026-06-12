package com.vyttah.goaml.service.report;

/**
 * Report-service exceptions, mapped to HTTP by the global handler (Phase 7.3):
 * not-found → 404, duplicate reference → 409, invalid review transition → 409,
 * client-metadata too large → 422.
 */
public final class ReportExceptions {

    private ReportExceptions() {}

    /**
     * The optional {@code clientMetadata} object on create exceeded the size cap (A3). The metadata is opaque
     * captured-not-filed context, so an oversized blob is a caller error → {@code 422} (we won't persist
     * unbounded JSON into the report row).
     */
    public static class ClientMetadataTooLargeException extends RuntimeException {
        public ClientMetadataTooLargeException(String message) {
            super(message);
        }
    }

    /**
     * A review-stage transition was requested that the report's current state (or the tenant's review config)
     * does not allow — e.g. approving a report that is not {@code PENDING_REVIEW}, or submitting a report for
     * review when the tenant has review disabled. → {@code 409}.
     */
    public static class InvalidReviewStateException extends RuntimeException {
        public InvalidReviewStateException(String message) {
            super(message);
        }
    }

    /**
     * A5 segregation-of-duties: the approver is the same user who authored the report. An MLRO must not
     * approve their own report — that collapses maker-checker into one person, defeating the review gate.
     * → {@code 409} (the report needs a different approver).
     */
    public static class SelfApprovalNotAllowedException extends RuntimeException {
        public SelfApprovalNotAllowedException(String message) {
            super(message);
        }
    }

    /** No report with the given id in the current tenant. */
    public static class ReportNotFoundException extends RuntimeException {
        public ReportNotFoundException(String message) {
            super(message);
        }
    }

    /** A report with the same {@code entity_reference} already exists for this tenant (idempotency key). */
    public static class DuplicateEntityReferenceException extends RuntimeException {
        public DuplicateEntityReferenceException(String message) {
            super(message);
        }
    }
}
