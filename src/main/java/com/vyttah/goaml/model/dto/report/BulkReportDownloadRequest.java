package com.vyttah.goaml.model.dto.report;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Body for the bulk PDF/XML download endpoints — the "Approve Transaction" multi-select (AML cockpit) picks
 * a set of reports and downloads them together as one ZIP.
 */
public record BulkReportDownloadRequest(@NotEmpty List<UUID> ids) {
}
