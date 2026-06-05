package com.vyttah.goaml.engine.build;

import com.vyttah.goaml.domain.generated.Report;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Assembles a {@link Report} for the transaction-based report types — STR, AIFT, ECDDT.
 * Header fields come from {@link ReportHeader}; the transaction list comes from the caller.
 */
@Component
public class TransactionReportBuilder {

    public Report build(ReportHeader header, List<Report.Transaction> transactions) {
        Report report = new Report();
        ReportHeaderApplier.apply(report, header);
        if (transactions != null) {
            report.getTransaction().addAll(transactions);
        }
        return report;
    }
}
