package com.vyttah.goaml.service.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.vyttah.goaml.engine.validation.ValidationMessage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The full read view of a stored report (Phase D.3): the summary, the stored filing {@code input} parsed back
 * to a JSON tree (parties / reporting person / goods / indicators / reason+action), the persisted validation
 * messages, and the D.2 review trail. {@code hasXml} signals whether the marshalled goAML XML is available to
 * view/download. This is read-only — a goAML login sees the whole filing without rebuilding it.
 */
public record ReportDetail(
        UUID id,
        String entityReference,
        String reportCode,
        String status,
        Integer rentityId,
        OffsetDateTime createdAt,
        JsonNode input,
        List<ValidationMessage> validationMessages,
        UUID reviewedBy,
        OffsetDateTime reviewedAt,
        String reviewRemark,
        boolean hasXml) {
}
