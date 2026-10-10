package com.vyttah.goaml.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vyttah.goaml.config.integration.OutboundIntegrationProperties;
import com.vyttah.goaml.config.scheduler.SchedulerProperties;
import com.vyttah.goaml.model.dto.integration.ReportStatusEventPayload;
import com.vyttah.goaml.model.entity.federated.ReportStatusOutbox;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.repository.federated.ReportStatusOutboxRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.security.OutboundAssertionSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Drains {@code public.report_status_outbox} to the AML platform (goAML ↔ ERP wiring, Phase 2 / S2,
 * decision D22). The read half of the outbox; {@code ReportStatusOutboxService} is the write half.
 *
 * <p>Modelled on {@link SubmissionStatusPoller} and holding the same two invariants: the {@code @Scheduled}
 * method <strong>never</strong> lets an exception escape (Spring would suppress every future execution), and
 * one bad row never aborts the cycle. It needs no {@code TenantContext} juggling — the outbox is a shared table
 * carrying its own {@code tenant_id}, which is the reason S2 put it in {@code public} rather than per-tenant.
 *
 * <p><strong>Delivery is at-least-once, deliberately.</strong> A row is marked sent only on a 2xx, so a lost
 * acknowledgement causes a redelivery rather than a lost event — the receivers de-duplicate (AML on
 * {@code eventId}, accounting on {@code occurredAt}). The reverse choice would lose the FIU's answer to a
 * filing on any transient network fault, which is the one thing this path exists to prevent.
 *
 * <p>Like the poller, it honours {@code goaml.scheduler.enabled}: the Helm chart sets that false on the web
 * replicas and true on one dedicated pod, so with N replicas the AML platform is not sent N copies of every
 * event. That is coarse rather than exact — two instances with the switch on would both claim the same rows,
 * since the publisher takes no database lock — but it is the same topology control the FIU poller already
 * relies on, and redelivery is already handled.
 */
@Slf4j
@Component
public class ReportStatusPublisher {

    static final String PATH = "/api/v1/integration/goaml/report-status";

    static final String ASSERTION_HEADER = "X-Service-Assertion";

