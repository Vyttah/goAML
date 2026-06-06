package com.vyttah.goaml.repository.ingestion;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.ingestion.ImportJob;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11.1 persistence proof (Testcontainers): the tenant {@code import_job} table exists and round-trips
 * (tallies + the {@code results} JSONB array as a String), and the newest-first listing query works.
 */
@SpringBootTest(classes = GoamlApplication.class)
@Testcontainers
class ImportJobPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TenantProvisioningService provisioningService;
    @Autowired ImportJobRepository importJobRepository;

    @Test
    void importJobRoundTripsWithJsonbResults() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "imp-a", "Import Tenant A", "AE", "imp-a@test", "P@ssw0rd!", "Imp", "A"));

        UUID jobId = UUID.randomUUID();
        UUID uploader = UUID.randomUUID();
        String resultsJson = """
                [{"row":1,"entityReference":"REF-1","status":"SUCCEEDED","reportId":"%s","errors":[]},
                 {"row":2,"entityReference":"REF-2","status":"FAILED","reportId":null,"errors":["bad value"]}]
                """.formatted(UUID.randomUUID());

        runAsTenant(tenant.getSchemaName(), () -> {
            ImportJob job = new ImportJob(jobId, "DPMSR_CSV", "sales.csv", uploader);
            job.setStatus("PARTIAL");
            job.setTotalRows(2);
            job.setSucceeded(1);
            job.setFailed(1);
            job.setResults(resultsJson);
            importJobRepository.saveAndFlush(job);
            return null;
        });

        runAsTenant(tenant.getSchemaName(), () -> {
            ImportJob loaded = importJobRepository.findById(jobId).orElseThrow();
            assertThat(loaded.getSourceType()).isEqualTo("DPMSR_CSV");
            assertThat(loaded.getFilename()).isEqualTo("sales.csv");
            assertThat(loaded.getStatus()).isEqualTo("PARTIAL");
            assertThat(loaded.getTotalRows()).isEqualTo(2);
            assertThat(loaded.getSucceeded()).isEqualTo(1);
            assertThat(loaded.getFailed()).isEqualTo(1);
            assertThat(loaded.getResults()).contains("REF-1").contains("bad value");
            assertThat(loaded.getCreatedBy()).isEqualTo(uploader);
            assertThat(loaded.getCreatedAt()).isNotNull();

            List<ImportJob> all = importJobRepository.findAllByOrderByCreatedAtDesc();
            assertThat(all).extracting(ImportJob::getId).contains(jobId);
            return null;
        });
    }

    private static <T> T runAsTenant(String tenant, Supplier<T> body) {
        TenantContext.set(tenant);
        try {
            return body.get();
        } finally {
            TenantContext.clear();
        }
    }
}
