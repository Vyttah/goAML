package com.vyttah.goaml.engine.packaging;

/** Raised when ZIP packaging fails or violates configured {@link PackagingLimits}. */
public class PackagingException extends RuntimeException {
    public PackagingException(String message) { super(message); }
    public PackagingException(String message, Throwable cause) { super(message, cause); }
}
