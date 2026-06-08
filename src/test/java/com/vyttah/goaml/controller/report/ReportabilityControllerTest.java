package com.vyttah.goaml.controller.report;

import com.vyttah.goaml.ingestion.reportability.ReportabilityDetector;
import com.vyttah.goaml.model.dto.reportability.ReportabilityCheckRequest;
import com.vyttah.goaml.model.dto.reportability.ReportabilityCheckResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1.5b.2 — {@link ReportabilityController} request → verdict mapping (AED gating, precious default).
 */
class ReportabilityControllerTest {

    private final ReportabilityController controller = new ReportabilityController(new ReportabilityDetector());

    @Test
    void reportableForLargeAedPreciousAmount() {
        ReportabilityCheckResponse res = controller.check(
                new ReportabilityCheckRequest(new BigDecimal("90000"), "AED", true));
        assertThat(res.reportable()).isTrue();
        assertThat(res.thresholdAed()).isEqualByComparingTo("55000");
    }

    @Test
    void defaultsCurrencyToAedAndPreciousToTrue() {
        ReportabilityCheckResponse res = controller.check(
                new ReportabilityCheckRequest(new BigDecimal("60000"), null, null));
        assertThat(res.reportable()).isTrue();
    }

    @Test
    void notReportableBelowThreshold() {
        ReportabilityCheckResponse res = controller.check(
                new ReportabilityCheckRequest(new BigDecimal("10000"), "AED", true));
        assertThat(res.reportable()).isFalse();
    }

    @Test
    void notReportableWhenNotPrecious() {
        ReportabilityCheckResponse res = controller.check(
                new ReportabilityCheckRequest(new BigDecimal("90000"), "AED", false));
        assertThat(res.reportable()).isFalse();
    }

    @Test
    void rejectsNonAedAmount() {
        ReportabilityCheckResponse res = controller.check(
                new ReportabilityCheckRequest(new BigDecimal("90000"), "USD", true));
        assertThat(res.reportable()).isFalse();
        assertThat(res.reasons()).anyMatch(r -> r.contains("AED"));
    }
}
