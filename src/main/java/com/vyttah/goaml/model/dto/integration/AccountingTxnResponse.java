package com.vyttah.goaml.model.dto.integration;

import java.util.List;
import java.util.UUID;

/**
 * goAML's immediate response to an accounting push / status pull (Phase 1.5b, Model 2).
 *
 * @param goamlRef   the goAML report reference (derived, stable per source document — the idempotency key)
 * @param reportable whether goAML judged the transaction DPMS-reportable
 * @param reportId   the created/existing report id (null when not reportable)
 * @param status     report status (VALID/INVALID/SUBMITTED…) or {@code NOT_REPORTABLE}
 * @param reasons    the reportability reasons (shown to the caller + recorded on the draft)
 */
public record AccountingTxnResponse(
        String goamlRef,
        boolean reportable,
        UUID reportId,
        String status,
        List<String> reasons) {

    public static final String NOT_REPORTABLE = "NOT_REPORTABLE";
}
