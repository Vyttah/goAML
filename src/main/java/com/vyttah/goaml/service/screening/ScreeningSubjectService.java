package com.vyttah.goaml.service.screening;

import com.vyttah.goaml.model.dto.integration.ScreeningSeedRequest;
import com.vyttah.goaml.model.dto.integration.ScreeningSubjectResponse;
import com.vyttah.goaml.service.report.ReportResult;

import java.util.List;
import java.util.UUID;

/**
 * User-facing reads + report-seeding over the screened subjects the AML screening software pushed
 * (Phase 1.5c.3). Operates in the caller's bound tenant {@link com.vyttah.goaml.config.tenant.TenantContext}
 * (set by the JWT filter), unlike the server-to-server {@code ScreeningIngestionService}.
 * Implemented by {@link DefaultScreeningSubjectService}.
 */
public interface ScreeningSubjectService {

    /** All screened subjects in the caller's tenant, newest first. */
    List<ScreeningSubjectResponse> list();

    /** One screened subject by its reference ({@code SCR-<companyId>-<customerUid>}). */
    ScreeningSubjectResponse get(String subjectRef);

    /**
     * Create a DPMSR draft seeded with the subject's parties + the caller-supplied goods/report fields.
     * Validated + persisted via the existing {@code ReportService.create}.
     */
    ReportResult seedReport(String subjectRef, ScreeningSeedRequest request, UUID tenantId, UUID actorUserId);
}
