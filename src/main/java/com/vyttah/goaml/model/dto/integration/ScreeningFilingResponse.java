package com.vyttah.goaml.model.dto.integration;

import com.vyttah.goaml.engine.validation.ValidationMessage;

import java.util.List;
import java.util.UUID;

/**
 * goAML's response to a one-shot filing (Phase C.4a). Echoes the stable filing reference (idempotency key), the
 * created goAML report id + its persisted status ({@code VALID}/{@code INVALID}), and the merged validation
 * messages — so the AML cockpit can store the report id, link to it, and surface any validation gaps to the
 * analyst (e.g. a person-party draft missing the heavier field set).
 *
 * @param filingRef          {@code FIL-<companyId>-<filingRef>} — the idempotency key
 * @param reportId           the created/idempotently-returned goAML report id
 * @param status             {@code VALID} | {@code INVALID}
 * @param validationMessages business-rule + XSD messages (empty when clean)
 */
public record ScreeningFilingResponse(
        String filingRef,
        UUID reportId,
        String status,
        List<ValidationMessage> validationMessages) {
}
