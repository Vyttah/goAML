package com.vyttah.goaml.service.integration;

import com.vyttah.goaml.config.integration.OutboundIntegrationProperties;
import com.vyttah.goaml.model.entity.federated.ReportStatusOutbox;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.repository.federated.ReportStatusOutboxRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Records report-status transitions for delivery to the AML platform (goAML ↔ ERP wiring, Phase 2 / S2,
 * decision D22). The write half of the outbox; {@code ReportStatusPublisher} is the read half.
 *
 * <p>Called from every submission transition. Two things decide whether a transition is recorded:
 *
 * <ol>
 *   <li><strong>Is outbound integration usable at all?</strong> If not, nothing is recorded. The alternative —
 *       recording anyway so history is kept — would quietly accumulate rows in every standalone goAML
 *       deployment forever, and then flood the AML platform with months of stale transitions the first time
 *       someone turned the flag on. An "off" switch should make a feature inert, not merely silent.</li>
 *   <li><strong>Does the ERP file this tenant's reports?</strong> Only a tenant with an {@code ACCOUNTING}
 *       external ref has an ERP that could be waiting for the answer. One indexed lookup on the shared
 *       {@code tenant_external_ref}, which is the "cheap check" the step file asks for.</li>
 * </ol>
 *
 * <p><strong>A recording failure never breaks the compliance action.</strong> Approving or submitting a report
 * is the user's actual work; telling the ERP about it is a courtesy. So {@link #record} swallows and logs
 * everything — including, deliberately, the case where it is called from inside a transaction: a failed insert
 * there will still roll the enclosing transaction back when it commits, which is the correct outcome (status
 * and event are one unit) and is not something this method can or should paper over. What it must not do is
 * replace a submission's own FIU exception with a database error from the outbox.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportStatusOutboxService {

    private final ReportStatusOutboxRepository outboxRepository;

    private final TenantExternalRefRepository tenantExternalRefRepository;

    private final OutboundIntegrationProperties properties;

    /**
     * Record one transition, if it is of any interest to anyone.
     *
     * @param report      the report that transitioned; only its id and entity reference are read
     * @param tenantId    the goAML tenant owning it
     * @param status      the transition — {@code SUBMITTED}, {@code FAILED}, {@code ACCEPTED} or
     *                    {@code REJECTED} — <em>not</em> necessarily the report's resulting status
     * @param remark      the FIU's error text; forwarded verbatim, may be null
     * @param actorUserId who caused it, or null for a background poll
     * @return true when a row was written
     */
    public boolean record(Report report, UUID tenantId, String status, String remark, UUID actorUserId) {
        if (report == null || tenantId == null) {
            return false;
        }
        try {
            if (!properties.usable()) {
                return false;
            }
            if (tenantExternalRefRepository
                    .findByTenantIdAndSourceSystem(tenantId, SourceSystem.ACCOUNTING).isEmpty()) {
                log.debug("No ACCOUNTING ref for tenant {} — not recording {} for report {}",
                        tenantId, status, report.getEntityReference());
                return false;
            }
            outboxRepository.save(new ReportStatusOutbox(UUID.randomUUID(), tenantId, report.getId(),
                    report.getEntityReference(), status, remark, actorUserId, OffsetDateTime.now()));
            log.info("[goAML status outbox]: queued {} for report {} (tenant {})",
                    status, report.getEntityReference(), tenantId);
            return true;
        } catch (RuntimeException e) {
            // Never the reason a review or a submission fails. See the class javadoc on what this does and
            // does not protect.
            log.error("Failed to queue status {} for report {} (tenant {}): {}",
                    status, report.getEntityReference(), tenantId, e.getMessage());
            return false;
        }
    }
}
