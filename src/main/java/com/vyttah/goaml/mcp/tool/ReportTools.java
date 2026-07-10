package com.vyttah.goaml.mcp.tool;

import com.vyttah.goaml.engine.validation.Severity;
import com.vyttah.goaml.engine.validation.ValidationMessage;
import com.vyttah.goaml.mcp.McpIdentity;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.model.dto.report.ReportResponses.CreateReportResponse;
import com.vyttah.goaml.model.dto.report.ReportResponses.ReportView;
import com.vyttah.goaml.service.report.ReportPreview;
import com.vyttah.goaml.service.report.ReportResult;
import com.vyttah.goaml.service.report.ReportService;
import com.vyttah.goaml.service.report.ReportValidationResult;
import jakarta.validation.Validator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * MCP tools for the DPMSR report lifecycle up to (but not including) submission — validate, preview the XML,
 * create a draft, and read stored reports. Each delegates to {@link ReportService} (the same path the REST
 * controller uses) and runs as the caller's tenant + role: tenant comes from {@link McpIdentity}, and RBAC
 * mirrors {@code ReportController} (create/validate/preview = ANALYST or MLRO; read = +TENANT_ADMIN). The
 * irreversible submit/delete tools are added in a later step (12.4) behind the safety harness.
 *
 * <p>Requests are bean-validated first (mirroring the REST {@code @Valid}); constraint violations are
 * returned as structured {@code CONSTRAINT} messages rather than throwing, so the agent can fix and retry.
 */
@Component
public class ReportTools {

    private final ReportService reportService;
    private final Validator validator;

    public ReportTools(ReportService reportService, Validator validator) {
        this.reportService = reportService;
        this.validator = validator;
    }

    @Tool(name = "goaml_validate_dpmsr",
            description = "Validate a DPMSR report (UAE precious-metals dealer) WITHOUT saving it. Returns "
                    + "VALID/INVALID plus every business-rule and XSD message by path/code, so you can fix a "
                    + "draft before creating it. Requires the ANALYST, MLRO, or TENANT_ADMIN role.")
    public ReportValidationResult validateDpmsr(DpmsrCreateRequest request) {
        McpIdentity.Identity identity = McpIdentity.requireAnyRole("ANALYST", "MLRO", "TENANT_ADMIN");
        List<ValidationMessage> violations = beanViolations(request);
        if (!violations.isEmpty()) {
            return new ReportValidationResult("INVALID", violations);
        }
        return reportService.validate(request, identity.tenantId());
    }

    @Tool(name = "goaml_preview_dpmsr_xml",
            description = "Build a DPMSR report to the exact goAML XML that WOULD be submitted, WITHOUT saving "
                    + "or submitting it, plus the validation verdict. Use this to inspect the XML. Requires the "
                    + "ANALYST, MLRO, or TENANT_ADMIN role.")
    public ReportPreview previewDpmsrXml(DpmsrCreateRequest request) {
        McpIdentity.Identity identity = McpIdentity.requireAnyRole("ANALYST", "MLRO", "TENANT_ADMIN");
        List<ValidationMessage> violations = beanViolations(request);
        if (!violations.isEmpty()) {
            return new ReportPreview("INVALID", null, violations);
        }
        return reportService.previewXml(request, identity.tenantId());
    }

    @Tool(name = "goaml_create_dpmsr",
            description = "Build, validate, and SAVE a DPMSR report as a draft for the current tenant (does NOT "
                    + "submit it to the FIU). Returns the new report id, status, and validation messages. The "
                    + "entity_reference must be unique per tenant (idempotency key). Requires ANALYST, MLRO, or "
                    + "TENANT_ADMIN.")
    public CreateReportResponse createDpmsr(DpmsrCreateRequest request) {
        McpIdentity.Identity identity = McpIdentity.requireAnyRole("ANALYST", "MLRO", "TENANT_ADMIN");
        List<ValidationMessage> violations = beanViolations(request);
        if (!violations.isEmpty()) {
            return new CreateReportResponse(null, "INVALID", violations);
        }
        ReportResult result = reportService.create(request, identity.tenantId(), identity.userId());
        return CreateReportResponse.from(result);
    }

    @Tool(name = "goaml_list_reports",
            description = "List the stored reports for the current tenant (id, entity_reference, code, status, "
                    + "created date). Read-only. Requires ANALYST, MLRO, or TENANT_ADMIN.")
    public List<ReportView> listReports() {
        McpIdentity.requireAnyRole("ANALYST", "MLRO", "TENANT_ADMIN");
        return reportService.list().stream().map(ReportView::from).toList();
    }

    @Tool(name = "goaml_get_report",
            description = "Get one stored report by its id (id, entity_reference, code, status, created date). "
                    + "Read-only. Requires ANALYST, MLRO, or TENANT_ADMIN.")
    public ReportView getReport(
            @ToolParam(description = "The report's UUID, as returned by goaml_create_dpmsr / goaml_list_reports.")
            String reportId) {
        McpIdentity.requireAnyRole("ANALYST", "MLRO", "TENANT_ADMIN");
        return ReportView.from(reportService.get(parseUuid(reportId)));
    }

    private List<ValidationMessage> beanViolations(DpmsrCreateRequest request) {
        return validator.validate(request).stream()
                .map(v -> new ValidationMessage(Severity.ERROR, v.getPropertyPath().toString(),
                        "CONSTRAINT", v.getMessage()))
                .toList();
    }

    private static UUID parseUuid(String reportId) {
        try {
            return UUID.fromString(reportId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("reportId must be a UUID, got '" + reportId + "'");
        }
    }
}
