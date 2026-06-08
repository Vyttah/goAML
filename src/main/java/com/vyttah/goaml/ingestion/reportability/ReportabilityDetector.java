package com.vyttah.goaml.ingestion.reportability;

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
 * <p>v1 rule (per the UAE DPMS threshold — see {@code .planning/discussion-log.md} topic 11): a transaction
 * is reportable when it is a <strong>dealing in precious metals or stones</strong> <em>and</em> its
 * <strong>qualifying cash amount is ≥ AED {@value #DPMS_CASH_THRESHOLD}</strong>. The caller supplies the
 * qualifying AED amount (the cash settlement for the accounting push; the total goods value for the embedded
 * pre-check) and whether the goods are in DPMS scope — this class owns the threshold and the verdict, not the
 * source vocabulary.
 *
 * <p>Aggregation of multiple sub-threshold transactions ("structuring") is out of scope for v1 (per-document
 * detection only) and is logged as a future enhancement, not silently ignored.
 */
@Component
public class ReportabilityDetector {

    /** UAE DPMS cash threshold, in AED. At or above this, a cash precious-metals/stones dealing is reportable. */
    public static final BigDecimal DPMS_CASH_THRESHOLD_AED = new BigDecimal("55000");

    private static final String DPMS_CASH_THRESHOLD = "55,000";

    /**
     * @param qualifyingAmountAed the qualifying amount in AED (cash settlement, or total goods value for a pre-check)
     * @param involvesPreciousMetalsOrStones whether the goods are in DPMS scope (precious metals / stones)
     * @return the verdict with reasons (always populated, whether reportable or not)
     */
    public ReportabilityVerdict assess(BigDecimal qualifyingAmountAed, boolean involvesPreciousMetalsOrStones) {
        List<String> reasons = new ArrayList<>();

        boolean amountMeetsThreshold = qualifyingAmountAed != null
                && qualifyingAmountAed.compareTo(DPMS_CASH_THRESHOLD_AED) >= 0;

        if (!involvesPreciousMetalsOrStones) {
            reasons.add("Not a dealing in precious metals or stones — outside DPMS scope");
        }
        if (qualifyingAmountAed == null) {
            reasons.add("No qualifying AED amount supplied");
        } else if (amountMeetsThreshold) {
            reasons.add("Qualifying amount " + qualifyingAmountAed.toPlainString()
                    + " AED meets or exceeds the DPMS threshold of " + DPMS_CASH_THRESHOLD + " AED");
        } else {
            reasons.add("Qualifying amount " + qualifyingAmountAed.toPlainString()
                    + " AED is below the DPMS threshold of " + DPMS_CASH_THRESHOLD + " AED");
        }

        return new ReportabilityVerdict(involvesPreciousMetalsOrStones && amountMeetsThreshold,
                List.copyOf(reasons));
    }
}
