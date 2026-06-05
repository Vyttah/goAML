package com.vyttah.goaml.b2b.error;

/**
 * The goAML B2B endpoint rejected our credentials / session (HTTP 401). Callers should re-authenticate
 * (refresh the cached token) and retry once before giving up.
 */
public class B2bAuthException extends RuntimeException {

    public B2bAuthException(String message) {
        super(message);
    }

    public B2bAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
