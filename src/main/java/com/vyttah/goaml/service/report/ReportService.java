package com.vyttah.goaml.service.report;

import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
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

    /** @throws ReportExceptions.ReportNotFoundException if absent in this tenant */
    Report get(UUID reportId);

    List<Report> list();
}
