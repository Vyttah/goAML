package com.vyttah.goaml.service.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.domain.generated.TPersonRegistrationInReport;
import com.vyttah.goaml.engine.build.DpmsrReportBuilder;
import com.vyttah.goaml.engine.build.DpmsrReportInput;
import com.vyttah.goaml.engine.build.ValidatedReport;
import com.vyttah.goaml.engine.validation.Severity;
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

    /** Cap on the opaque {@code clientMetadata} blob (A3) — 16 KiB; over this is a 422. */
    private static final int MAX_CLIENT_METADATA_BYTES = 16 * 1024;

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
        List<ValidationMessage> mapperMessages = new ArrayList<>();
        return doCreate(of(filled, mapperMessages), filled, filled.clientMetadata(), tenantId, actorUserId,
                mapperMessages);
    }

    @Override
    public ReportResult create(DpmsrReportPayload payload, UUID tenantId, UUID actorUserId) {
        DpmsrReportPayload filled = withDefaultReportingPerson(payload, tenantId);
        // The full-fidelity payload maps 1:1 onto the generated types — no normalization, no mapper messages.
        return doCreate(filled, filled, filled.clientMetadata(), tenantId, actorUserId, List.of());
    }

    @Override
    public ReportValidationResult validate(DpmsrCreateRequest request, UUID tenantId) {
        List<ValidationMessage> mapperMessages = new ArrayList<>();
        ValidatedReport validated = buildAndValidate(
                of(withDefaultReportingPerson(request, tenantId), mapperMessages), tenantId);
        return new ReportValidationResult(statusOf(validated, mapperMessages),
                mergeMessages(validated, mapperMessages));
    }

    @Override
    public ReportPreview previewXml(DpmsrCreateRequest request, UUID tenantId) {
        List<ValidationMessage> mapperMessages = new ArrayList<>();
        ValidatedReport validated = buildAndValidate(
                of(withDefaultReportingPerson(request, tenantId), mapperMessages), tenantId);
        return new ReportPreview(statusOf(validated, mapperMessages), validated.xml(),
                mergeMessages(validated, mapperMessages));
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
                r.goods(), r.clientMetadata());
    }

    private static DpmsrCreateRequest.Person toPerson(TenantGoamlPerson p) {
        return new DpmsrCreateRequest.Person(p.getGender(), p.getFirstName(), p.getLastName(), null, null,
                p.getNationality(), null, p.getIdNumber(), null, p.getOccupation(), null, null, null);
    }

    /**
     * Same MLRO injection for the full-fidelity {@link DpmsrReportPayload} path: fill the reporting person
     * from the tenant's active {@code tenant_goaml_person} when the caller omitted it, so a full-payload
     * client (the AML cockpit) need not hold MLRO data either.
     */
    private DpmsrReportPayload withDefaultReportingPerson(DpmsrReportPayload payload, UUID tenantId) {
        if (payload.reportingPerson() != null) {
            return payload;
        }
        return personRepository.findByTenantIdAndActiveTrue(tenantId)
                .map(person -> withReportingPerson(payload, toRegistrationPerson(person)))
                .orElse(payload);
    }

    private static DpmsrReportPayload withReportingPerson(DpmsrReportPayload p, TPersonRegistrationInReport rp) {
        return new DpmsrReportPayload(p.rentityBranch(), p.entityReference(), p.submissionDate(),
                p.fiuRefNumber(), rp, p.location(), p.reason(), p.action(), p.indicators(), p.parties(),
                p.goods(), p.clientMetadata());
    }

    private static TPersonRegistrationInReport toRegistrationPerson(TenantGoamlPerson p) {
        TPersonRegistrationInReport rp = new TPersonRegistrationInReport();
        rp.setGender(p.getGender());
        rp.setFirstName(p.getFirstName());
        rp.setLastName(p.getLastName());
        rp.setNationality1(p.getNationality());
        rp.setIdNumber(p.getIdNumber());
        rp.setOccupation(p.getOccupation());
        return rp;
    }

    /**
     * Shared create path for both contracts: {@code src} drives the engine build; {@code persist} is the
     * original contract object stored verbatim as the report's input JSONB. {@code clientMetadata} (A3) is the
     * caller's opaque captured-not-filed JSON — persisted verbatim to its own column, never built into the
     * engine input (it is not read by {@code src.toInput(...)}) and so never reaches the marshalled XML.
     */
    private ReportResult doCreate(DpmsrInputSource src, Object persist, JsonNode clientMetadata,
                                  UUID tenantId, UUID actorUserId, List<ValidationMessage> mapperMessages) {
        if (reportRepository.existsByEntityReference(src.entityReference())) {
            throw new ReportExceptions.DuplicateEntityReferenceException(
                    "A report already exists with entity_reference " + src.entityReference());
        }

        String clientMetadataJson = clientMetadataJson(clientMetadata);

        int rentityId = resolveRentityId(tenantId);
        ValidatedReport validated = buildAndValidate(src, tenantId);

        List<ValidationMessage> messages = mergeMessages(validated, mapperMessages);
        String status = statusOf(validated, mapperMessages);

        Report report = new Report(UUID.randomUUID(), src.entityReference(), REPORT_CODE,
                rentityId, status, toJson(persist), actorUserId);
        report.setReportXml(validated.xml());
        report.setValidationErrors(toJson(messages));
        report.setClientMetadata(clientMetadataJson);
        reportRepository.save(report);

        auditService.record("REPORT.CREATE", actorUserId, null, TenantContext.get(),
                REPORT_CODE + " " + src.entityReference() + " -> " + status);

        return new ReportResult(report.getId(), status, messages);
    }

    /**
     * Serialize the optional client metadata to its verbatim JSON string for the {@code client_metadata}
     * column, enforcing the size cap. Returns {@code null} (column stays NULL) when no metadata was supplied.
     */
    private String clientMetadataJson(JsonNode clientMetadata) {
        if (clientMetadata == null || clientMetadata.isNull()) {
            return null;
        }
        String json = toJson(clientMetadata);
        int bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (bytes > MAX_CLIENT_METADATA_BYTES) {
            throw new ReportExceptions.ClientMetadataTooLargeException(
                    "clientMetadata is " + bytes + " bytes; the maximum is " + MAX_CLIENT_METADATA_BYTES);
        }
        return json;
    }

    /** The shared engine path: resolve the tenant's rentity_id + jurisdiction, build + validate. */
    private ValidatedReport buildAndValidate(DpmsrInputSource src, UUID tenantId) {
        Optional<TenantGoamlConfig> config = configRepository.findByTenantId(tenantId);
        int rentityId = config.map(TenantGoamlConfig::getRentityId).orElse(0);
        String jurisdiction = config.map(c -> c.getJurisdictionCode().toLowerCase()).orElse("ae");
        DpmsrReportInput input = src.toInput(rentityId);
        return reportBuilder.buildAndValidate(input, jurisdiction);
    }

    /**
     * Adapt the curated {@link DpmsrCreateRequest} to a {@link DpmsrInputSource} via the request mapper.
     * Normalization findings (changed/emptied names) are appended to {@code mapperMessages} when the input
     * is built, so the caller can merge them into the response.
     */
    private DpmsrInputSource of(DpmsrCreateRequest request, List<ValidationMessage> mapperMessages) {
        return new DpmsrInputSource() {
            @Override
            public String entityReference() {
                return request.entityReference();
            }

            @Override
            public DpmsrReportInput toInput(int rentityId) {
                return requestMapper.toInput(request, rentityId, mapperMessages);
            }
        };
    }

    private int resolveRentityId(UUID tenantId) {
        return configRepository.findByTenantId(tenantId).map(TenantGoamlConfig::getRentityId).orElse(0);
    }

    /** VALID only when the engine verdict is clean AND the mapper raised no ERROR (e.g. an emptied name). */
    private static String statusOf(ValidatedReport validated, List<ValidationMessage> mapperMessages) {
        boolean mapperClean = mapperMessages.stream().noneMatch(m -> m.severity() == Severity.ERROR);
        return validated.isValid() && mapperClean ? "VALID" : "INVALID";
    }

    private static List<ValidationMessage> mergeMessages(ValidatedReport validated,
                                                         List<ValidationMessage> mapperMessages) {
        List<ValidationMessage> messages = new ArrayList<>(mapperMessages);
        messages.addAll(validated.rules().messages());
        messages.addAll(validated.xsd().messages());
        return messages;
    }

    @Override
    public Report get(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportExceptions.ReportNotFoundException("Report not found: " + reportId));
    }

    @Override
    public ReportDetail detail(UUID reportId) {
        Report report = get(reportId);
        return new ReportDetail(
                report.getId(), report.getEntityReference(), report.getReportCode(), report.getStatus(),
                report.getRentityId(), report.getCreatedAt(),
                parseInput(report.getInput()), parseMessages(report.getValidationErrors()),
                report.getReviewedBy(), report.getReviewedAt(), report.getReviewRemark(),
                report.getReportXml() != null && !report.getReportXml().isBlank(),
                parseClientMetadata(report.getClientMetadata()));
    }

    /** The stored client metadata (JSONB) parsed back to a JSON tree, or {@code null} when none was stored. */
    private JsonNode parseClientMetadata(String clientMetadata) {
        if (clientMetadata == null || clientMetadata.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(clientMetadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse stored client metadata JSON", e);
        }
    }

    /** The stored filing input (JSONB) parsed back to a JSON tree for the read view. */
    private JsonNode parseInput(String input) {
        if (input == null || input.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(input);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse stored report input JSON", e);
        }
    }

    /** The persisted validation messages (JSONB), or an empty list when none were stored. */
    private List<ValidationMessage> parseMessages(String validationErrors) {
        if (validationErrors == null || validationErrors.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(validationErrors, new TypeReference<List<ValidationMessage>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse stored validation messages JSON", e);
        }
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
