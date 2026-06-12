package com.vyttah.goaml.ingestion.reportability;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1.5b.1 — DPMS reportability rule: precious goods AND qualifying amount ≥ AED 55,000.
 */
class ReportabilityDetectorTest {

    private final ReportabilityDetector detector = new ReportabilityDetector();

    @Test
    void reportableWhenPreciousAndAtOrAboveThreshold() {
        assertThat(detector.assess(new BigDecimal("90000"), true).reportable()).isTrue();
    }

    @Test
    void boundaryExactlyAtThresholdIsReportable() {
        ReportabilityVerdict v = detector.assess(new BigDecimal("55000"), true);
        assertThat(v.reportable()).isTrue();
        assertThat(v.reasons()).anyMatch(r -> r.contains("meets or exceeds"));
    }

    @Test
    void notReportableBelowThreshold() {
        ReportabilityVerdict v = detector.assess(new BigDecimal("54999.99"), true);
        assertThat(v.reportable()).isFalse();
        assertThat(v.reasons()).anyMatch(r -> r.contains("below the DPMS threshold"));
    }

    @Test
    void notReportableWhenNotPreciousEvenIfLarge() {
        ReportabilityVerdict v = detector.assess(new BigDecimal("1000000"), false);
        assertThat(v.reportable()).isFalse();
        assertThat(v.reasons()).anyMatch(r -> r.contains("outside DPMS scope"));
    }

    @Test
    void notReportableWhenAmountNull() {
        ReportabilityVerdict v = detector.assess(null, true);
        assertThat(v.reportable()).isFalse();
        assertThat(v.reasons()).anyMatch(r -> r.contains("No qualifying AED amount"));
    }

    // ----- MOE Circular 08/AML/2021 + FIU FAQ v1.10 Q51 funds-flow matrix -----

    private static final BigDecimal ABOVE = new BigDecimal("90000");

    private ReportabilityVerdict assess(ReportabilityFacts.FundsType funds,
                                        ReportabilityFacts.CounterpartyType who,
                                        ReportabilityFacts.WireScope scope,
                                        Boolean viaExchangeHouse) {
        return detector.assess(new ReportabilityFacts(ABOVE, true, funds, who, scope, viaExchangeHouse));
    }

    @Test
    void cashAtOrAboveThresholdIsReportableForIndividualsAndCompanies() {
        ReportabilityVerdict individual = assess(ReportabilityFacts.FundsType.CASH,
                ReportabilityFacts.CounterpartyType.INDIVIDUAL, null, null);
        assertThat(individual.reportable()).isTrue();
        assertThat(individual.reasons()).anyMatch(r -> r.contains("MOE Circular 08/AML/2021"));

        assertThat(assess(ReportabilityFacts.FundsType.CASH,
                ReportabilityFacts.CounterpartyType.LEGAL_ENTITY, null, null).reportable()).isTrue();
    }

    @Test
    void individualWireTransferIsNotReportable() {
        ReportabilityVerdict v = assess(ReportabilityFacts.FundsType.WIRE_TRANSFER,
                ReportabilityFacts.CounterpartyType.INDIVIDUAL,
                ReportabilityFacts.WireScope.INTERNATIONAL, null);
        assertThat(v.reportable()).isFalse();
        assertThat(v.reasons()).anyMatch(r -> r.contains("individual"));
    }

    @Test
    void chequeAndCardAreNeverReportable() {
        ReportabilityVerdict cheque = assess(ReportabilityFacts.FundsType.CHEQUE,
                ReportabilityFacts.CounterpartyType.LEGAL_ENTITY, null, null);
        assertThat(cheque.reportable()).isFalse();
        assertThat(cheque.reasons()).anyMatch(r -> r.contains("Cheque"));

        ReportabilityVerdict card = assess(ReportabilityFacts.FundsType.CARD,
                ReportabilityFacts.CounterpartyType.INDIVIDUAL, null, null);
        assertThat(card.reportable()).isFalse();
        assertThat(card.reasons()).anyMatch(r -> r.contains("Card"));
    }

