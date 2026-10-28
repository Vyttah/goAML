package com.vyttah.goaml.repository.federated;

import com.vyttah.goaml.model.entity.federated.ReportStatusOutbox;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The report-status outbox in shared {@code public.report_status_outbox} (goAML ↔ ERP wiring, Phase 2 / S2).
 */
public interface ReportStatusOutboxRepository extends JpaRepository<ReportStatusOutbox, UUID> {

    /**
     * Undelivered rows that are due, oldest <strong>transition</strong> first — so a backlog is delivered in
     * the order the transitions happened, not the order the retries came up. Matches
     * {@code idx_report_status_outbox_due}.
     */
    @Query("SELECT o FROM ReportStatusOutbox o "
            + "WHERE o.sentAt IS NULL AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now) "
            + "ORDER BY o.occurredAt ASC")
    List<ReportStatusOutbox> findDue(@Param("now") OffsetDateTime now, Limit limit);

    /** Everything recorded for one report, newest first — for the operator and for tests. */
    List<ReportStatusOutbox> findByReportIdOrderByOccurredAtDesc(UUID reportId);
}
