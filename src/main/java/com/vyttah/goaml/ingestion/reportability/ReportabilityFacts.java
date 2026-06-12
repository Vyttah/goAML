package com.vyttah.goaml.ingestion.reportability;

import java.math.BigDecimal;

/**
 * The facts a caller supplies for a DPMS reportability assessment. Every field beyond the original pair
 * (amount + DPMS scope) is <strong>optional</strong> — {@code null} means "not known", and an unknown
 * funds type falls back to the original cash-trigger behavior so no existing caller silently changes
 * verdict without supplying the new facts.
 *
 * @param qualifyingAmountAed            the qualifying amount in AED (cash settlement / wire amount, or
 *                                       total goods value for a pre-check)
 * @param involvesPreciousMetalsOrStones whether the goods are in DPMS scope (precious metals / stones)
 * @param fundsType                      how the funds moved ({@code null} = unknown → cash-trigger fallback)
 * @param counterpartyType               who paid ({@code null} = unknown)
 * @param wireScope                      for wire transfers, domestic vs international ({@code null} = unknown)
 * @param viaExchangeHouse               for domestic UAE wires, whether routed via an exchange house
 *                                       ({@code null} = unknown)
 */
public record ReportabilityFacts(
        BigDecimal qualifyingAmountAed,
        boolean involvesPreciousMetalsOrStones,
        FundsType fundsType,
        CounterpartyType counterpartyType,
        WireScope wireScope,
        Boolean viaExchangeHouse) {

    /** How the funds moved (MOE Circular 08/AML/2021 distinguishes these). */
    public enum FundsType { CASH, WIRE_TRANSFER, CHEQUE, CARD }

    /** Whether the counterparty is a natural person or a legal person/company. */
    public enum CounterpartyType { INDIVIDUAL, LEGAL_ENTITY }

    /** Whether a wire transfer is within the UAE or cross-border. */
    public enum WireScope { DOMESTIC, INTERNATIONAL }

    /** The original two-fact assessment (cash-trigger semantics) — used by the legacy entry point. */
    public static ReportabilityFacts of(BigDecimal qualifyingAmountAed, boolean involvesPreciousMetalsOrStones) {
        return new ReportabilityFacts(qualifyingAmountAed, involvesPreciousMetalsOrStones,
                null, null, null, null);
    }
}
