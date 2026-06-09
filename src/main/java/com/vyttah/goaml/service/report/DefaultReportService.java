package com.vyttah.goaml.service.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.engine.build.DpmsrReportBuilder;
import com.vyttah.goaml.engine.build.DpmsrReportInput;
import com.vyttah.goaml.engine.build.ValidatedReport;
import com.vyttah.goaml.engine.validation.ValidationMessage;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.model.dto.report.DpmsrInputSource;
import com.vyttah.goaml.model.dto.report.DpmsrReportPayload;
import com.vyttah.goaml.engine.build.DpmsrReportInput;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlPerson;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.mapper.report.DpmsrRequestMapper;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlPersonRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link ReportService}. Resolves {@code rentity_id}/jurisdiction from the tenant's
 * {@code tenant_goaml_config} (a report with no config builds with {@code rentity_id=0} → {@code INVALID},
 * surfaced as a validation error rather than a failure), builds + validates via the engine, and persists the
 * request JSON, the marshalled XML, and the status. Audit is recorded last (the audit service manages its own
 * tenant context).
 */
@RequiredArgsConstructor
@Service
public class DefaultReportService implements ReportService {

    private static final String REPORT_CODE = "DPMSR";

    private final DpmsrRequestMapper requestMapper;
    private final DpmsrReportBuilder reportBuilder;
    private final ReportRepository reportRepository;
    private final TenantGoamlConfigRepository configRepository;
    private final TenantGoamlPersonRepository personRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Override
    public ReportResult create(DpmsrCreateRequest request, UUID tenantId, UUID actorUserId) {
        DpmsrCreateRequest filled = withDefaultReportingPerson(request, tenantId);
        return doCreate(of(filled), filled, tenantId, actorUserId);
    }

    @Override
    public ReportResult create(DpmsrReportPayload payload, UUID tenantId, UUID actorUserId) {
        return doCreate(payload, payload, tenantId, actorUserId);
    }

    @Override
    public ReportValidationResult validate(DpmsrCreateRequest request, UUID tenantId) {
        ValidatedReport validated = buildAndValidate(of(withDefaultReportingPerson(request, tenantId)), tenantId);
        return new ReportValidationResult(statusOf(validated), mergeMessages(validated));
    }

    @Override
    public ReportPreview previewXml(DpmsrCreateRequest request, UUID tenantId) {
        ValidatedReport validated = buildAndValidate(of(withDefaultReportingPerson(request, tenantId)), tenantId);
        return new ReportPreview(statusOf(validated), validated.xml(), mergeMessages(validated));
    }

    /**
     * Fills the reporting person (the MLRO) from the tenant's active {@code tenant_goaml_person} when the
     * caller omitted it — so the AML cockpit / CSV / accounting / screening feeds need not send it (the
     * goAML-feeds-the-MLRO decision). The filled person is persisted as part of the report input (the
     * system-of-record shows exactly what was filed). If no default is configured and none was supplied, the
     * request is unchanged → null reporting person → INVALID ({@code reporting_person is mandatory}).
     */
    private DpmsrCreateRequest withDefaultReportingPerson(DpmsrCreateRequest request, UUID tenantId) {
        if (request.reportingPerson() != null) {
            return request;
        }
        return personRepository.findByTenantIdAndActiveTrue(tenantId)
                .map(person -> withReportingPerson(request, toPerson(person)))
                .orElse(request);
    }

    private static DpmsrCreateRequest withReportingPerson(DpmsrCreateRequest r, DpmsrCreateRequest.Person rp) {
        return new DpmsrCreateRequest(r.rentityBranch(), r.entityReference(), r.submissionDate(),
                r.fiuRefNumber(), r.reason(), r.action(), r.indicators(), rp, r.location(), r.parties(),
                r.goods());
    }

    private static DpmsrCreateRequest.Person toPerson(TenantGoamlPerson p) {
        return new DpmsrCreateRequest.Person(p.getGender(), p.getFirstName(), p.getLastName(), null, null,
                p.getNationality(), null, p.getIdNumber(), null, p.getOccupation(), null, null, null);
    }

    /**
     * Shared create path for both contracts: {@code src} drives the engine build; {@code persist} is the
     * original contract object stored verbatim as the report's input JSONB.
     */
    private ReportResult doCreate(DpmsrInputSource src, Object persist, UUID tenantId, UUID actorUserId) {
        if (reportRepository.existsByEntityReference(src.entityReference())) {
            throw new ReportExceptions.DuplicateEntityReferenceException(
                    "A report already exists with entity_reference " + src.entityReference());
        }

        int rentityId = resolveRentityId(tenantId);
        ValidatedReport validated = buildAndValidate(src, tenantId);

        List<ValidationMessage> messages = mergeMessages(validated);
        String status = statusOf(validated);

        Report report = new Report(UUID.randomUUID(), src.entityReference(), REPORT_CODE,
                rentityId, status, toJson(persist), actorUserId);
        report.setReportXml(validated.xml());
        report.setValidationErrors(toJson(messages));
        reportRepository.save(report);

        auditService.record("REPORT.CREATE", actorUserId, null, TenantContext.get(),
                REPORT_CODE + " " + src.entityReference() + " -> " + status);

        return new ReportResult(report.getId(), status, messages);
    }

    /** The shared engine path: resolve the tenant's rentity_id + jurisdiction, build + validate. */
    private ValidatedReport buildAndValidate(DpmsrInputSource src, UUID tenantId) {
        Optional<TenantGoamlConfig> config = configRepository.findByTenantId(tenantId);
        int rentityId = config.map(TenantGoamlConfig::getRentityId).orElse(0);
        String jurisdiction = config.map(c -> c.getJurisdictionCode().toLowerCase()).orElse("ae");
        DpmsrReportInput input = src.toInput(rentityId);
        return reportBuilder.buildAndValidate(input, jurisdiction);
    }

    /** Adapt the curated {@link DpmsrCreateRequest} to a {@link DpmsrInputSource} via the request mapper. */
    private DpmsrInputSource of(DpmsrCreateRequest request) {
        return new DpmsrInputSource() {
            @Override
            public String entityReference() {
                return request.entityReference();
            }

            @Override
            public DpmsrReportInput toInput(int rentityId) {
                return requestMapper.toInput(request, rentityId);
            }
        };
    }

    private int resolveRentityId(UUID tenantId) {
        return configRepository.findByTenantId(tenantId).map(TenantGoamlConfig::getRentityId).orElse(0);
    }

    private static String statusOf(ValidatedReport validated) {
        return validated.isValid() ? "VALID" : "INVALID";
    }

    private static List<ValidationMessage> mergeMessages(ValidatedReport validated) {
        List<ValidationMessage> messages = new ArrayList<>(validated.rules().messages());
        messages.addAll(validated.xsd().messages());
        return messages;
    }

    @Override
    public Report get(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportExceptions.ReportNotFoundException("Report not found: " + reportId));
    }

    @Override
    public List<Report> list() {
        return reportRepository.findAll();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize report JSON", e);
        }
    }
}
