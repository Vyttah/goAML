package com.vyttah.goaml.b2b.error;

/**
 * The goAML B2B endpoint rejected the report (HTTP 400). The fix is in the report itself, not the auth or
 * the transport — so this carries the FIU's error {@link #responseBody()} for surfacing to the user.
 */
public class B2bValidationException extends RuntimeException {

    private final String responseBody;

    public B2bValidationException(String message, String responseBody) {
        super(message);
        this.responseBody = responseBody;
    }

    public String responseBody() {
        return responseBody;
    }
}
