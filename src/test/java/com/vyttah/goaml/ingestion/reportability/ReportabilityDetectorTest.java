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
}
