package com.vyttah.goaml.ingestion.reportability;

import java.util.List;

/**
 * The outcome of a {@link ReportabilityDetector} assessment: whether goAML considers a transaction
 * DPMS-reportable, with human-readable reasons (shown to clients and recorded on the auto-created draft).
 */
public record ReportabilityVerdict(boolean reportable, List<String> reasons) {
}
