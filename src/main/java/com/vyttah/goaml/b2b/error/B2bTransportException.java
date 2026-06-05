package com.vyttah.goaml.b2b.error;

/**
 * A transport-level failure talking to the goAML B2B endpoint — network error, timeout, or a non-2xx
 * response that isn't a 400 (validation) or 401 (auth). Typically transient → retry with backoff.
 */
public class B2bTransportException extends RuntimeException {

    public B2bTransportException(String message) {
        super(message);
    }

    public B2bTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
