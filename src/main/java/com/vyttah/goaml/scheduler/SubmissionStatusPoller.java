package com.vyttah.goaml.scheduler;

import com.vyttah.goaml.config.scheduler.SchedulerProperties;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.service.submission.SubmissionService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically refreshes the FIU status of every {@code SUBMITTED} report across all ACTIVE tenants, moving
 * them to {@code ACCEPTED}/{@code REJECTED} without anyone calling {@code GET …/status} (Phase 9). It only
 * orchestrates: discovery + per-tenant {@link TenantContext} binding + bounded transient retry; the actual
 * FIU call + status mapping + persistence stay in {@link SubmissionService#refreshStatus} (Phase 7).
 *
 * <p><strong>Two invariants</strong>: (1) the {@code @Scheduled} method never lets an exception escape —
 * Spring would suppress all future executions; (2) {@link TenantContext} is always cleared in a
 * {@code finally} per tenant, so a pooled thread can't leak one tenant's schema into the next iteration.
 */
@Slf4j
@Component
public class SubmissionStatusPoller {

    private static final String SUBMITTED = "SUBMITTED";
    private static final String ACTIVE = "ACTIVE";
    /** B8/decision #4: poll-failure counter (per-tenant AND per-report failures); G-OPS alerts on
     *  {@code goaml_poller_errors_total}. */
    static final String POLLER_ERRORS_METRIC = "goaml.poller.errors";

    private final TenantRepository tenantRepository;
    private final ReportRepository reportRepository;
    private final SubmissionService submissionService;
    private final RetryService retryService;
    private final SchedulerProperties properties;
    private final Counter pollerErrors;

    public SubmissionStatusPoller(TenantRepository tenantRepository, ReportRepository reportRepository,
                                  SubmissionService submissionService, RetryService retryService,
                                  SchedulerProperties properties, MeterRegistry meterRegistry) {
        this.tenantRepository = tenantRepository;
        this.reportRepository = reportRepository;
        this.submissionService = submissionService;
        this.retryService = retryService;
        this.properties = properties;
        this.pollerErrors = Counter.builder(POLLER_ERRORS_METRIC)
                .description("FIU status-poll failures, per tenant and per report (the cycle continues; the failed item is skipped)")
                .register(meterRegistry);
    }

    /**
     * Timer entry point. Guarded by two flags ({@code goaml.scheduler.enabled} master switch — set false on
     * web replicas by Helm so only the dedicated poller pod polls — and the finer {@code status-poll.enabled});
     * swallows everything so the schedule survives.
     */
    @Scheduled(fixedDelayString = "${goaml.scheduler.status-poll.interval}")
    public void scheduledPoll() {
        if (!properties.enabled() || !properties.statusPoll().enabled()) {
            return;
        }
        try {
            pollAllTenants();
        } catch (RuntimeException e) {
            log.error("Status poll cycle failed", e);
        }
    }

    /**
     * Run one full cycle across ACTIVE tenants. Per-tenant and per-report failures are logged and skipped so
     * one bad tenant/report never aborts the cycle. Returns a {@link PollSummary} (also logged).
     */
    public PollSummary pollAllTenants() {
        List<Tenant> tenants = tenantRepository.findByStatus(ACTIVE);
        int tenantsScanned = 0;
        int reportsPolled = 0;
        int succeeded = 0;
        int skipped = 0;

        for (Tenant tenant : tenants) {
            tenantsScanned++;
            try {
                TenantContext.set(tenant.getSchemaName());
                List<Report> submitted = reportRepository.findByStatus(SUBMITTED);
                for (Report report : submitted) {
                    reportsPolled++;
                    try {
                        retryService.retryTransient(
                                "poll report " + report.getId() + " (tenant " + tenant.getId() + ")",
                                () -> submissionService.refreshStatus(report.getId(), tenant.getId()));
                        succeeded++;
                    } catch (RuntimeException e) {
                        skipped++;
                        // Count per-REPORT failures too — during an FIU outage every refresh fails here
                        // (not in the per-tenant catch), and the G-OPS alert must still fire.
                        pollerErrors.increment();
                        log.warn("Skipping report {} (tenant {}): {}",
                                report.getId(), tenant.getId(), e.getMessage());
                    }
                }
            } catch (RuntimeException e) {
                skipped++;
                pollerErrors.increment();
                log.warn("Skipping tenant {} ({}): {}",
                        tenant.getId(), tenant.getSchemaName(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }

        PollSummary summary = new PollSummary(tenantsScanned, reportsPolled, succeeded, skipped);
        log.info("Status poll cycle: {} tenant(s) scanned, {} report(s) polled, {} ok, {} skipped",
                summary.tenantsScanned(), summary.reportsPolled(), summary.succeeded(), summary.skipped());
        return summary;
    }
}
