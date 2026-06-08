package com.vyttah.goaml.model.dto.reportability;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request to goAML's authoritative reportability check (Phase 1.5b). Lets an embedded client (accounting /
 * AML software) ask "is this DPMS-reportable?" before building or submitting a report.
 *
 * @param amount                          the qualifying cash amount (in AED unless {@link #currencyCode} says otherwise)
 * @param currencyCode                    ISO currency of {@link #amount}; defaults to AED. v1 checks AED only.
 * @param involvesPreciousMetalsOrStones  whether the goods are in DPMS scope; defaults to {@code true}
 */
public record ReportabilityCheckRequest(
        @NotNull BigDecimal amount,
        String currencyCode,
        Boolean involvesPreciousMetalsOrStones) {
}
