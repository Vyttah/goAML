package com.vyttah.goaml.service.report.export;

import java.util.List;
import java.util.UUID;

/**
 * Bundles multiple reports' filed PDFs or persisted goAML XML into a single ZIP, for the "Approve
 * Transaction" multi-select bulk download. Both entry points reject the whole batch (400) if any selected
 * report is {@code INVALID} or absent from the current tenant.
 */
public interface ReportBulkExportService {

    /** A generated ZIP plus the download filename to stamp on the response. */
    record ZipExport(String filename, byte[] bytes) {}

    /** One PDF per report (same house style as {@code /reports/{id}/report.pdf}), zipped together. */
    ZipExport pdfZip(List<UUID> reportIds, UUID tenantId, String userLabel);

    /** One goAML XML per report (the exact persisted filing payload), zipped together. */
    ZipExport xmlZip(List<UUID> reportIds);
}
