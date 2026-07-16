package com.vyttah.goaml.engine.build;

import com.vyttah.goaml.domain.generated.Report;

/** Shared header-copy logic used by both builders. Package-private utility. */
final class ReportHeaderApplier {

    /** Stamped on every built report — matches the vendored XSD ({@code xsd/goaml/5.0.2}) and what the
     *  FIU web portal itself emits ({@code <schema_version>5.0.2</schema_version>} in downloaded reports). */
    static final String SCHEMA_VERSION = "5.0.2";

    private ReportHeaderApplier() {}

    static void apply(Report report, ReportHeader header) {
        report.setSchemaVersion(SCHEMA_VERSION);
        if (header.rentityId() != null) {
            report.setRentityId(header.rentityId());
        }
        report.setRentityBranch(header.rentityBranch());
        report.setSubmissionCode(header.submissionCode());
        report.setReportCode(header.reportCode());
        report.setEntityReference(header.entityReference());
        report.setFiuRefNumber(header.fiuRefNumber());
        report.setSubmissionDate(header.submissionDate());
        report.setCurrencyCodeLocal(header.currencyCodeLocal());
        report.setReportingPerson(header.reportingPerson());
        report.setLocation(header.location());
        report.setReason(header.reason());
        report.setAction(header.action());
        if (header.reportIndicators() != null && !header.reportIndicators().isEmpty()) {
            Report.ReportIndicators indicators = new Report.ReportIndicators();
            indicators.getIndicator().addAll(header.reportIndicators());
            report.setReportIndicators(indicators);
        }
    }
}
