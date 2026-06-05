package com.vyttah.goaml.integration.aws;

/**
 * Raised when a report attachment cannot be stored in, fetched from, or deleted from S3 — the object is
 * missing, the bucket is misconfigured, or the SDK call failed. Carries no object bytes in its message.
 */
public class S3AccessException extends RuntimeException {

    public S3AccessException(String message) {
        super(message);
    }

    public S3AccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
