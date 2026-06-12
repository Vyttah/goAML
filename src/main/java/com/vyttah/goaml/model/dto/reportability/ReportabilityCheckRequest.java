package com.vyttah.goaml.model.dto.reportability;

import com.vyttah.goaml.ingestion.reportability.ReportabilityFacts;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request to goAML's authoritative reportability check (Phase 1.5b). Lets an embedded client (accounting /
 * AML software) ask "is this DPMS-reportable?" before building or submitting a report.
 *
 * <p>The funds-flow fields are optional and additive (MOE Circular 08/AML/2021 + FIU FAQ v1.10 Q51 rules):
 * omitting them keeps the original cash-trigger behavior, so existing callers are unaffected.
 *
 * @param amount                          the qualifying amount (in AED unless {@link #currencyCode} says otherwise)
 * @param currencyCode                    ISO currency of {@link #amount}; defaults to AED. v1 checks AED only.
 * @param involvesPreciousMetalsOrStones  whether the goods are in DPMS scope; defaults to {@code true}
 * @param fundsType                       how the funds moved ({@code CASH}|{@code WIRE_TRANSFER}|{@code CHEQUE}|
 *                                        {@code CARD}); omitted → cash-trigger fallback
 * @param counterpartyType                {@code INDIVIDUAL}|{@code LEGAL_ENTITY}; omitted → unknown
 * @param wireScope                       for wires, {@code DOMESTIC}|{@code INTERNATIONAL}; omitted → unknown
 * @param viaExchangeHouse                for domestic UAE wires, whether routed via an exchange house
 */
public record ReportabilityCheckRequest(
        @NotNull BigDecimal amount,
        String currencyCode,
        Boolean involvesPreciousMetalsOrStones,
        ReportabilityFacts.FundsType fundsType,
        ReportabilityFacts.CounterpartyType counterpartyType,
        ReportabilityFacts.WireScope wireScope,
        Boolean viaExchangeHouse) {

    /** Back-compat constructor (the original three-field contract). */
    public ReportabilityCheckRequest(BigDecimal amount, String currencyCode,
                                     Boolean involvesPreciousMetalsOrStones) {
        this(amount, currencyCode, involvesPreciousMetalsOrStones, null, null, null, null);
    }
}
