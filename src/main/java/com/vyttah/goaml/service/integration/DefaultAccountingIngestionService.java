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
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.service.report.ReportResult;
import com.vyttah.goaml.service.report.ReportService;
import lombok.RequiredArgsConstructor;
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

    private final TenantExternalRefRepository tenantExternalRefs;
    private final TenantRepository tenants;
    private final AppUserRepository appUsers;
    private final ReportRepository reportRepository;
    private final ReportabilityDetector detector;
    private final ReportService reportService;

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
            return new AccountingTxnResponse(ref, true, result.reportId(), result.status(), verdict.reasons());
        } finally {
            restore(previous);
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
