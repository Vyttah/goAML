package com.vyttah.goaml.model.dto.integration;

import com.vyttah.goaml.engine.validation.ValidationMessage;

import java.util.List;
import java.util.UUID;

/**
 * goAML's answer to an ERP filing (Phase 2 F0) — both the create and the status pull.
 *
 * <p>{@code validationMessages} carries the same {@link ValidationMessage} records the SPA's
 * {@code CreateReportResponse} returns (severity · path · code · message), so the ERP can show exactly why a
 * report came back {@code INVALID} rather than a flattened string.
 *
 * @param entityReference   the filing reference the ERP sent ({@code ACC-<companyId>-<docNo>}) — the
 *                          idempotency key
 * @param reportId          the created (or already-existing) goAML report id
 * @param status            report status: {@code VALID} / {@code INVALID} / {@code SUBMITTED} / …
 * @param duplicate         {@code true} when this call matched an existing report instead of creating one
 * @param validationMessages business-rule + XSD findings
 */
public record AccountingReportResponse(
        String entityReference,
        UUID reportId,
        String status,
        boolean duplicate,
        List<ValidationMessage> validationMessages) {
}
