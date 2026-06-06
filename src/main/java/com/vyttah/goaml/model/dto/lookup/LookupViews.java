package com.vyttah.goaml.model.dto.lookup;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only API views over the engine's jurisdiction + lookup reference data (Phase 13). These power the
 * frontend's builder dropdowns and the lookups browser, so UI choices always match backend validation.
 */
public final class LookupViews {

    private LookupViews() {}

    /**
     * A jurisdiction (FIU) the platform validates against.
     *
     * @param code               e.g. {@code ae}
     * @param name               e.g. {@code United Arab Emirates}
     * @param defaultCurrency    local currency, e.g. {@code AED}
     * @param allowedReportTypes report codes this FIU accepts (e.g. {@code DPMSR})
     * @param dpmsThreshold      DPMS cash threshold (UAE: 55000), may be null
     * @param lookupSet          the lookup directory name backing this jurisdiction
     */
    public record JurisdictionView(String code, String name, String defaultCurrency,
                                   List<String> allowedReportTypes, BigDecimal dpmsThreshold,
                                   String lookupSet) {}

    /** The lookup-set names available for a jurisdiction (e.g. countries, currencies, fund types). */
    public record LookupSetsView(String jurisdiction, List<String> sets) {}

    /** The codes in one lookup set (sorted), e.g. all ISO country codes for {@code ae}. */
    public record LookupSetView(String jurisdiction, String set, List<String> codes) {}
}
