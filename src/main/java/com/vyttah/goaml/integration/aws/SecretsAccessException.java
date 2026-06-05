package com.vyttah.goaml.integration.aws;

/**
 * Raised when a tenant's goAML credentials cannot be resolved from AWS Secrets Manager — the secret is
 * missing, unreadable, not valid JSON, or missing required fields. Carries no secret material in its message.
 */
public class SecretsAccessException extends RuntimeException {

    public SecretsAccessException(String message) {
        super(message);
    }

    public SecretsAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
