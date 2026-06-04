package com.vyttah.goaml.engine.validation;

import com.vyttah.goaml.domain.generated.ActivityType;
import com.vyttah.goaml.domain.generated.CurrencyType;
import com.vyttah.goaml.domain.generated.FundsType;
import com.vyttah.goaml.domain.generated.Report;
import com.vyttah.goaml.domain.generated.ReportType;
import com.vyttah.goaml.domain.generated.TTransItem;
import com.vyttah.goaml.engine.SampleReports;
import com.vyttah.goaml.engine.build.ActivityReportBuilder;
import com.vyttah.goaml.engine.build.TransactionReportBuilder;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionRegistry;
import com.vyttah.goaml.engine.lookup.LookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReportValidatorTest {

    private final ReportValidator validator =
            new ReportValidator(new JurisdictionRegistry(), new LookupService());
    private final ActivityReportBuilder activityBuilder = new ActivityReportBuilder();
    private final TransactionReportBuilder transactionBuilder = new TransactionReportBuilder();

    private Report buildSample(ReportType code) {
        SampleReports.Sample s = SampleReports.sampleFor(code);
        return s.isActivity()
                ? activityBuilder.build(s.header(), s.activity())
                : transactionBuilder.build(s.header(), s.transactions());
    }

    @ParameterizedTest
    @EnumSource(value = ReportType.class, names = {"DPMSR", "SAR", "AIF", "ECDD", "STR", "AIFT", "ECDDT"})
    void everyCanonicalSampleValidatesClean(ReportType code) {
        ValidationResult result = validator.validate(buildSample(code), "ae");
        assertThat(result.isValid())
                .as("sample %s should validate clean but had errors: %s", code, result.errors())
                .isTrue();
    }

    @Test
    void unknownJurisdictionIsRejected() {
        ValidationResult result = validator.validate(buildSample(ReportType.STR), "zz");
        assertThat(result.isValid()).isFalse();
        assertThat(result.hasCode("UNKNOWN_JURISDICTION")).isTrue();
    }

    @Test
    void missingEntityReferenceIsMandatory() {
        Report report = buildSample(ReportType.STR);
        report.setEntityReference(null);
        ValidationResult result = validator.validate(report, "ae");
        assertThat(result.isValid()).isFalse();
        assertThat(result.messages())
                .anyMatch(m -> m.code().equals("MANDATORY") && m.path().equals("report.entity_reference"));
    }

    @Test
    void aifWithoutFiuRefNumberIsRejected() {
        Report report = buildSample(ReportType.AIF);
        report.setFiuRefNumber(null);
        ValidationResult result = validator.validate(report, "ae");
        assertThat(result.isValid()).isFalse();
        assertThat(result.hasCode("FIU_REF_REQUIRED")).isTrue();
    }

    @Test
    void strWithoutReasonIsRejected() {
        Report report = buildSample(ReportType.STR);
        report.setReason(null);
        ValidationResult result = validator.validate(report, "ae");
        assertThat(result.isValid()).isFalse();
        assertThat(result.messages())
                .anyMatch(m -> m.code().equals("MANDATORY") && m.path().equals("report.reason"));
    }

    @Test
    void currencyOtherThanAedIsRejected() {
        Report report = buildSample(ReportType.STR);
        report.setCurrencyCodeLocal(CurrencyType.USD);
        ValidationResult result = validator.validate(report, "ae");
        assertThat(result.hasCode("CURRENCY_MISMATCH")).isTrue();
    }

    @Test
    void transactionBasedReportWithActivityIsShapeConflict() {
        Report report = buildSample(ReportType.STR);
        report.setReportActivity(new ActivityType());
        ValidationResult result = validator.validate(report, "ae");
        assertThat(result.hasCode("SHAPE_CONFLICT")).isTrue();
    }

    @Test
    void biPartyTransactionWithTwoFromSidesIsRejected() {
        Report report = buildSample(ReportType.STR);
        Report.Transaction tx = report.getTransaction().get(0);
        // sample already has a t_from_my_client; add a plain t_from to create two from-sides
        Report.Transaction.TFrom extra = new Report.Transaction.TFrom();
        extra.setFromFundsCode(FundsType.CASH);
        extra.setFromCountry("AE");
        tx.setTFrom(extra);
        ValidationResult result = validator.validate(report, "ae");
        assertThat(result.hasCode("BIPARTY_FROM")).isTrue();
    }

    @Test
    void transactionWithUnknownTransModeIsRejected() {
        Report report = buildSample(ReportType.STR);
        report.getTransaction().get(0).setTransmodeCode("NOT_A_MODE");
        ValidationResult result = validator.validate(report, "ae");
        assertThat(result.hasCode("LOOKUP")).isTrue();
    }

    @Test
    void dpmsrBelowThresholdIsRejected() {
        Report report = buildSample(ReportType.DPMSR);
        TTransItem g = report.getReportActivity().getGoodsServices().getItem().get(0);
        g.setEstimatedValue(new BigDecimal("10000.00")); // below AED 55,000
        g.setCurrencyCode(CurrencyType.AED);
        ValidationResult result = validator.validate(report, "ae");
        assertThat(result.isValid()).isFalse();
        assertThat(result.hasCode("DPMS_THRESHOLD")).isTrue();
    }

    @Test
    void dpmsrWithoutGoodsIsRejected() {
        Report report = buildSample(ReportType.DPMSR);
        report.getReportActivity().setGoodsServices(new ActivityType.GoodsServices());
        ValidationResult result = validator.validate(report, "ae");
        assertThat(result.hasCode("DPMS_GOODS_REQUIRED")).isTrue();
    }
}
