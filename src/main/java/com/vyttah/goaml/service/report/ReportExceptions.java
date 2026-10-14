package com.vyttah.goaml.service.report;

/**
 * Report-service exceptions, mapped to HTTP by the global handler (Phase 7.3):
 * not-found → 404, duplicate reference → 409, client-metadata too large → 422.
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
