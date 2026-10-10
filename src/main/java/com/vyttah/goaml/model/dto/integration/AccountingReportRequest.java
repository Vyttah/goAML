package com.vyttah.goaml.model.dto.integration;

import com.vyttah.goaml.model.dto.report.DpmsrReportPayload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * ERP → goAML <em>filing</em> request (Phase 2 F0, filing path (b)): the ERP's accounting service posts an
 * already-assembled, full-fidelity {@link DpmsrReportPayload} — the same contract the AML cockpit posts to
 * {@code POST /api/v1/reports} — wrapped with the accounting {@code companyId} so the B11 org cross-check has
 * a request value to compare the signed {@code org} claim against.
 *
 * <p>This is <strong>not</strong> the {@code /transactions} push: there goAML judges reportability from a raw
 * invoice and assembles the report itself. Here the ERP's filing wizard has already decided to file and built
 * the whole report, so goAML only resolves the tenant, defaults the reporting person, builds + validates, and
 * applies the same auto-submit / MLRO gate.
 *
 * @param companyId the accounting company id (ERP {@code company_details.id}); must equal the assertion's
 *                  signed {@code org} claim
 * @param payload   the full DPMSR report; its {@code entityReference} is the idempotency key
 */
public record AccountingReportRequest(
        @Positive int companyId,
        @NotNull @Valid DpmsrReportPayload payload) {
}
