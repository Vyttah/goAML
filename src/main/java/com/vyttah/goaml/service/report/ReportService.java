package com.vyttah.goaml.service.report;

import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.model.dto.report.DpmsrReportPayload;
import com.vyttah.goaml.model.entity.report.Report;

import java.util.List;
import java.util.UUID;

/**
 * Creates, validates, persists, and reads DPMSR reports for the current tenant. The engine builds + validates
 * (rules + XSD); this service persists the input (JSONB), the marshalled XML snapshot, and the status.
 */
public interface ReportService {

    /**
     * Build + validate a DPMSR from the request, persist it (status {@code VALID}/{@code INVALID}), and return
     * the outcome. {@code rentity_id} + jurisdiction are resolved from the tenant's {@code tenant_goaml_config}.
     *
     * @throws ReportExceptions.DuplicateEntityReferenceException if the {@code entity_reference} already exists
     */
    ReportResult create(DpmsrCreateRequest request, UUID tenantId, UUID actorUserId);

    /**
     * Full-schema-fidelity create: same as {@link #create(DpmsrCreateRequest, UUID, UUID)} but from the
     * {@link DpmsrReportPayload} contract, which carries every element the goAML schema defines (so nothing is
     * dropped). This is the contract the REST report API accepts.
     *
     * @throws ReportExceptions.DuplicateEntityReferenceException if the {@code entity_reference} already exists
     */
    ReportResult create(DpmsrReportPayload payload, UUID tenantId, UUID actorUserId);

    /**
     * Build + validate a DPMSR from the request and return the verdict + messages, <strong>without</strong>
     * persisting (no duplicate check). Same engine path as {@link #create} — lets a caller check a draft first.
     */
    ReportValidationResult validate(DpmsrCreateRequest request, UUID tenantId);

    /**
     * Build a DPMSR from the request to its marshalled goAML XML (the bytes that would be submitted) plus the
     * validation verdict, <strong>without</strong> persisting. Same engine path as {@link #create}.
     */
    ReportPreview previewXml(DpmsrCreateRequest request, UUID tenantId);

    /** @throws ReportExceptions.ReportNotFoundException if absent in this tenant */
    Report get(UUID reportId);

    List<Report> list();
}
