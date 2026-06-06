package com.vyttah.goaml.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.model.entity.ingestion.ImportJob;
import com.vyttah.goaml.repository.ingestion.ImportJobRepository;
import com.vyttah.goaml.service.audit.AuditService;
import com.vyttah.goaml.service.ingestion.IngestionExceptions.ImportRejectedException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultIngestionService}: importers/repo/audit mocked, real {@link ObjectMapper}.
 * Covers the job tally + status derivation (COMPLETED/PARTIAL/FAILED), results serialization, the
 * rejection propagation (no job persisted), and get-not-found.
 */
class DefaultIngestionServiceTest {

    private final GoamlXmlImporter xmlImporter = mock(GoamlXmlImporter.class);
    private final CsvImporter csvImporter = mock(CsvImporter.class);
    private final ImportJobRepository repository = mock(ImportJobRepository.class);
    private final AuditService auditService = mock(AuditService.class);

    private final DefaultIngestionService service = new DefaultIngestionService(
            xmlImporter, csvImporter, repository, auditService, new ObjectMapper());

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    @Test
    void xmlCreatedRowYieldsCompletedJob() {
        when(xmlImporter.importXml(any(), anyString(), eq(tenantId), eq(actor)))
                .thenReturn(ImportRowResult.created(1, "REF", "VALID", UUID.randomUUID(), List.of()));

        ImportJob job = service.importXml("x".getBytes(), "r.xml", tenantId, actor);

        assertThat(job.getStatus()).isEqualTo("COMPLETED");
        assertThat(job.getTotalRows()).isEqualTo(1);
        assertThat(job.getSucceeded()).isEqualTo(1);
        assertThat(job.getFailed()).isZero();
        assertThat(job.getSourceType()).isEqualTo("GOAML_XML");
        verify(repository).save(any(ImportJob.class));
        verify(auditService).record(eq("REPORT.IMPORT"), eq(actor), any(), any(), anyString());
    }

    @Test
    void xmlFailedRowYieldsFailedJob() {
        when(xmlImporter.importXml(any(), anyString(), any(), any()))
                .thenReturn(ImportRowResult.failed(1, null, "Unparseable goAML XML"));

        ImportJob job = service.importXml("x".getBytes(), "r.xml", tenantId, actor);

        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getSucceeded()).isZero();
        assertThat(job.getFailed()).isEqualTo(1);
    }

    @Test
    void csvMixedRowsYieldPartialJobWithSerializedResults() {
        when(csvImporter.importCsv(any(), eq(tenantId), eq(actor))).thenReturn(List.of(
                ImportRowResult.created(1, "REF-1", "VALID", UUID.randomUUID(), List.of()),
                ImportRowResult.failed(2, "REF-2", "bad good_estimated_value")));

        ImportJob job = service.importCsv("c".getBytes(), "sales.csv", tenantId, actor);

        assertThat(job.getStatus()).isEqualTo("PARTIAL");
        assertThat(job.getTotalRows()).isEqualTo(2);
        assertThat(job.getSucceeded()).isEqualTo(1);
        assertThat(job.getFailed()).isEqualTo(1);
        assertThat(job.getSourceType()).isEqualTo("DPMSR_CSV");
        ArgumentCaptor<ImportJob> saved = ArgumentCaptor.forClass(ImportJob.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getResults()).contains("REF-1").contains("bad good_estimated_value");
    }

    @Test
    void csvRejectionPropagatesWithoutPersistingAJob() {
        when(csvImporter.importCsv(any(), any(), any()))
                .thenThrow(new ImportRejectedException("CSV is missing required columns: [good_item_type]"));

        assertThatThrownBy(() -> service.importCsv("c".getBytes(), "bad.csv", tenantId, actor))
                .isInstanceOf(ImportRejectedException.class);
        verify(repository, never()).save(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void getMissingJobThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(IngestionExceptions.ImportJobNotFoundException.class);
    }

    @Test
    void listDelegatesToRepository() {
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        service.list();
        verify(repository).findAllByOrderByCreatedAtDesc();
    }
}