    @Test
    void companyInternationalWireIsReportable() {
        ReportabilityVerdict v = assess(ReportabilityFacts.FundsType.WIRE_TRANSFER,
                ReportabilityFacts.CounterpartyType.LEGAL_ENTITY,
                ReportabilityFacts.WireScope.INTERNATIONAL, null);
        assertThat(v.reportable()).isTrue();
        assertThat(v.reasons()).anyMatch(r -> r.contains("International wire"));
    }

    @Test
    void companyLocalBankWireIsNotReportable() {
        ReportabilityVerdict v = assess(ReportabilityFacts.FundsType.WIRE_TRANSFER,
                ReportabilityFacts.CounterpartyType.LEGAL_ENTITY,
                ReportabilityFacts.WireScope.DOMESTIC, false);
        assertThat(v.reportable()).isFalse();
        assertThat(v.reasons()).anyMatch(r -> r.contains("not via an exchange house"));
    }

    @Test
    void companyLocalWireViaExchangeHouseIsReportable() {
        ReportabilityVerdict v = assess(ReportabilityFacts.FundsType.WIRE_TRANSFER,
                ReportabilityFacts.CounterpartyType.LEGAL_ENTITY,
                ReportabilityFacts.WireScope.DOMESTIC, true);
        assertThat(v.reportable()).isTrue();
        assertThat(v.reasons()).anyMatch(r -> r.contains("exchange house"));
    }

    @Test
    void companyWireBelowThresholdIsNotReportable() {
        ReportabilityVerdict v = detector.assess(new ReportabilityFacts(new BigDecimal("54999"), true,
                ReportabilityFacts.FundsType.WIRE_TRANSFER, ReportabilityFacts.CounterpartyType.LEGAL_ENTITY,
                ReportabilityFacts.WireScope.INTERNATIONAL, null));
        assertThat(v.reportable()).isFalse();
        assertThat(v.reasons()).anyMatch(r -> r.contains("below the DPMS threshold"));
    }

    @Test
    void unknownWireDetailsAreFlaggedConservativelyForReview() {
        // explicit wire but unknown scope / unknown routing → flag for review (over-report, never under)
        assertThat(assess(ReportabilityFacts.FundsType.WIRE_TRANSFER,
                ReportabilityFacts.CounterpartyType.LEGAL_ENTITY, null, null).reportable()).isTrue();
        assertThat(assess(ReportabilityFacts.FundsType.WIRE_TRANSFER,
                ReportabilityFacts.CounterpartyType.LEGAL_ENTITY,
                ReportabilityFacts.WireScope.DOMESTIC, null).reportable()).isTrue();
    }

    @Test
    void unknownFundsTypeKeepsTheLegacyCashTriggerBehavior() {
        // additive guarantee: a caller that supplies no funds facts gets exactly the old cash-only verdict
        ReportabilityVerdict v = detector.assess(new ReportabilityFacts(ABOVE, true, null, null, null, null));
        assertThat(v.reportable()).isTrue();
        assertThat(v.reasons()).anyMatch(r -> r.contains("cash assumed"));
        assertThat(detector.assess(ABOVE, true).reportable()).isTrue();
        assertThat(detector.assess(new BigDecimal("1000"), true).reportable()).isFalse();
    }

    @Test
    void everyVerdictCitesTheRuleThatFired() {
        assertThat(assess(ReportabilityFacts.FundsType.CASH,
                ReportabilityFacts.CounterpartyType.INDIVIDUAL, null, null).reasons())
                .anyMatch(r -> r.contains("MOE Circular 08/AML/2021"));
        assertThat(assess(ReportabilityFacts.FundsType.CHEQUE, null, null, null).reasons())
                .anyMatch(r -> r.contains("FAQ v1.10 Q51"));
        assertThat(assess(ReportabilityFacts.FundsType.WIRE_TRANSFER,
                ReportabilityFacts.CounterpartyType.LEGAL_ENTITY,
                ReportabilityFacts.WireScope.INTERNATIONAL, null).reasons())
                .anyMatch(r -> r.contains("FAQ v1.10 Q51"));
    }
}
