package com.vyttah.goaml.service.submission;

/**
 * Submission-service exceptions, mapped to HTTP by the global handler (Phase 7.3 + 8.4):
 * not-submittable → 409, missing tenant config → 409, FIU-rejected → 422, packaging-too-large → 422,
 * transport/auth → 502.
 */
public final class SubmissionExceptions {

    private SubmissionExceptions() {}

    /** The report is not in a submittable state (must be {@code VALID}; e.g. already submitted or invalid). */
    public static class ReportNotSubmittableException extends RuntimeException {
        public ReportNotSubmittableException(String message) {
            super(message);
        }
    }

    /** The tenant has no {@code tenant_goaml_config} row, so we can't resolve its FIU endpoint/credentials. */
    public static class TenantConfigMissingException extends RuntimeException {
        public TenantConfigMissingException(String message) {
            super(message);
        }
    }

    /** The FIU rejected the report (HTTP 400) — carries the FIU's error body. The fix is in the report. */
    public static class SubmissionRejectedException extends RuntimeException {
        private final String responseBody;

        public SubmissionRejectedException(String message, String responseBody) {
            super(message);
            this.responseBody = responseBody;
        }

        public String responseBody() {
            return responseBody;
        }
    }

    /** Auth or transport failure talking to the FIU — typically transient; retry later. */
    public static class SubmissionTransportException extends RuntimeException {
        public SubmissionTransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * The report + its attachments exceed the FIU packaging limits (total ZIP size / count). The report
     * stays {@code VALID} — the fix is to remove attachments. Distinct from a per-file rejection at upload.
     */
    public static class SubmissionPackagingException extends RuntimeException {
        public SubmissionPackagingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
