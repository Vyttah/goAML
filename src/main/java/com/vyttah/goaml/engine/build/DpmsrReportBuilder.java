package com.vyttah.goaml.engine.build;

import com.vyttah.goaml.domain.generated.ActivityType;
import com.vyttah.goaml.domain.generated.CurrencyType;
import com.vyttah.goaml.domain.generated.Report;
import com.vyttah.goaml.domain.generated.ReportType;
import com.vyttah.goaml.engine.marshal.ReportMarshaller;
import com.vyttah.goaml.engine.validation.ReportValidator;
import com.vyttah.goaml.engine.validation.ValidationResult;
import com.vyttah.goaml.engine.validation.XsdSchemaValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembles a valid DPMSR {@link Report} from a {@link DpmsrReportInput}, hiding the JAXB wrapper/choice/header
 * boilerplate. Schema-driven and invoice-generic: every goods/party field comes from the input's generated
 * leaf objects, and the DPMSR-fixed header values (submission_code {@code E}, report_code {@code DPMSR},
 * currency {@code AED}) are applied here. Reuses {@link ActivityReportBuilder} for header application + the
 * {@code reportActivity} choice slot.
 */
@RequiredArgsConstructor
@Component
public class DpmsrReportBuilder {

    private static final String SUBMISSION_CODE = "E";

    private final ActivityReportBuilder activityReportBuilder;
    private final ReportValidator reportValidator;
    private final XsdSchemaValidator xsdSchemaValidator;
    private final ReportMarshaller reportMarshaller;

    /** Build a DPMSR-shaped {@link Report} from the input (no validation — see {@link #buildAndValidate}). */
    public Report build(DpmsrReportInput in) {
        ActivityType activity = new ActivityType();
        activity.setReportParties(GoamlWrappers.reportParties(in.parties()));
        activity.setGoodsServices(GoamlWrappers.goodsServices(in.goods()));

        ReportHeader header = new ReportHeader(
                in.rentityId(),
                in.rentityBranch(),
                SUBMISSION_CODE,
                ReportType.DPMSR,
                in.entityReference(),
                in.fiuRefNumber(),
                in.submissionDate(),
                CurrencyType.AED,
                in.reportingPerson(),
                in.location(),
                in.reason(),
                in.action(),
                in.indicators());

        return activityReportBuilder.build(header, activity);
    }

    /**
     * Build and validate against both gates: business rules ({@link ReportValidator}) and the authoritative
     * XSD ({@link XsdSchemaValidator}, run on the marshalled XML). Callers (draft creation, the plugin) read
     * {@link ValidatedReport#isValid()} for a clear pass/fail.
     */
    public ValidatedReport buildAndValidate(DpmsrReportInput in, String jurisdictionCode) {
        Report report = build(in);
        byte[] xml = reportMarshaller.marshal(report);
        ValidationResult rules = reportValidator.validate(report, jurisdictionCode);
        ValidationResult xsd = xsdSchemaValidator.validate(xml);
        return new ValidatedReport(report, new String(xml, java.nio.charset.StandardCharsets.UTF_8), rules, xsd);
    }
}
