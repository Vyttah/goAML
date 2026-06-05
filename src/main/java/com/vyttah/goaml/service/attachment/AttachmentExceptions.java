package com.vyttah.goaml.service.attachment;

/**
 * Attachment-service exceptions, mapped to HTTP by the global handler (Phase 8.4):
 * not-found → 404, report-not-editable → 409, rejected (bad type/size) → 400.
 */
public final class AttachmentExceptions {

    private AttachmentExceptions() {}

    /** No attachment with the given id on the given report in the current tenant. */
    public static class AttachmentNotFoundException extends RuntimeException {
        public AttachmentNotFoundException(String message) {
            super(message);
        }
    }

    /** The report is not in an editable state — attachments are frozen once it is submitted/decided. */
    public static class ReportNotEditableException extends RuntimeException {
        public ReportNotEditableException(String message) {
            super(message);
        }
    }

    /** The upload fails validation (blank/empty, oversize, or a disallowed extension). */
    public static class AttachmentRejectedException extends RuntimeException {
        public AttachmentRejectedException(String message) {
            super(message);
        }
    }
}
