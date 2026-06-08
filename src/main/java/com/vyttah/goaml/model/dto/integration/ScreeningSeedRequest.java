package com.vyttah.goaml.model.dto.integration;

import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The report-completing fields a goAML user supplies when seeding a DPMSR draft from a stored screened
 * subject (Phase 1.5c.3). The <b>parties</b> come from the screened subject (customer + shareholders/UBOs);
 * the caller provides the transaction-side data a screening profile can't carry (the goods/activity, the
 * reporting MLRO, the report reference). Reuses the {@link DpmsrCreateRequest} leaf types so the seeded
 * report is identical in shape to a hand-built one.
 *
 * @param entityReference the report reference (unique per tenant)
 * @param submissionDate  the report submission timestamp
 * @param reason          free-text reason for the report
 * @param action          the action taken (e.g. {@code Filed})
 * @param indicators      goAML report indicator codes
 * @param reportingPerson the filing MLRO
 * @param location        optional report location/address
 * @param goods           the DPMS goods/activity (a screened profile has none — supplied here)
 */
public record ScreeningSeedRequest(
        @NotBlank String entityReference,
        @NotNull OffsetDateTime submissionDate,
        String reason,
        String action,
        List<String> indicators,
        @NotNull DpmsrCreateRequest.Person reportingPerson,
        DpmsrCreateRequest.Address location,
        @NotNull List<DpmsrCreateRequest.Goods> goods) {
}
