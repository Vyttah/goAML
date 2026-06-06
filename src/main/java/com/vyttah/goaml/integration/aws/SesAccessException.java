package com.vyttah.goaml.integration.aws;

/**
 * Raised when an email cannot be sent via SES — the sender address is unconfigured, the recipient is
 * blank, or the SDK send call failed. Notifications treat this as best-effort: the in-app row is the
 * durable record, so callers log and swallow this rather than failing the originating action.
 */
public class SesAccessException extends RuntimeException {

    public SesAccessException(String message) {
        super(message);
    }

    public SesAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
