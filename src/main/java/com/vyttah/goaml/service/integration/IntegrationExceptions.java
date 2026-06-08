package com.vyttah.goaml.service.integration;

/**
 * Integration-push exceptions (Phase 1.5b/1.5c). Mapped to HTTP by {@code GlobalExceptionHandler}.
 */
public final class IntegrationExceptions {

    private IntegrationExceptions() {
    }

    /**
     * The source org reference (e.g. accounting {@code companyId}) is not mapped to any goAML tenant
     * ({@code tenant_external_ref}). A provisioning gap, not a malformed request → {@code 404}.
     */
    public static class UnmappedOrgException extends RuntimeException {
        public UnmappedOrgException(String message) {
            super(message);
        }
    }

    /**
     * No screened subject exists for the requested screening company + customer uid (Phase 1.5c). → {@code 404}.
     */
    public static class ScreenedSubjectNotFoundException extends RuntimeException {
        public ScreenedSubjectNotFoundException(String message) {
            super(message);
        }
    }
}
