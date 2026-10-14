package com.vyttah.goaml.model.dto.integration;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The report-status event goAML pushes to the AML platform (goAML ↔ ERP wiring, Phase 2 / S2, decision D22).
 * Posted to {@code POST {amlBaseUrl}/api/v1/integration/goaml/report-status} under a {@code GOAML} assertion;
 * AML de-duplicates on {@link #eventId()} and forwards it to the ERP's accounting service (S3 → S5).
 *
 * <p>Both company ids travel on the event even though either side could look the other up, because the two
 * receivers need different ones and neither should have to guess: AML cross-checks {@link #amlCompanyId()}
 * against the assertion's {@code org}, and the ERP tenant that the status belongs to is
 * {@link #erpCompanyId()}.
 *
 * @param eventId         the outbox row id — stable across redeliveries, which is what makes it a de-duplication key
 * @param tenantId        the goAML tenant, for support and for correlating logs across the three systems
 * @param amlCompanyId    the tenant's {@code SCREENING} external ref (the AML platform's company id)
 * @param erpCompanyId    the tenant's {@code ACCOUNTING} external ref (the ERP's company id)
 * @param reportId        the goAML report; the ERP checks it against the report id it stored when it filed
 * @param entityReference the filing reference — {@code ACC-<erpCompanyId>-<documentNumber>} for an ERP filing
 * @param status          the transition, not necessarily the report's resulting status
 * @param remark          the FIU's error text; the ERP shows it verbatim
 * @param actorUserId     who caused it, or null when the FIU answered a background poll
 * @param occurredAt      when it happened in goAML; the receivers order by this, never by arrival time
 */
public record ReportStatusEventPayload(UUID eventId,
                                       UUID tenantId,
                                       String amlCompanyId,
                                       String erpCompanyId,
                                       UUID reportId,
                                       String entityReference,
                                       String status,
                                       String remark,
                                       UUID actorUserId,
                                       OffsetDateTime occurredAt) {
}
