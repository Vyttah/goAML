package com.vyttah.goaml.service.report;

/**
 * Report-service exceptions, mapped to HTTP by the global handler (Phase 7.3):
 * not-found → 404, duplicate reference → 409, invalid review transition → 409.
 */
public final class ReportExceptions {

    private ReportExceptions() {}

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
