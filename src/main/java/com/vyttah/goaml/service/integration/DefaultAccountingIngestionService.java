package com.vyttah.goaml.service.integration;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.domain.generated.TPersonRegistrationInReport;
import com.vyttah.goaml.ingestion.reportability.ReportabilityDetector;
import com.vyttah.goaml.ingestion.reportability.ReportabilityVerdict;
import com.vyttah.goaml.model.dto.integration.AccountingReportResponse;
import com.vyttah.goaml.model.dto.integration.AccountingTxnPayload;
import com.vyttah.goaml.model.dto.integration.AccountingTxnResponse;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.model.dto.report.DpmsrReportPayload;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlPersonRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.service.audit.AuditService;
import com.vyttah.goaml.service.notification.NotificationService;
import com.vyttah.goaml.service.report.ReportDetail;
import com.vyttah.goaml.service.report.ReportResult;
import com.vyttah.goaml.service.report.ReportService;
import com.vyttah.goaml.service.report.pdf.ReportPdfService;
import com.vyttah.goaml.service.submission.SubmissionResult;
import com.vyttah.goaml.service.submission.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    /** Filing timestamps are FIU-facing calendar facts — stamp them at UAE local time, not UTC. */
    private static final ZoneId UAE = ZoneId.of("Asia/Dubai");

    private final TenantExternalRefRepository tenantExternalRefs;
    private final TenantRepository tenants;
    private final AppUserRepository appUsers;
    private final ReportRepository reportRepository;
    private final TenantGoamlConfigRepository configRepository;
    private final TenantGoamlPersonRepository personRepository;
    private final ReportabilityDetector detector;
    private final ReportService reportService;
    private final ReportPdfService reportPdfService;
    private final SubmissionService submissionService;
    private final NotificationService notificationService;
    private final AuditService auditService;

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
            // The server-stamped submission_date is filed in the XML — generate it at UAE local time so the
            // FIU sees the UAE calendar date (the marshaller preserves the wall-clock, never converts to UTC).
            DpmsrCreateRequest request = AccountingDpmsrMapper.toCreateRequest(
                    payload, ref, OffsetDateTime.now(UAE), mlro[0], mlro[1]);
            ReportResult result = reportService.create(request, tenant.tenantId(), null);
            return finalizeDraft(tenant.tenantId(), ref, result, verdict.reasons());
        } finally {
            restore(previous);
        }
    }

    /**
     * F0 — file an ERP-assembled full-fidelity report. No reportability judgement: the ERP operator ran the
     * filing wizard, so the decision to file is already made. Everything else matches {@link #ingest}: tenant
     * from the {@code ACCOUNTING} external ref, idempotency on the caller's {@code entityReference}, the MLRO
     * default, and the same auto-submit / MLRO-notify gate.
     */
    @Override
    public AccountingReportResponse fileReport(int companyId, DpmsrReportPayload payload, String actorSubject) {
        ResolvedTenant tenant = resolveTenant(companyId);
        String ref = payload.entityReference();

        String previous = TenantContext.get();
        TenantContext.set(tenant.schema());
        try {
            // Idempotent: a retried filing for the same document returns the existing report untouched
            // (reportService.create would answer 409 on the duplicate entity_reference).
            Optional<Report> existing = reportRepository.findByEntityReference(ref);
            if (existing.isPresent()) {
                Report r = existing.get();
                return new AccountingReportResponse(ref, r.getId(), r.getStatus(), true,
                        persistedMessages(r.getId()));
            }

            DpmsrReportPayload filled = withFallbackReportingPerson(payload, tenant.tenantId());
            ReportResult result = reportService.create(filled, tenant.tenantId(), null);
            String status = finalizeStatus(tenant.tenantId(), ref, result);
            audit(tenant.schema(), actorSubject, ref, result.reportId(), status);
            return new AccountingReportResponse(ref, result.reportId(), status, false,
                    result.validationMessages());
        } finally {
            restore(previous);
        }
    }

    @Override
    public AccountingReportResponse reportStatus(int companyId, String entityReference) {
        ResolvedTenant tenant = resolveTenant(companyId);
        String previous = TenantContext.get();
        TenantContext.set(tenant.schema());
        try {
            Report r = reportRepository.findByEntityReference(entityReference)
                    .orElseThrow(() -> new com.vyttah.goaml.service.report.ReportExceptions
                            .ReportNotFoundException("No goAML report for reference " + entityReference));
            return new AccountingReportResponse(entityReference, r.getId(), r.getStatus(), false,
                    persistedMessages(r.getId()));
        } finally {
            restore(previous);
        }
    }

    /**
     * X1 — the persisted XML of a filed report. Same shape as {@link #reportStatus}: tenant from the
     * {@code ACCOUNTING} ref, schema bound for the lookup, 404 on an unknown reference; and, as
     * {@code ReportController.xml}, 404 when the report has no generated XML (never the case for a report
     * created through {@link #fileReport}, which marshals at create — kept for parity).
     */
    @Override
    public ReportArtefact reportXml(int companyId, String entityReference) {
        ResolvedTenant tenant = resolveTenant(companyId);
        String previous = TenantContext.get();
        TenantContext.set(tenant.schema());
        try {
            Report r = reportRepository.findByEntityReference(entityReference)
                    .orElseThrow(() -> new com.vyttah.goaml.service.report.ReportExceptions
                            .ReportNotFoundException("No goAML report for reference " + entityReference));
            String xml = r.getReportXml();
            if (xml == null || xml.isBlank()) {
                throw new com.vyttah.goaml.service.report.ReportExceptions
                        .ReportNotFoundException("Report has no generated XML: " + r.getId());
            }
            return new ReportArtefact(r.getEntityReference() + ".xml", xml);
        } finally {
            restore(previous);
        }
    }

    /**
     * X1 — the PDF of a filed report, rendered now from its persisted XML by {@link ReportPdfService#filed}
     * (the same renderer the goAML SPA's {@code GET /api/v1/reports/{id}/report.pdf} uses), so its status
     * line always reflects the current goAML status. The tenant id goes to the renderer for the header; the
     * schema is bound here because {@code reportService.get/detail} read the tenant's {@code report} table.
     */
    @Override
    public ReportPdfService.PdfDocument reportPdf(int companyId, String entityReference, String userLabel) {
        ResolvedTenant tenant = resolveTenant(companyId);
        String previous = TenantContext.get();
        TenantContext.set(tenant.schema());
        try {
            Report r = reportRepository.findByEntityReference(entityReference)
                    .orElseThrow(() -> new com.vyttah.goaml.service.report.ReportExceptions
                            .ReportNotFoundException("No goAML report for reference " + entityReference));
            return reportPdfService.filed(r.getId(), tenant.tenantId(), userLabel);
        } finally {
            restore(previous);
        }
    }

    /** The validation messages persisted with a report, for the idempotent / status answers. */
    private List<com.vyttah.goaml.engine.validation.ValidationMessage> persistedMessages(UUID reportId) {
        try {
            ReportDetail detail = reportService.detail(reportId);
            return detail.validationMessages() == null ? List.of() : detail.validationMessages();
        } catch (RuntimeException e) {
            log.warn("Could not read persisted validation messages of report {}: {}", reportId, e.getMessage());
            return List.of();
        }
    }

    /**
     * {@link ReportService#create(DpmsrReportPayload, UUID, UUID)} already defaults the reporting person from
     * the tenant's active {@code tenant_goaml_person} (the richer record: gender, nationality, id number,
     * occupation), so we leave that path alone. This only covers the case it cannot: a tenant with <em>no</em>
     * configured goAML person but an active MLRO app user — fill the name so the report is not INVALID on
     * "reporting_person is mandatory" alone. Mirrors {@link #reportingPerson(UUID)}, which the
     * {@code /transactions} push uses for the same reason.
     */
    private DpmsrReportPayload withFallbackReportingPerson(DpmsrReportPayload payload, UUID tenantId) {
        if (payload.reportingPerson() != null
                || personRepository.findByTenantIdAndActiveTrue(tenantId).isPresent()) {
            return payload;
        }
        String[] mlro = reportingPerson(tenantId);
        TPersonRegistrationInReport rp = new TPersonRegistrationInReport();
        rp.setFirstName(mlro[0]);
        rp.setLastName(mlro[1]);
        return new DpmsrReportPayload(payload.rentityBranch(), payload.entityReference(),
                payload.submissionDate(), payload.fiuRefNumber(), rp, payload.location(), payload.reason(),
                payload.action(), payload.indicators(), payload.parties(), payload.goods(),
                payload.clientMetadata());
    }

    /** Best-effort audit of an ERP filing — the acting ERP user id lands in {@code actor_email}. */
    private void audit(String schema, String actorSubject, String ref, UUID reportId, String status) {
        try {
            auditService.record("REPORT.FILED_BY_ERP", null, actorSubject, schema,
                    "entityReference=" + ref + " reportId=" + reportId + " status=" + status);
        } catch (RuntimeException e) {
            log.warn("Could not audit ERP filing of {}: {}", ref, e.getMessage());
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
        return new AccountingTxnResponse(ref, true, result.reportId(),
                finalizeStatus(tenantId, ref, result), reasons);
    }

    /**
     * The submit gate itself, shared by the {@code /transactions} push ({@link #finalizeDraft}) and the F0
     * filing path ({@link #fileReport}) so both behave identically. Returns the status the caller should
     * report: the submission status when auto-submit ran, otherwise the create status.
     */
    private String finalizeStatus(UUID tenantId, String ref, ReportResult result) {
        UUID reportId = result.reportId();
        if (!"VALID".equals(result.status())) {
            return result.status();
        }

        boolean autoSubmit = configRepository.findByTenantId(tenantId)
                .map(TenantGoamlConfig::isAutoSubmit)
                .orElse(false);
        if (autoSubmit) {
            try {
                SubmissionResult sub = submissionService.submit(reportId, tenantId, null);
                return sub.status();
            } catch (RuntimeException e) {
                log.warn("Auto-submit of {} (tenant {}) failed: {} — leaving a VALID draft for the MLRO",
                        ref, tenantId, e.getMessage());
            }
        }

        notifyMlros(reportId, tenantId);
        return result.status();
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
