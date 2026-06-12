package com.vyttah.goaml.scheduler;

import com.vyttah.goaml.b2b.ReportStatus;
import com.vyttah.goaml.b2b.error.B2bTransportException;
import com.vyttah.goaml.config.scheduler.SchedulerProperties;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.service.submission.SubmissionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubmissionStatusPoller}: collaborators mocked; the mocked {@link RetryService} runs
 * the supplied call so {@code refreshStatus} is actually invoked. Asserts discovery, per-report/per-tenant
 * error isolation, {@link TenantContext} hygiene, and the disabled-flag short-circuit.
 */
class SubmissionStatusPollerTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final SubmissionService submissionService = mock(SubmissionService.class);
    private final RetryService retryService = mock(RetryService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    /** Poller with the finer status-poll flag toggled; the top-level master switch stays enabled. */
    private SubmissionStatusPoller poller(boolean statusPollEnabled) {
        return poller(true, statusPollEnabled);
    }

    private SubmissionStatusPoller poller(boolean masterEnabled, boolean statusPollEnabled) {
        SchedulerProperties props = new SchedulerProperties(masterEnabled,
                new SchedulerProperties.StatusPoll(statusPollEnabled, Duration.ofMinutes(5)),
                new SchedulerProperties.Retry(3, Duration.ZERO));
        // make the retry wrapper just run the supplied call
        lenient().when(retryService.retryTransient(anyString(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
        return new SubmissionStatusPoller(tenantRepository, reportRepository, submissionService,
                retryService, props, meterRegistry);
    }

    private double pollerErrorCount() {
        return meterRegistry.get(SubmissionStatusPoller.POLLER_ERRORS_METRIC).counter().count();
    }

    private Tenant tenant(UUID id, String schema) {
        Tenant t = mock(Tenant.class);
        lenient().when(t.getId()).thenReturn(id);
        lenient().when(t.getSchemaName()).thenReturn(schema);
        return t;
    }

    private Report report(String entityRef) {
        return new Report(UUID.randomUUID(), entityRef, "DPMSR", 3177, "SUBMITTED", "{}", null);
    }

    @AfterEach
    void contextIsAlwaysClear() {
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void pollsSubmittedReportsAcrossTenantsAndBindsContext() {
        UUID tA = UUID.randomUUID();
        UUID tB = UUID.randomUUID();
        Tenant ta = tenant(tA, "tenant_a");
        Tenant tb = tenant(tB, "tenant_b");
        when(tenantRepository.findByStatus("ACTIVE")).thenReturn(List.of(ta, tb));
        Report rA = report("A-1");
        Report rB = report("B-1");
        when(reportRepository.findByStatus("SUBMITTED"))
                .thenReturn(List.of(rA), List.of(rB));

        List<String> contextDuringCall = new ArrayList<>();
        when(submissionService.refreshStatus(any(), any())).thenAnswer(inv -> {
            contextDuringCall.add(TenantContext.get());
            return new ReportStatus("RK", "Accepted", null);
        });

        PollSummary summary = poller(true).pollAllTenants();

        assertThat(summary).isEqualTo(new PollSummary(2, 2, 2, 0));
        verify(submissionService).refreshStatus(rA.getId(), tA);
        verify(submissionService).refreshStatus(rB.getId(), tB);
        assertThat(contextDuringCall).containsExactlyInAnyOrder("tenant_a", "tenant_b");
    }

    @Test
    void perReportFailureIsSkippedAndCycleContinues() {
        UUID tId = UUID.randomUUID();
        Tenant tx = tenant(tId, "tenant_x");
        when(tenantRepository.findByStatus("ACTIVE")).thenReturn(List.of(tx));
        Report bad = report("BAD");
        Report good = report("GOOD");
        when(reportRepository.findByStatus("SUBMITTED")).thenReturn(List.of(bad, good));

        when(submissionService.refreshStatus(bad.getId(), tId)).thenThrow(new B2bTransportException("down"));
        when(submissionService.refreshStatus(good.getId(), tId)).thenReturn(new ReportStatus("RK", "Accepted", null));

        PollSummary summary = poller(true).pollAllTenants();

        assertThat(summary).isEqualTo(new PollSummary(1, 2, 1, 1));
        verify(submissionService).refreshStatus(good.getId(), tId); // continued past the failure
        // a per-REPORT failure must fire the alert metric too (an FIU outage fails every report here,
        // never the per-tenant catch)
        assertThat(pollerErrorCount()).isEqualTo(1.0);
    }

    @Test
    void perTenantFailureIsSkippedAndNextTenantStillScanned() {
        UUID tA = UUID.randomUUID();
        UUID tB = UUID.randomUUID();
        Tenant ta = tenant(tA, "tenant_a");
        Tenant tb = tenant(tB, "tenant_b");
        when(tenantRepository.findByStatus("ACTIVE")).thenReturn(List.of(ta, tb));
        Report rB = report("B-1");
        // first tenant's discovery blows up, second succeeds
        when(reportRepository.findByStatus("SUBMITTED"))
                .thenThrow(new RuntimeException("schema boom"))
                .thenReturn(List.of(rB));
        when(submissionService.refreshStatus(any(), any())).thenReturn(new ReportStatus("RK", "Accepted", null));

        PollSummary summary = poller(true).pollAllTenants();

        assertThat(summary.tenantsScanned()).isEqualTo(2);
        assertThat(summary.skipped()).isEqualTo(1);   // the failed tenant
        assertThat(summary.succeeded()).isEqualTo(1); // tenant B's report
        verify(submissionService).refreshStatus(rB.getId(), tB);
        // B8/decision #4: the per-tenant failure incremented the metric (and did not throw out of the cycle).
        assertThat(pollerErrorCount()).isEqualTo(1.0);
    }

    @Test
    void disabledStatusPollFlagShortCircuitsScheduledPoll() {
        poller(false).scheduledPoll();
        verify(tenantRepository, never()).findByStatus(any());
    }

    @Test
    void disabledMasterSwitchShortCircuitsScheduledPoll() {
        // B8: GOAML_SCHEDULER_ENABLED=false (web replicas) → the @Scheduled method no-ops on this instance.
        poller(false, true).scheduledPoll();
        verify(tenantRepository, never()).findByStatus(any());
    }

    @Test
    void scheduledPollSwallowsExceptionsSoScheduleSurvives() {
        when(tenantRepository.findByStatus("ACTIVE")).thenThrow(new RuntimeException("db down"));
        // must NOT throw out of the @Scheduled method
        poller(true).scheduledPoll();
        verify(tenantRepository).findByStatus("ACTIVE");
    }

    @Test
    void enabledScheduledPollRunsACycle() {
        UUID tId = UUID.randomUUID();
        Tenant tx = tenant(tId, "tenant_x");
        when(tenantRepository.findByStatus("ACTIVE")).thenReturn(List.of(tx));
        when(reportRepository.findByStatus("SUBMITTED")).thenReturn(List.of(report("A-1")));
        ArgumentCaptor<UUID> reportId = ArgumentCaptor.forClass(UUID.class);
        when(submissionService.refreshStatus(reportId.capture(), eq(tId)))
                .thenReturn(new ReportStatus("RK", "Accepted", null));

        poller(true).scheduledPoll();

        verify(submissionService).refreshStatus(any(), eq(tId));
    }
}
