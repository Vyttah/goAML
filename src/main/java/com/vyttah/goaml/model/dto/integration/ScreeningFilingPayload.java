package com.vyttah.goaml.model.dto.integration;

import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * AML cockpit → goAML <b>one-shot filing</b> (Phase C.4a): file a complete DPMSR in a single service-to-service
 * call. Unlike the parties-only screening push ({@link ScreeningPartyPayload}), this carries the customer party
 * bundle <i>and</i> the precious-metals deal ({@code goods}) <i>and</i> the report header, so goAML builds +
 * validates a DPMSR draft immediately. The AML deal module ("File to goAML") is the caller.
 *
 * <p>The customer party bundle reuses the resolved-codes {@link ScreeningPartyPayload} contract verbatim (mapped
 * by {@code ScreeningPartyMapper}); the goods reuse {@link DpmsrCreateRequest.Goods} (already the engine's goods
 * shape, exactly like {@code ScreeningSeedRequest}). The reporting person (MLRO) is the goAML tenant default
 * (Phase A) — the AML side sends none. {@code submissionDate} is server-stamped.
 *
 * @param companyId  the AML org id → resolves the goAML tenant via {@code tenant_external_ref} (SCREENING)
 * @param filingRef  the AML deal's stable id → the idempotency key {@code FIL-<companyId>-<filingRef>}
 * @param subject    the customer + related parties (resolved-codes; reused {@link ScreeningPartyPayload})
 * @param goods      the precious-metals deal items (≥1) — become the DPMSR {@code t_trans_item} goods
 * @param reason     optional report reason / description
 * @param action     optional action taken
 * @param indicators optional goAML report indicator codes
 * @param location   optional report location/address
 */
public record ScreeningFilingPayload(
        @NotBlank String companyId,
        @NotBlank String filingRef,
        @Valid @NotNull ScreeningPartyPayload subject,
        @Valid @NotEmpty List<DpmsrCreateRequest.Goods> goods,
        String reason,
        String action,
        List<String> indicators,
        @Valid DpmsrCreateRequest.Address location) {
}
