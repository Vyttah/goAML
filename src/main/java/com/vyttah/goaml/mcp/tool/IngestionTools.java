package com.vyttah.goaml.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.mcp.McpIdentity;
import com.vyttah.goaml.model.dto.ingestion.ImportJobView;
import com.vyttah.goaml.service.ingestion.IngestionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * MCP tools for file import (Phase 11 ingestion): bulk-create reports from a goAML XML file or a flat DPMSR
 * CSV. Each row is created via the same engine path as a single report, with per-row results returned. Thin
 * over {@link IngestionService}; RBAC mirrors {@code ImportController} (import = ANALYST/MLRO; reads also
 * TENANT_ADMIN). The file content is passed as text (these formats are text); it is UTF-8 encoded to bytes.
 */
@Component
public class IngestionTools {

    private final IngestionService ingestionService;
    private final ObjectMapper objectMapper;

    public IngestionTools(IngestionService ingestionService, ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "goaml_import_xml",
            description = "Import a goAML XML file: each report in it is unmarshalled, validated, and stored as "
                    + "a draft. Returns a job with per-row results (status + errors). Does NOT submit. "
                    + "Requires ANALYST, MLRO, or TENANT_ADMIN.")
    public ImportJobView importXml(
            @ToolParam(description = "The original filename, e.g. 'batch.xml'.") String filename,
            @ToolParam(description = "The full goAML XML content to import.") String xmlContent) {
        McpIdentity.Identity identity = McpIdentity.requireAnyRole("ANALYST", "MLRO", "TENANT_ADMIN");
        var job = ingestionService.importXml(xmlContent.getBytes(StandardCharsets.UTF_8), filename,
                identity.tenantId(), identity.userId());
        return ImportJobView.from(job, objectMapper);
    }

    @Tool(name = "goaml_import_csv",
            description = "Import a flat DPMSR CSV (one report per row): each row maps to a DPMSR request, is "
                    + "validated, and stored as a draft. Returns a job with per-row results. Does NOT submit. "
                    + "Requires ANALYST, MLRO, or TENANT_ADMIN.")
    public ImportJobView importCsv(
            @ToolParam(description = "The original filename, e.g. 'dpmsr.csv'.") String filename,
            @ToolParam(description = "The full DPMSR CSV content (with the header row).") String csvContent) {
        McpIdentity.Identity identity = McpIdentity.requireAnyRole("ANALYST", "MLRO", "TENANT_ADMIN");
        var job = ingestionService.importCsv(csvContent.getBytes(StandardCharsets.UTF_8), filename,
                identity.tenantId(), identity.userId());
        return ImportJobView.from(job, objectMapper);
    }

    @Tool(name = "goaml_list_imports",
            description = "List this tenant's import jobs (id, source type, filename, counts, status). "
                    + "Read-only. Requires ANALYST, MLRO, or TENANT_ADMIN.")
    public List<ImportJobView> listImports() {
        McpIdentity.requireAnyRole("ANALYST", "MLRO", "TENANT_ADMIN");
        return ingestionService.list().stream().map(j -> ImportJobView.from(j, objectMapper)).toList();
    }

    @Tool(name = "goaml_get_import",
            description = "Get one import job by id, including its per-row results. Read-only. Requires "
                    + "ANALYST, MLRO, or TENANT_ADMIN.")
    public ImportJobView getImport(
            @ToolParam(description = "The import job's UUID.") String jobId) {
        McpIdentity.requireAnyRole("ANALYST", "MLRO", "TENANT_ADMIN");
        return ImportJobView.from(ingestionService.get(parseUuid(jobId)), objectMapper);
    }

    private static UUID parseUuid(String jobId) {
        try {
            return UUID.fromString(jobId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("jobId must be a UUID, got '" + jobId + "'");
        }
    }
}
