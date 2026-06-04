package com.vyttah.goaml.engine.build;

import com.vyttah.goaml.domain.generated.ActivityType;
import com.vyttah.goaml.domain.generated.Report;
import org.springframework.stereotype.Component;

/**
 * Assembles a {@link Report} for the activity-based report types — SAR, AIF, ECDD, and
 * the UAE FIU's DPMSR. Header fields come from {@link ReportHeader}; the activity body
 * (report parties + goods/services) comes from the caller, fully built.
 *
 * <p>The activity is set via {@link Report#setReportActivity(ActivityType)} — the choice
 * member that the generated model populates for activity-shaped reports (see goaml-bindings.xjb).
 *
 * <p>This builder does no validation — that lives in Phase 5's validation engine.
 */
@Component
public class ActivityReportBuilder {

    public Report build(ReportHeader header, ActivityType activity) {
        Report report = new Report();
        ReportHeaderApplier.apply(report, header);
        report.setReportActivity(activity);
        return report;
    }
}
