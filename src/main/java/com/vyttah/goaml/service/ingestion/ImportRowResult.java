package com.vyttah.goaml.service.ingestion;

import java.util.List;
import java.util.UUID;

/**
 * The outcome of importing a single row/report within an {@code import_job} (Phase 11). Serialized into the
 * job's {@code results} JSONB array.
 *
 * @param row             1-based position in the source (1 for a single XML file; the CSV data-row number)
 * @param entityReference the report's {@code entity_reference} (may be null/blank if the row couldn't be read)
 * @param status          {@code VALID}/{@code INVALID} when a report was created (mirrors the report status),
 *                        or {@code FAILED} when no report was created (unparseable, missing ref, duplicate)
 * @param reportId        the created report's id, or null when {@code FAILED}
 * @param errors          per-row messages: validation messages for {@code INVALID}, the reason for {@code FAILED}
 */
public record ImportRowResult(int row, String entityReference, String status, UUID reportId,
                              List<String> errors) {

    public static final String FAILED = "FAILED";

    /** A report was created (persisted) with the given validation {@code status} ({@code VALID}/{@code INVALID}). */
    public static ImportRowResult created(int row, String entityReference, String status, UUID reportId,
                                          List<String> messages) {
        return new ImportRowResult(row, entityReference, status, reportId, messages);
    }

    /** No report was created — the row was rejected for the given reason. */
    public static ImportRowResult failed(int row, String entityReference, String error) {
        return new ImportRowResult(row, entityReference, FAILED, null, List.of(error));
    }

    /** True when a report row was persisted (VALID or INVALID), i.e. not {@code FAILED}. */
    public boolean reportCreated() {
        return reportId != null;
    }
}
