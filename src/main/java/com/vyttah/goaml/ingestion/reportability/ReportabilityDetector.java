package com.vyttah.goaml.ingestion.reportability;

import com.vyttah.goaml.ingestion.reportability.ReportabilityFacts.CounterpartyType;
import com.vyttah.goaml.ingestion.reportability.ReportabilityFacts.FundsType;
import com.vyttah.goaml.ingestion.reportability.ReportabilityFacts.WireScope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * goAML-owned reportability rules for DPMSR (dealers in precious metals & stones). Centralising the rule
 * here — rather than letting each sibling system re-implement it — keeps the reportability decision
 * authoritative and consistent across the accounting push, the screening push, and the embedded clients'
 * {@code /reportability/check} pre-check.
 *
 * <p><strong>Rules</strong> (MOE Circular 08/AML/2021 + UAE FIU goAML FAQ v1.10 Q51; legal basis Federal
 * Decree-Law 10/2025 + Cabinet Resolution 134/2025 — threshold unchanged at AED {@value #DPMS_THRESHOLD}),
 * for a dealing in precious metals or stones at/above the threshold:
 * <ul>
 *   <li><b>Individuals</b> (resident or not): <b>CASH</b> → reportable; card / cheque / bank transfer →
 *       <em>not</em> reportable.</li>
 *   <li><b>Legal persons/companies</b>: <b>CASH or WIRE TRANSFER</b> → reportable, except a <em>local UAE
 *       bank wire</em>, which is not reportable <em>unless</em> routed via an exchange house. <b>All
 *       international wires</b> (inward and outward) are reportable.</li>
 *   <li><b>Cheques</b> are never reportable; cards likewise.</li>
 *   <li><b>Linked / installment payments</b> against one invoice count toward a single report at the time
 *       the funds are received — the caller supplies the accumulated qualifying amount.</li>
 * </ul>
 *
 * <p><strong>Backwards compatibility:</strong> the two-argument {@link #assess(BigDecimal, boolean)} keeps
 * the original cash-trigger semantics (the amount supplied is a cash settlement — the individual/cash rule),
 * and a {@link ReportabilityFacts} with an unknown ({@code null}) funds type falls back to exactly that
 * behavior, so no existing caller changes verdict without supplying the new facts. Unknown wire details on
 * an explicit wire lean <em>conservative</em> (flag for review) — over-reporting is recoverable,
 * under-reporting is not.
 *
 * <p>Aggregation of multiple sub-threshold transactions ("structuring") remains out of scope (per-document
 * detection only) and is logged as a future enhancement, not silently ignored.
 */
@Component
public class ReportabilityDetector {

    /** UAE DPMS threshold, in AED. At or above this, a qualifying precious-metals/stones dealing is reportable. */
    public static final BigDecimal DPMS_CASH_THRESHOLD_AED = new BigDecimal("55000");

    private static final String DPMS_THRESHOLD = "55,000";
    private static final String CIRCULAR = "MOE Circular 08/AML/2021";
    private static final String FAQ = "UAE FIU goAML FAQ v1.10 Q51";

    /**
     * Original cash-trigger assessment — the amount is a <em>cash</em> settlement (the individual/cash rule).
     *
     * @param qualifyingAmountAed the qualifying amount in AED (cash settlement, or total goods value for a pre-check)
     * @param involvesPreciousMetalsOrStones whether the goods are in DPMS scope (precious metals / stones)
     * @return the verdict with reasons (always populated, whether reportable or not)
     */
    public ReportabilityVerdict assess(BigDecimal qualifyingAmountAed, boolean involvesPreciousMetalsOrStones) {
        return assess(ReportabilityFacts.of(qualifyingAmountAed, involvesPreciousMetalsOrStones));
    }

    /** Full assessment over the supplied {@link ReportabilityFacts}. */
    public ReportabilityVerdict assess(ReportabilityFacts facts) {
        List<String> reasons = new ArrayList<>();

        BigDecimal amount = facts.qualifyingAmountAed();
        boolean meetsThreshold = amount != null && amount.compareTo(DPMS_CASH_THRESHOLD_AED) >= 0;

        if (!facts.involvesPreciousMetalsOrStones()) {
            reasons.add("Not a dealing in precious metals or stones — outside DPMS scope");
        }
        if (amount == null) {
            reasons.add("No qualifying AED amount supplied");
        } else if (!meetsThreshold) {
            reasons.add("Qualifying amount " + amount.toPlainString()
                    + " AED is below the DPMS threshold of " + DPMS_THRESHOLD + " AED ("
                    + CIRCULAR + ")");
        }
        if (!facts.involvesPreciousMetalsOrStones() || !meetsThreshold) {
            return new ReportabilityVerdict(false, List.copyOf(reasons));
        }

        // In DPMS scope, at/above threshold — the funds type decides.
        boolean reportable = switch (facts.fundsType() == null ? FundsType.CASH : facts.fundsType()) {
            case CASH -> {
                reasons.add("Cash " + amount.toPlainString() + " AED meets or exceeds the DPMS threshold of "
                        + DPMS_THRESHOLD + " AED — reportable for individuals and companies ("
                        + CIRCULAR + (facts.fundsType() == null ? "; funds type not supplied, cash assumed" : "")
                        + ")");
                yield true;
            }
            case CHEQUE -> {
                reasons.add("Cheque payments are not reportable regardless of amount (" + FAQ + ")");
                yield false;
            }
            case CARD -> {
                reasons.add("Card payments are not reportable regardless of amount (" + FAQ + ")");
                yield false;
            }
            case WIRE_TRANSFER -> assessWire(facts, amount, reasons);
        };
        return new ReportabilityVerdict(reportable, List.copyOf(reasons));
    }

    /** The wire-transfer branch: individuals never; companies per the domestic/international rule. */
    private static boolean assessWire(ReportabilityFacts facts, BigDecimal amount, List<String> reasons) {
        if (facts.counterpartyType() == CounterpartyType.INDIVIDUAL) {
            reasons.add("Bank transfer from an individual (resident or not) is not reportable — only cash "
                    + "triggers the individual rule (" + FAQ + ")");
            return false;
        }
        String who = facts.counterpartyType() == CounterpartyType.LEGAL_ENTITY
                ? "a legal person/company"
                : "an unspecified counterparty (treated as a legal person for review — supply counterpartyType)";

        WireScope scope = facts.wireScope();
        if (scope == WireScope.INTERNATIONAL) {
            reasons.add("International wire transfer of " + amount.toPlainString() + " AED (inward or outward) "
                    + "from " + who + " meets the DPMS threshold — reportable (" + FAQ + ")");
            return true;
        }
        if (scope == WireScope.DOMESTIC) {
            if (Boolean.TRUE.equals(facts.viaExchangeHouse())) {
                reasons.add("Local UAE wire transfer of " + amount.toPlainString() + " AED from " + who
                        + " routed via an exchange house — reportable (" + FAQ + ")");
                return true;
            }
            if (facts.viaExchangeHouse() == null) {
                reasons.add("Local UAE wire transfer of " + amount.toPlainString() + " AED from " + who
                        + " with unknown routing — flagged for review (an exchange-house-routed wire is "
                        + "reportable; supply viaExchangeHouse) (" + FAQ + ")");
                return true;
            }
            reasons.add("Local UAE bank wire transfer from " + who + " (not via an exchange house) is not "
                    + "reportable (" + FAQ + ")");
            return false;
        }
        // Wire scope unknown — conservative: an international wire would be reportable, so flag for review.
        reasons.add("Wire transfer of " + amount.toPlainString() + " AED from " + who + " with unknown scope "
                + "— flagged for review (an international wire is reportable; supply wireScope) (" + FAQ + ")");
        return true;
    }
}
