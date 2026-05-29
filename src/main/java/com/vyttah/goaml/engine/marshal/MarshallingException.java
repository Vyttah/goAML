package com.vyttah.goaml.engine.marshal;

/** Raised when a goAML report fails to (un)marshal to/from XML. */
public class MarshallingException extends RuntimeException {
    public MarshallingException(String message, Throwable cause) {
        super(message, cause);
    }
}
