package com.vyttah.goaml.engine.build;

import com.vyttah.goaml.domain.generated.Report;

/** Shared header-copy logic used by both builders. Package-private utility. */
final class ReportHeaderApplier {

    private ReportHeaderApplier() {}

    static void apply(Report report, ReportHeader header) {
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
