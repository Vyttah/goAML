package com.vyttah.goaml.controller.lookup;

/**
 * Lookup controller exceptions (Phase 13). Mapped to HTTP status in {@code GlobalExceptionHandler}.
 */
public final class LookupExceptions {

    private LookupExceptions() {}

    /** Unknown jurisdiction or lookup set. → {@code 404}. */
    public static class LookupNotFoundException extends RuntimeException {
        public LookupNotFoundException(String message) {
            super(message);
        }
    }
}
