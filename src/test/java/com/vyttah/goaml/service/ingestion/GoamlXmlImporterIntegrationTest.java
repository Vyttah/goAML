package com.vyttah.goaml.service.ingestion;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11.2 round-trip (Testcontainers): a canonical goAML DPMSR XML (the engine's golden output) imports
 * back into a persisted, valid report — proving the unmarshal → validate → persist path end-to-end with the
 * real marshaller + validators. A second import of the same file is rejected as a duplicate.
 */
@SpringBootTest(classes = GoamlApplication.class)
@Testcontainers
class GoamlXmlImporterIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired GoamlXmlImporter importer;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired ReportRepository reportRepository;

    @Test
    void goldenDpmsrXmlImportsAndIsRejectedOnReimport() throws Exception {
        byte[] xml = new ClassPathResource("golden/DPMSR.xml").getContentAsByteArray();
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "imp-xml", "Import XML Tenant", "AE", "imp-xml@test", "P@ssw0rd!", "Imp", "Xml"));
        UUID actor = UUID.randomUUID();

        ImportRowResult first = runAsTenant(tenant.getSchemaName(),
                () -> importer.importXml(xml, "DPMSR.xml", tenant.getId(), actor));

        assertThat(first.status()).isEqualTo("VALID");
        assertThat(first.entityReference()).isEqualTo("DPMSR-2026-001");
        assertThat(first.reportCreated()).isTrue();

        runAsTenant(tenant.getSchemaName(), () -> {
            List<Report> reports = reportRepository.findAll();
            assertThat(reports).hasSize(1);
            Report r = reports.get(0);
            assertThat(r.getEntityReference()).isEqualTo("DPMSR-2026-001");
            assertThat(r.getReportCode()).isEqualTo("DPMSR");
            assertThat(r.getStatus()).isEqualTo("VALID");
            assertThat(r.getReportXml()).contains("<report_code>DPMSR</report_code>");
            assertThat(r.getInput()).contains("GOAML_XML_IMPORT");
            return null;
        });

        // re-import the same file → duplicate, no second report
        ImportRowResult second = runAsTenant(tenant.getSchemaName(),
                () -> importer.importXml(xml, "DPMSR.xml", tenant.getId(), actor));
        assertThat(second.status()).isEqualTo("FAILED");
        assertThat(second.errors().get(0)).contains("already exists");
        runAsTenant(tenant.getSchemaName(),
                () -> assertThat(reportRepository.findAll()).hasSize(1));
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
