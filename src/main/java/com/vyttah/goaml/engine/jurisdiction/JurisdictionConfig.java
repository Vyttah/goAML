package com.vyttah.goaml.engine.jurisdiction;

import com.vyttah.goaml.domain.enums.ReportCode;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Per-jurisdiction (per-FIU) configuration that drives validation. Loaded from
 * {@code classpath:jurisdictions/<code>.yml} by {@link JurisdictionRegistry}. Only UAE
 * ({@code ae}) ships today; new countries slot in as new config + lookup sets, no code change.
 *
 * @param code               ISO-style jurisdiction key, e.g. {@code ae}
 * @param name               display name, e.g. {@code United Arab Emirates}
 * @param defaultCurrency    the FIU's local currency for {@code currency_code_local}, e.g. {@code AED}
 * @param allowedReportCodes report types this FIU accepts
 * @param dpmsThreshold      cash threshold above which a DPMS report is required (UAE: AED 55,000)
 * @param lookupSet          name of the lookup directory under {@code lookups/} to validate against
 */
public record JurisdictionConfig(
        String code,
        String name,
        String defaultCurrency,
        Set<ReportCode> allowedReportCodes,
        BigDecimal dpmsThreshold,
        String lookupSet) {

    public boolean allows(ReportCode reportCode) {
        return allowedReportCodes.contains(reportCode);
    }
}