    /**
     * Retry delays by attempts already made: the first failure waits a minute, the fifth twelve hours, and it
     * stays at twelve hours after that. Bounded growth and no give-up state: an event the FIU has produced is
     * worth delivering late, and a permanent misconfiguration should stay visible in {@code last_error}
     * rather than quietly become a dropped row.
     */
    static final Duration[] BACKOFF = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
            Duration.ofHours(2), Duration.ofHours(12)
    };

    private final ReportStatusOutboxRepository outboxRepository;

    private final TenantExternalRefRepository tenantExternalRefRepository;

    private final OutboundAssertionSigner signer;

    private final OutboundIntegrationProperties properties;

    private final SchedulerProperties schedulerProperties;

    private final RestClient restClient;

    /**
     * Serialising the payload here rather than leaving it to whichever message converters the injected
     * {@code RestClient.Builder} happens to carry. Two reasons, both learned the hard way: a builder without
     * the Java-time module cannot write {@code occurredAt} at all (the call fails before any request is made,
     * which looks exactly like the receiver being down), and the receivers parse {@code occurredAt} as an
     * ISO-8601 instant — a mapper configured to write dates as epoch numbers would be accepted by Jackson and
     * rejected by them. Both are pinned on a private copy, so an app-wide mapper change cannot silently alter
     * this wire contract.
     */
    private final ObjectMapper json;

    public ReportStatusPublisher(ReportStatusOutboxRepository outboxRepository,
                                 TenantExternalRefRepository tenantExternalRefRepository,
                                 OutboundAssertionSigner signer,
                                 OutboundIntegrationProperties properties,
                                 SchedulerProperties schedulerProperties,
                                 RestClient.Builder restClientBuilder,
                                 ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.tenantExternalRefRepository = tenantExternalRefRepository;
        this.signer = signer;
        this.properties = properties;
        this.schedulerProperties = schedulerProperties;
        this.restClient = restClientBuilder.build();
        this.json = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** Timer entry point. Swallows everything so the schedule survives a bad cycle. */
    @Scheduled(fixedDelayString = "${goaml.integration.outbound.interval-ms:15000}")
    public void scheduledPublish() {
        if (!properties.usable() || !signer.isEnabled() || !schedulerProperties.enabled()) {
            return;
        }
        try {
            publishDue();
        } catch (RuntimeException e) {
            log.error("Report-status publish cycle failed", e);
        }
    }

    /**
     * Run one cycle over the due rows. Per-row failures are recorded on the row and skipped, so one
     * undeliverable event never blocks the ones behind it.
     *
     * @return how many rows were delivered
     */
    public int publishDue() {
        OffsetDateTime now = OffsetDateTime.now();
        List<ReportStatusOutbox> due = outboxRepository.findDue(now, Limit.of(properties.batchSize()));
        if (due.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (ReportStatusOutbox row : due) {
            if (publish(row)) {
                sent++;
            }
        }
        log.info("Report-status publish cycle: {} due, {} delivered", due.size(), sent);
        return sent;
    }

    private boolean publish(ReportStatusOutbox row) {
        try {
            String amlCompanyId = externalRef(row, SourceSystem.SCREENING);
            String erpCompanyId = externalRef(row, SourceSystem.ACCOUNTING);

            ReportStatusEventPayload payload = new ReportStatusEventPayload(row.getId(), row.getTenantId(),
                    amlCompanyId, erpCompanyId, row.getReportId(), row.getEntityReference(), row.getStatus(),
                    row.getRemark(), row.getActorUserId(), row.getOccurredAt());

            String subject = row.getActorUserId() == null
                    ? OutboundAssertionSigner.SYSTEM_SUBJECT
                    : row.getActorUserId().toString();

            restClient.post()
                    .uri(properties.amlBaseUrl() + PATH)
                    .header(ASSERTION_HEADER, signer.mint(subject, amlCompanyId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(payload))
                    .retrieve()
                    .toBodilessEntity();

            row.markSent(OffsetDateTime.now());
            outboxRepository.save(row);
            log.info("[goAML status outbox]: delivered {} for report {} (event {})",
                    row.getStatus(), row.getEntityReference(), row.getId());
            return true;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            fail(row, e);
            return false;
        }
    }

    /**
     * A tenant without the ref cannot produce a valid event — {@code SCREENING} is the assertion's {@code org}
     * and the AML platform rejects an event whose org it cannot match. That is a configuration problem, not a
     * transient one, so it is recorded on the row (where {@code last_error} will show it) instead of being
     * logged once and forgotten.
     */
    private String externalRef(ReportStatusOutbox row, SourceSystem system) {
        Optional<TenantExternalRef> ref =
                tenantExternalRefRepository.findByTenantIdAndSourceSystem(row.getTenantId(), system);
        if (ref.isEmpty()) {
            throw new IllegalStateException(
                    "Tenant " + row.getTenantId() + " has no " + system + " external ref");
        }
        return ref.get().getExternalOrgRef();
    }

    private void fail(ReportStatusOutbox row, Exception e) {
        Duration delay = BACKOFF[Math.min(row.getAttempts(), BACKOFF.length - 1)];
        String message = e.getClass().getSimpleName() + ": " + e.getMessage();
        row.markFailed(message, OffsetDateTime.now().plus(delay));
        try {
            outboxRepository.save(row);
        } catch (RuntimeException saveFailure) {
            log.error("Could not record the failure of event {}: {}", row.getId(), saveFailure.getMessage());
        }
        log.warn("Event {} ({} for report {}) failed on attempt {} — retrying in {}: {}",
                row.getId(), row.getStatus(), row.getEntityReference(), row.getAttempts(), delay, message);
    }
}
