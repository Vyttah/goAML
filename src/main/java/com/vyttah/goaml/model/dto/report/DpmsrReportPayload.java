package com.vyttah.goaml.model.dto.report;

import com.vyttah.goaml.domain.generated.ReportPartyType;
import com.vyttah.goaml.domain.generated.TAddress;
import com.vyttah.goaml.domain.generated.TPersonRegistrationInReport;
import com.vyttah.goaml.domain.generated.TTransItem;
import com.vyttah.goaml.engine.build.DpmsrReportInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Full-schema-fidelity JSON contract for a DPMSR report. Unlike the legacy curated {@code DpmsrCreateRequest},
 * this carries the <em>xjc-generated domain leaf types</em> directly ({@link TPersonRegistrationInReport},
 * {@link ReportPartyType} — all 6 subject choices, {@link TTransItem}, {@link TAddress}), so a caller can
 * supply <em>every</em> element the official goAML schema defines and nothing is silently dropped. It maps
 * 1:1 onto the engine's {@link DpmsrReportInput}.
 *
 * <p>The values goAML applies itself are <strong>not</strong> carried here and cannot be set by the caller:
 * {@code rentity_id} (from {@code tenant_goaml_config}), and the DPMSR-fixed {@code submission_code=E},
 * {@code report_code=DPMSR}, {@code currency_code_local=AED} (forced by the engine builder). They still
 * appear in the marshalled output.
 *
 * <p>Persisted verbatim as the report's {@code input} JSONB (the generated enums bind by schema value via
 * {@code GeneratedEnumJacksonModule}) so a report can be re-validated / re-edited losslessly.
 */
public record DpmsrReportPayload(
        String rentityBranch,
        @NotBlank String entityReference,
        @NotNull OffsetDateTime submissionDate,
        String fiuRefNumber,
        @NotNull TPersonRegistrationInReport reportingPerson,
        TAddress location,
        String reason,
        String action,
        List<String> indicators,
        @NotNull List<ReportPartyType> parties,
        @NotNull List<TTransItem> goods) {

    /** Map onto the engine input, injecting the server-resolved {@code rentity_id}. */
    public DpmsrReportInput toInput(int rentityId) {
        DpmsrReportInput.Builder b = DpmsrReportInput.builder()
                .rentityId(rentityId)
                .rentityBranch(rentityBranch)
                .entityReference(entityReference)
                .submissionDate(submissionDate)
                .fiuRefNumber(fiuRefNumber)
                .reportingPerson(reportingPerson)
                .location(location)
                .reason(reason)
                .action(action);
        if (indicators != null) {
            b.indicators(indicators.toArray(String[]::new));
        }
        if (parties != null) {
            parties.forEach(b::party);
        }
        if (goods != null) {
            b.goods(goods.toArray(TTransItem[]::new));
        }
        return b.build();
    }
}
