package com.vyttah.goaml.service.admin;

/**
 * Admin service exceptions (Phase 13.2). Mapped to HTTP status in {@code GlobalExceptionHandler}.
 */
public final class AdminExceptions {

    private AdminExceptions() {}

    /** A user already exists with the given email (globally unique). → {@code 409}. */
    public static class UserEmailExistsException extends RuntimeException {
        public UserEmailExistsException(String message) {
            super(message);
        }
    }

    /** The caller's tenant has no goAML config yet (GET before it's set). → {@code 404}. */
    public static class GoamlConfigNotFoundException extends RuntimeException {
        public GoamlConfigNotFoundException(String message) {
            super(message);
        }
    }

    /** No goAML reporting person with that id in the caller's tenant. → {@code 404}. */
    public static class GoamlPersonNotFoundException extends RuntimeException {
        public GoamlPersonNotFoundException(String message) {
            super(message);
        }
    }

    /** No user with that id in the caller's tenant. → {@code 404}. */
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }

    /** Hard-delete blocked because the user is referenced by reports (author/reviewer) — disable instead.
     *  → {@code 409}. */
    public static class UserReferencedException extends RuntimeException {
        public UserReferencedException(String message) {
            super(message);
        }
    }

    /** No trusted service with that id. → {@code 404}. */
    public static class TrustedServiceNotFoundException extends RuntimeException {
        public TrustedServiceNotFoundException(String message) {
            super(message);
        }
    }

    /** A trusted service is already registered for that source system (unique). → {@code 409}. */
    public static class TrustedServiceExistsException extends RuntimeException {
        public TrustedServiceExistsException(String message) {
            super(message);
        }
    }

    /** No company link (tenant_external_ref) with that id. → {@code 404}. */
    public static class TenantExternalRefNotFoundException extends RuntimeException {
        public TenantExternalRefNotFoundException(String message) {
            super(message);
        }
    }

    /** A company link already exists for that (source system, org ref). → {@code 409}. */
    public static class TenantExternalRefExistsException extends RuntimeException {
        public TenantExternalRefExistsException(String message) {
            super(message);
        }
    }

    /** The tenant referenced by a company link does not exist. → {@code 404}. */
    public static class TenantNotFoundException extends RuntimeException {
        public TenantNotFoundException(String message) {
            super(message);
        }
    }
}
