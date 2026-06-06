package com.vyttah.goaml.service.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.entity.ingestion.ImportJob;
import com.vyttah.goaml.repository.ingestion.ImportJobRepository;
import com.vyttah.goaml.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Default {@link IngestionService}. Delegates the per-row work to {@link GoamlXmlImporter}/{@link CsvImporter},
 * then records the outcome as a single {@link ImportJob} (tallies + the results JSONB) and audits it. Runs
 * under the caller's bound tenant; each {@code save} commits independently (like {@code DefaultReportService}).
 */
@Service
@RequiredArgsConstructor
public class DefaultIngestionService implements IngestionService {

    private static final String SOURCE_XML = "GOAML_XML";
    private static final String SOURCE_CSV = "DPMSR_CSV";

    private final GoamlXmlImporter xmlImporter;
    private final CsvImporter csvImporter;
    private final ImportJobRepository importJobRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Override
    public ImportJob importXml(byte[] xml, String filename, UUID tenantId, UUID actorUserId) {
        ImportRowResult result = xmlImporter.importXml(xml, filename, tenantId, actorUserId);
        return persistJob(SOURCE_XML, filename, List.of(result), actorUserId);
    }

    @Override
    public ImportJob importCsv(byte[] csv, String filename, UUID tenantId, UUID actorUserId) {
        // ImportRejectedException (unreadable / missing headers / over cap) propagates → 400, no job row.
        List<ImportRowResult> results = csvImporter.importCsv(csv, tenantId, actorUserId);
        return persistJob(SOURCE_CSV, filename, results, actorUserId);
    }

    @Override
    public List<ImportJob> list() {
        return importJobRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public ImportJob get(UUID jobId) {
        return importJobRepository.findById(jobId)
                .orElseThrow(() -> new IngestionExceptions.ImportJobNotFoundException(
                        "Import job not found: " + jobId));
    }

    private ImportJob persistJob(String sourceType, String filename, List<ImportRowResult> results,
                                 UUID actorUserId) {
        int succeeded = (int) results.stream().filter(ImportRowResult::reportCreated).count();
        int failed = results.size() - succeeded;
        String status = failed == 0 ? "COMPLETED" : (succeeded == 0 ? "FAILED" : "PARTIAL");

        ImportJob job = new ImportJob(UUID.randomUUID(), sourceType, filename, actorUserId);
        job.setStatus(status);
        job.setTotalRows(results.size());
        job.setSucceeded(succeeded);
        job.setFailed(failed);
        job.setResults(toJson(results));
        importJobRepository.save(job);

        auditService.record("REPORT.IMPORT", actorUserId, null, TenantContext.get(),
                sourceType + " " + (filename == null ? "" : filename) + " -> " + status
                        + " (" + succeeded + "/" + results.size() + " created)");
        return job;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize import results", e);
        }
    }
}
