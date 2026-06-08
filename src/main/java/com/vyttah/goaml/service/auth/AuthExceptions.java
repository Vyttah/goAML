package com.vyttah.goaml.service.auth;

/**
 * Auth-service exceptions (Phase 1.5). Mapped to HTTP by {@code GlobalExceptionHandler}.
 */
public final class AuthExceptions {

    private AuthExceptions() {
    }

    /**
     * The requested authentication on-ramp is not enabled in this deployment's {@code goaml.auth.mode}
     * (e.g. native login on a {@code federated}-only deployment, or token-exchange on a {@code native} one).
     * Mapped to {@code 404 Not Found} — the endpoint is effectively absent in this mode.
     */
    public static class AuthModeDisabledException extends RuntimeException {
        public AuthModeDisabledException(String message) {
            super(message);
        }
    }
}
