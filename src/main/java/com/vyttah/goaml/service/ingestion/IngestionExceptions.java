package com.vyttah.goaml.service.ingestion;

/**
 * Ingestion service exceptions (Phase 11). Mapped to HTTP status in {@code GlobalExceptionHandler}.
 */
public final class IngestionExceptions {

    private IngestionExceptions() {}

    /**
     * The whole upload is rejected before any report is created — the file is unreadable, has no/missing
     * required headers, or exceeds the row cap. (Per-row problems are recorded as {@code FAILED} results,
     * not thrown.) → {@code 400}.
     */
    public static class ImportRejectedException extends RuntimeException {
        public ImportRejectedException(String message) {
            super(message);
        }
    }
}
