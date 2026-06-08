package com.vyttah.goaml.model.dto.reportability;

import java.math.BigDecimal;
import java.util.List;

/**
 * goAML's reportability verdict (Phase 1.5b): whether the transaction is DPMS-reportable, the reasons, and
 * the threshold applied (so the caller can show "X of AED 55,000").
 */
public record ReportabilityCheckResponse(
        boolean reportable,
        List<String> reasons,
        BigDecimal thresholdAed) {
}
