package com.vyttah.goaml.repository.attachment;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.attachment.Attachment;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.report.ReportRepository;
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
 * Phase 8.2 persistence proof (Testcontainers): the tenant `attachment` table exists and round-trips
 * (metadata + S3 key), the repository queries work, and the FK to `report` cascades on delete.
 */
@SpringBootTest(classes = GoamlApplication.class)
@Testcontainers
class AttachmentPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TenantProvisioningService provisioningService;
    @Autowired ReportRepository reportRepository;
    @Autowired AttachmentRepository attachmentRepository;

    @Test
    void attachmentRoundTripsAndQueriesByReport() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "att-a", "Attachment Tenant A", "AE", "att-a@test", "P@ssw0rd!", "Att", "A"));

        UUID reportId = UUID.randomUUID();
        UUID attId = UUID.randomUUID();
        runAsTenant(tenant.getSchemaName(), () -> {
            reportRepository.saveAndFlush(new Report(reportId, "ATT-REF-001", "DPMSR", 3177,
                    "VALID", "{}", null));
            attachmentRepository.saveAndFlush(new Attachment(attId, reportId, "invoice.pdf",
                    "application/pdf", 2048L,
                    "tenants/" + tenant.getId() + "/reports/" + reportId + "/" + attId + "-invoice.pdf",
                    UUID.randomUUID()));
            return null;
        });

        runAsTenant(tenant.getSchemaName(), () -> {
            List<Attachment> list = attachmentRepository.findByReportIdOrderByCreatedAt(reportId);
            assertThat(list).hasSize(1);
            Attachment a = list.get(0);
            assertThat(a.getFilename()).isEqualTo("invoice.pdf");
            assertThat(a.getContentType()).isEqualTo("application/pdf");
            assertThat(a.getSizeBytes()).isEqualTo(2048L);
            assertThat(a.getS3Key()).contains("/reports/" + reportId + "/").endsWith("-invoice.pdf");

            assertThat(attachmentRepository.findByIdAndReportId(attId, reportId)).isPresent();
            assertThat(attachmentRepository.findByIdAndReportId(attId, UUID.randomUUID())).isEmpty();
            return null;
        });
    }

    @Test
    void deletingReportCascadesToAttachments() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "att-b", "Attachment Tenant B", "AE", "att-b@test", "P@ssw0rd!", "Att", "B"));

        UUID reportId = UUID.randomUUID();
        runAsTenant(tenant.getSchemaName(), () -> {
            reportRepository.saveAndFlush(new Report(reportId, "ATT-REF-002", "DPMSR", 3177,
                    "VALID", "{}", null));
            attachmentRepository.saveAndFlush(new Attachment(UUID.randomUUID(), reportId, "id.png",
                    "image/png", 1024L, "tenants/x/reports/" + reportId + "/id.png", null));

            assertThat(attachmentRepository.findByReportIdOrderByCreatedAt(reportId)).hasSize(1);

            reportRepository.deleteById(reportId);
            reportRepository.flush();

            assertThat(attachmentRepository.findByReportIdOrderByCreatedAt(reportId)).isEmpty();
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
