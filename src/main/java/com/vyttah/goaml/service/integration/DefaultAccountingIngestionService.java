package com.vyttah.goaml.service.integration;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.ingestion.reportability.ReportabilityDetector;
import com.vyttah.goaml.ingestion.reportability.ReportabilityVerdict;
import com.vyttah.goaml.model.dto.integration.AccountingTxnPayload;
import com.vyttah.goaml.model.dto.integration.AccountingTxnResponse;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.service.notification.NotificationService;
import com.vyttah.goaml.service.report.ReportResult;
import com.vyttah.goaml.service.report.ReportService;
import com.vyttah.goaml.service.submission.SubmissionResult;
import com.vyttah.goaml.service.submission.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link AccountingIngestionService}. Resolves the accounting company → goAML tenant, binds the
 * tenant {@link TenantContext} (the push has no user JWT, so it binds the schema itself — like the Phase 9
 * poller), runs the {@link ReportabilityDetector}, and on a reportable transaction reuses
 * {@link ReportService#create} to build a validated DPMSR draft as a system actor ({@code createdBy=null}).
 * Idempotent on the derived goAML reference, so accounting's outbox can retry safely.
 */
@RequiredArgsConstructor
@Service
public class DefaultAccountingIngestionService implements AccountingIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAccountingIngestionService.class);

    private final TenantExternalRefRepository tenantExternalRefs;
    private final TenantRepository tenants;
    private final AppUserRepository appUsers;
    private final ReportRepository reportRepository;
    private final TenantGoamlConfigRepository configRepository;
    private final ReportabilityDetector detector;
    private final ReportService reportService;
    private final SubmissionService submissionService;
    private final NotificationService notificationService;

    @Override
    public AccountingTxnResponse ingest(AccountingTxnPayload payload) {
        ResolvedTenant tenant = resolveTenant(payload.companyId());
        String ref = reference(payload.companyId(), payload.sourceDocument().documentNumber());

        String previous = TenantContext.get();
        TenantContext.set(tenant.schema());
        try {
            // Idempotent: a retried push for the same source document returns the existing report.
            Optional<Report> existing = reportRepository.findByEntityReference(ref);
            if (existing.isPresent()) {
                Report r = existing.get();
                return new AccountingTxnResponse(ref, true, r.getId(), r.getStatus(),
                        List.of("Already ingested for this source document"));
            }

            boolean precious = payload.goods().stream().anyMatch(g ->
                    CommodityMapping.isPrecious(g.commodityType(), g.metalAmount(), g.stoneAmount()));
            BigDecimal cashAed = payload.cashSettlement().cashAmountBaseCurrency();
            ReportabilityVerdict verdict = detector.assess(cashAed, precious);

            if (!verdict.reportable()) {
                return new AccountingTxnResponse(ref, false, null,
                        AccountingTxnResponse.NOT_REPORTABLE, verdict.reasons());
            }

            String[] mlro = reportingPerson(tenant.tenantId());
            DpmsrCreateRequest request = AccountingDpmsrMapper.toCreateRequest(
                    payload, ref, OffsetDateTime.now(ZoneOffset.UTC), mlro[0], mlro[1]);
            ReportResult result = reportService.create(request, tenant.tenantId(), null);
            return finalizeDraft(tenant.tenantId(), ref, result, verdict.reasons());
        } finally {
            restore(previous);
        }
    }

    /**
     * Decide what happens to a freshly-created reportable draft (submit gating, Phase 1.5b.5):
     * <ul>
     *   <li>not {@code VALID} (validation failed) — leave it for a human to fix; no auto-submit, no ping.</li>
     *   <li>{@code VALID} + tenant {@code auto_submit} on — submit to the FIU now (audited inside
     *       {@link SubmissionService#submit}); if that fails it falls back to the MLRO gate (best-effort —
     *       a transport/credential failure must never fail the accounting push).</li>
     *   <li>{@code VALID} + auto-submit off (default) — keep the draft and notify the tenant's MLROs that a
     *       one-click submit is waiting.</li>
     * </ul>
     * Runs with the tenant {@link TenantContext} already bound by {@link #ingest}.
     */
    private AccountingTxnResponse finalizeDraft(UUID tenantId, String ref, ReportResult result,
                                               List<String> reasons) {
        UUID reportId = result.reportId();
        if (!"VALID".equals(result.status())) {
            return new AccountingTxnResponse(ref, true, reportId, result.status(), reasons);
        }

        boolean autoSubmit = configRepository.findByTenantId(tenantId)
                .map(TenantGoamlConfig::isAutoSubmit)
                .orElse(false);
        if (autoSubmit) {
            try {
                SubmissionResult sub = submissionService.submit(reportId, tenantId, null);
                return new AccountingTxnResponse(ref, true, reportId, sub.status(), reasons);
            } catch (RuntimeException e) {
                log.warn("Auto-submit of {} (tenant {}) failed: {} — leaving a VALID draft for the MLRO",
                        ref, tenantId, e.getMessage());
            }
        }

        notifyMlros(reportId, tenantId);
        return new AccountingTxnResponse(ref, true, reportId, result.status(), reasons);
    }

    /** Best-effort MLRO ping that a validated draft awaits one-click submit — never fails the push. */
    private void notifyMlros(UUID reportId, UUID tenantId) {
        try {
            reportRepository.findById(reportId)
                    .ifPresent(r -> notificationService.notifyDraftAwaitingReview(r, tenantId));
        } catch (RuntimeException e) {
            log.warn("Failed to notify MLROs of draft {} (tenant {}): {}", reportId, tenantId, e.getMessage());
        }
    }

    @Override
    public AccountingTxnResponse status(int companyId, String documentNumber) {
        ResolvedTenant tenant = resolveTenant(companyId);
        String ref = reference(companyId, documentNumber);
        String previous = TenantContext.get();
        TenantContext.set(tenant.schema());
        try {
            Report r = reportRepository.findByEntityReference(ref)
                    .orElseThrow(() -> new com.vyttah.goaml.service.report.ReportExceptions
                            .ReportNotFoundException("No goAML report for document " + documentNumber));
            return new AccountingTxnResponse(ref, true, r.getId(), r.getStatus(), List.of());
        } finally {
            restore(previous);
        }
    }

    @Override
    public List<AccountingTxnResponse> list(int companyId, String statusFilter) {
        ResolvedTenant tenant = resolveTenant(companyId);
        String previous = TenantContext.get();
        TenantContext.set(tenant.schema());
        try {
            List<Report> reports = statusFilter == null || statusFilter.isBlank()
                    ? reportRepository.findAll()
                    : reportRepository.findByStatus(statusFilter.trim().toUpperCase());
            String prefix = referencePrefix(companyId);
            return reports.stream()
                    .filter(r -> r.getEntityReference() != null && r.getEntityReference().startsWith(prefix))
                    .map(r -> new AccountingTxnResponse(r.getEntityReference(), true, r.getId(),
                            r.getStatus(), List.of()))
                    .toList();
        } finally {
            restore(previous);
        }
    }

    private ResolvedTenant resolveTenant(int companyId) {
        UUID tenantId = tenantExternalRefs
                .findBySourceSystemAndExternalOrgRef(SourceSystem.ACCOUNTING, String.valueOf(companyId))
                .map(ref -> ref.getTenantId())
                .orElseThrow(() -> new IntegrationExceptions.UnmappedOrgException(
                        "No goAML tenant mapped to accounting company " + companyId));
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new IntegrationExceptions.UnmappedOrgException(
                        "Mapped tenant no longer exists for accounting company " + companyId));
        return new ResolvedTenant(tenant.getId(), tenant.getSchemaName());
    }

    private String[] reportingPerson(UUID tenantId) {
        List<AppUser> mlros = appUsers.findByTenantIdAndStatusAndRoles_Name(tenantId, "ACTIVE", "MLRO");
        if (!mlros.isEmpty()) {
            AppUser m = mlros.get(0);
            return new String[]{m.getFirstName(), m.getLastName()};
        }
        return new String[]{"Compliance", "Officer"};
    }

    private static String reference(int companyId, String documentNumber) {
        return referencePrefix(companyId) + documentNumber;
    }

    private static String referencePrefix(int companyId) {
        return "ACC-" + companyId + "-";
    }

    private static void restore(String previous) {
        if (previous != null) {
            TenantContext.set(previous);
        } else {
            TenantContext.clear();
        }
    }

    private record ResolvedTenant(UUID tenantId, String schema) {}
}
