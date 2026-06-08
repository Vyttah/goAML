package com.vyttah.goaml.model.dto.integration;

import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;

import java.util.List;

/**
 * goAML's response to a screening party push / fetch (Phase 1.5c). Echoes back what goAML understood —
 * the stable subject reference (for later report seeding), the mapped goAML party set, and the sanctions
 * context — so the screening client can confirm the mapping.
 *
 * @param subjectRef       the stable subject reference {@code SCR-<companyId>-<customerUid>} (idempotency key)
 * @param subjectType      {@code NATURAL} | {@code LEGAL}
 * @param displayName      the customer's display name
 * @param riskFlag         whether sanctions screening flagged a risk
 * @param parties          the mapped goAML party set (customer + shareholders/UBOs)
 * @param sanctionsContext the PEP/sanctions summary recorded on the customer party (null when clean)
 */
public record ScreeningSubjectResponse(
        String subjectRef,
        String subjectType,
        String displayName,
        boolean riskFlag,
        List<DpmsrCreateRequest.Party> parties,
        String sanctionsContext) {
}
