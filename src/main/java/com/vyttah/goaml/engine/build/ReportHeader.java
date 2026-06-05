package com.vyttah.goaml.engine.build;

import com.vyttah.goaml.domain.generated.CurrencyType;
import com.vyttah.goaml.domain.generated.ReportType;
import com.vyttah.goaml.domain.generated.TAddress;
import com.vyttah.goaml.domain.generated.TPersonRegistrationInReport;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Common header fields shared by every {@link com.vyttah.goaml.domain.generated.Report}. Used as the
 * "neutral" input to both {@link ActivityReportBuilder} and {@link TransactionReportBuilder}.
 *
 * <p>Conditional mandatory rules (e.g. {@code fiuRefNumber} for AIF/AIFT/ECDD/ECDDT,
 * {@code location}/{@code reason}/{@code action} for STR/SAR) are enforced by the validation
 * engine in Phase 5 — this DTO carries every field as nullable so the same shape works for
 * all report codes. {@code submissionCode} is the schema's {@code submission_type} string (e.g. {@code E}).
 */
public record ReportHeader(
        Integer rentityId,
        String rentityBranch,
        String submissionCode,
        ReportType reportCode,
        String entityReference,
        String fiuRefNumber,
        OffsetDateTime submissionDate,
        CurrencyType currencyCodeLocal,
        TPersonRegistrationInReport reportingPerson,
        TAddress location,
        String reason,
        String action,
        List<String> reportIndicators) {
}
