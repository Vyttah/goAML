package com.vyttah.goaml.service.integration;

import com.vyttah.goaml.ingestion.reportability.ReportabilityDetector;
import com.vyttah.goaml.ingestion.reportability.ReportabilityVerdict;
import com.vyttah.goaml.model.dto.integration.AccountingTxnPayload;
import com.vyttah.goaml.model.dto.integration.AccountingTxnResponse;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.service.notification.NotificationService;
import com.vyttah.goaml.service.report.ReportResult;
import com.vyttah.goaml.service.report.ReportService;
import com.vyttah.goaml.service.submission.SubmissionExceptions;
import com.vyttah.goaml.service.submission.SubmissionResult;
import com.vyttah.goaml.service.submission.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 1.5b.5 — submit gating in {@link DefaultAccountingIngestionService}: a reportable invoice creates a VALID
 * draft, then either auto-submits (tenant {@code auto_submit} on) or notifies the MLROs (off, the default).
 * Auto-submit failures fall back to the MLRO gate without failing the push.
 */
class DefaultAccountingIngestionServiceTest {

    private final TenantExternalRefRepository tenantExternalRefs = mock(TenantExternalRefRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final AppUserRepository appUsers = mock(AppUserRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final TenantGoamlConfigRepository configRepository = mock(TenantGoamlConfigRepository.class);
    private final ReportabilityDetector detector = mock(ReportabilityDetector.class);
    private final ReportService reportService = mock(ReportService.class);
    private final SubmissionService submissionService = mock(SubmissionService.class);
    private final NotificationService notificationService = mock(NotificationService.class);

    private final DefaultAccountingIngestionService service = new DefaultAccountingIngestionService(
            tenantExternalRefs, tenants, appUsers, reportRepository, configRepository, detector,
            reportService, submissionService, notificationService);

    private static final int COMPANY_ID = 777;
    private static final String SCHEMA = "tenant_acct";
    private final UUID tenantId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();

    @BeforeEach
    void resolveTenantAndReportable() {
        TenantExternalRef ref = mock(TenantExternalRef.class);
        when(ref.getTenantId()).thenReturn(tenantId);
        when(tenantExternalRefs.findBySourceSystemAndExternalOrgRef(
                SourceSystem.ACCOUNTING, String.valueOf(COMPANY_ID))).thenReturn(Optional.of(ref));

        Tenant tenant = mock(Tenant.class);
        when(tenant.getId()).thenReturn(tenantId);
        when(tenant.getSchemaName()).thenReturn(SCHEMA);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));

        when(reportRepository.findByEntityReference(any())).thenReturn(Optional.empty());
        when(detector.assess(any(), eq(true)))
                .thenReturn(new ReportabilityVerdict(true, List.of("cash ≥ AED 55,000, precious goods")));
        when(appUsers.findByTenantIdAndStatusAndRoles_Name(tenantId, "ACTIVE", "MLRO"))
                .thenReturn(List.of());
    }

    private void draftCreatesAs(String status) {
        when(reportService.create(any(), eq(tenantId), eq(null)))
                .thenReturn(new ReportResult(reportId, status, List.of()));
    }

    private void autoSubmit(boolean on) {
        TenantGoamlConfig cfg = mock(TenantGoamlConfig.class);
        when(cfg.isAutoSubmit()).thenReturn(on);
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(cfg));
    }

    @Test
    void autoSubmitOnSubmitsTheValidDraft() {
        draftCreatesAs("VALID");
        autoSubmit(true);
        when(submissionService.submit(reportId, tenantId, null))
                .thenReturn(new SubmissionResult(UUID.randomUUID(), "RK-1", "SUBMITTED"));

        AccountingTxnResponse res = service.ingest(payload());

        assertThat(res.status()).isEqualTo("SUBMITTED");
        assertThat(res.reportId()).isEqualTo(reportId);
        verify(submissionService).submit(reportId, tenantId, null);
        verifyNoInteractions(notificationService);
    }

    @Test
    void autoSubmitFailureFallsBackToMlroGateWithoutFailingThePush() {
        draftCreatesAs("VALID");
        autoSubmit(true);
        when(submissionService.submit(reportId, tenantId, null))
                .thenThrow(new SubmissionExceptions.SubmissionTransportException("FIU down", new RuntimeException()));
        Report draft = mock(Report.class);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(draft));

        AccountingTxnResponse res = service.ingest(payload());

        assertThat(res.status()).isEqualTo("VALID");           // left as a draft
        verify(notificationService).notifyDraftAwaitingReview(draft, tenantId);
    }

    @Test
    void autoSubmitOffNotifiesMlrosAndLeavesDraftValid() {
        draftCreatesAs("VALID");
        autoSubmit(false);
        Report draft = mock(Report.class);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(draft));

        AccountingTxnResponse res = service.ingest(payload());

        assertThat(res.status()).isEqualTo("VALID");
        verify(notificationService).notifyDraftAwaitingReview(draft, tenantId);
        verify(submissionService, never()).submit(any(), any(), any());
    }

    @Test
    void invalidDraftIsNeitherSubmittedNorAnnounced() {
        draftCreatesAs("INVALID");

        AccountingTxnResponse res = service.ingest(payload());

        assertThat(res.status()).isEqualTo("INVALID");
        verifyNoInteractions(submissionService, notificationService);
    }

    @Test
    void notReportableSkipsCreateSubmitAndNotify() {
        when(detector.assess(any(), eq(true)))
                .thenReturn(new ReportabilityVerdict(false, List.of("below AED 55,000")));

        AccountingTxnResponse res = service.ingest(payload());

        assertThat(res.reportable()).isFalse();
        assertThat(res.status()).isEqualTo(AccountingTxnResponse.NOT_REPORTABLE);
        verifyNoInteractions(reportService, submissionService, notificationService);
    }

    @Test
    void idempotentReturnsExistingWithoutCreating() {
        Report existing = mock(Report.class);
        when(existing.getId()).thenReturn(reportId);
        when(existing.getStatus()).thenReturn("SUBMITTED");
        when(reportRepository.findByEntityReference("ACC-777-SAL-1")).thenReturn(Optional.of(existing));

        AccountingTxnResponse res = service.ingest(payload());

        assertThat(res.reportId()).isEqualTo(reportId);
        assertThat(res.status()).isEqualTo("SUBMITTED");
        verifyNoInteractions(reportService, submissionService, notificationService);
    }

    private static AccountingTxnPayload payload() {
        return new AccountingTxnPayload(COMPANY_ID,
                new AccountingTxnPayload.SourceDocument("SAL-1", "SAL", LocalDate.of(2026, 6, 9), "SALE", "BULLION"),
                "AED",
                new AccountingTxnPayload.CashSettlement(new BigDecimal("90000"), "AED", LocalDate.of(2026, 6, 9),
                        List.of("REC-1")),
                new AccountingTxnPayload.Party("CORPORATE", "Acme Gold FZE", "TL-123", "AE", null, null, null, null),
                List.of(new AccountingTxnPayload.Goods("METAL", "GLD", "22K gold bar", null, null, null, null,
                        null, new BigDecimal("90000"), "AED", new BigDecimal("90000"), null)));
    }
}
