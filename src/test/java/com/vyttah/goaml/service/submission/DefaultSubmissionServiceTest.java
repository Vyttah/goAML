package com.vyttah.goaml.service.submission;

import com.vyttah.goaml.b2b.GoamlB2bClient;
import com.vyttah.goaml.b2b.ReportStatus;
import com.vyttah.goaml.b2b.error.B2bTransportException;
import com.vyttah.goaml.b2b.error.B2bValidationException;
import com.vyttah.goaml.engine.packaging.ReportZipPackager;
import com.vyttah.goaml.integration.aws.S3StorageClient;
import com.vyttah.goaml.model.entity.attachment.Attachment;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.model.entity.submission.Submission;
import com.vyttah.goaml.repository.attachment.AttachmentRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.repository.submission.SubmissionRepository;
import com.vyttah.goaml.service.audit.AuditService;
import com.vyttah.goaml.service.report.ReportExceptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultSubmissionService}: repos + b2b client + audit mocked, real
 * {@link ReportZipPackager}. Covers submit success, the state/config guards, and the FIU error→status mapping.
 */
class DefaultSubmissionServiceTest {

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
    private final TenantGoamlConfigRepository configRepository = mock(TenantGoamlConfigRepository.class);
    private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
    private final GoamlB2bClient b2bClient = mock(GoamlB2bClient.class);
    private final S3StorageClient s3StorageClient = mock(S3StorageClient.class);
    private final AuditService auditService = mock(AuditService.class);

    private final DefaultSubmissionService service = new DefaultSubmissionService(
            reportRepository, submissionRepository, configRepository, attachmentRepository, b2bClient,
            new ReportZipPackager(), s3StorageClient, auditService);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private Report validReport() {
        Report r = new Report(UUID.randomUUID(), "PAY-1", "DPMSR", 3177, "VALID", "{}", actor);
        r.setReportXml("<report><report_code>DPMSR</report_code></report>");
        return r;
    }

    private void stubConfig() {
        TenantGoamlConfig config = mock(TenantGoamlConfig.class);
        when(config.getBaseUrl()).thenReturn("https://goaml.test/uae");
        when(config.getSecretsPath()).thenReturn("goaml/t1/creds");
        when(config.getAuthMode()).thenReturn("TOKEN");
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(config));
    }

    @Test
    void submitValidReportStoresReportKeyAndMarksSubmitted() {
        Report report = validReport();
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        stubConfig();
        when(b2bClient.postReport(any(), any(), any())).thenReturn("RK-1");

        SubmissionResult result = service.submit(report.getId(), tenantId, actor);

        assertThat(result.reportKey()).isEqualTo("RK-1");
        assertThat(result.status()).isEqualTo("SUBMITTED");
        assertThat(report.getStatus()).isEqualTo("SUBMITTED");
        ArgumentCaptor<Submission> sub = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(sub.capture());
        assertThat(sub.getValue().getReportkey()).isEqualTo("RK-1");
        assertThat(sub.getValue().getStatus()).isEqualTo("SUBMITTED");
        verify(auditService).record(any(), any(), any(), any(), any());
    }

    @Test
    void nonValidReportNotSubmittable() {
        Report report = validReport();
        report.setStatus("INVALID");
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.submit(report.getId(), tenantId, actor))
                .isInstanceOf(SubmissionExceptions.ReportNotSubmittableException.class);
        verify(b2bClient, never()).postReport(any(), any(), any());
    }

    @Test
    void missingReportThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(reportRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(id, tenantId, actor))
                .isInstanceOf(ReportExceptions.ReportNotFoundException.class);
    }

    @Test
    void missingTenantConfigThrows() {
        Report report = validReport();
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(report.getId(), tenantId, actor))
                .isInstanceOf(SubmissionExceptions.TenantConfigMissingException.class);
    }

    @Test
    void fiuRejectionSavesFailedAndThrows() {
        Report report = validReport();
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        stubConfig();
        when(b2bClient.postReport(any(), any(), any()))
                .thenThrow(new B2bValidationException("rejected", "<error>bad date</error>"));

        assertThatThrownBy(() -> service.submit(report.getId(), tenantId, actor))
                .isInstanceOf(SubmissionExceptions.SubmissionRejectedException.class)
                .satisfies(e -> assertThat(((SubmissionExceptions.SubmissionRejectedException) e).responseBody())
                        .contains("bad date"));

        ArgumentCaptor<Submission> sub = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(sub.capture());
        assertThat(sub.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(report.getStatus()).isEqualTo("VALID"); // unchanged — fixable
    }

    @Test
    void transportFailureSavesFailedAndThrows() {
        Report report = validReport();
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        stubConfig();
        when(b2bClient.postReport(any(), any(), any())).thenThrow(new B2bTransportException("boom"));

        assertThatThrownBy(() -> service.submit(report.getId(), tenantId, actor))
                .isInstanceOf(SubmissionExceptions.SubmissionTransportException.class);
    }

    @Test
    void refreshStatusMapsRejected() {
        Report report = validReport();
        report.setStatus("SUBMITTED");
        Submission submission = new Submission(UUID.randomUUID(), report.getId(), "SUBMITTED");
        submission.setReportkey("RK-1");
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(submissionRepository.findByReportIdOrderBySubmittedAtDesc(report.getId()))
                .thenReturn(List.of(submission));
        stubConfig();
        when(b2bClient.getReportStatus(any(), any()))
                .thenReturn(new ReportStatus("RK-1", "Rejected", "bad field"));

        service.refreshStatus(report.getId(), tenantId);

        assertThat(submission.getStatus()).isEqualTo("REJECTED");
        assertThat(report.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    void refreshStatusUnknownStatusStaysSubmitted() {
        Report report = validReport();
        report.setStatus("SUBMITTED");
        Submission submission = new Submission(UUID.randomUUID(), report.getId(), "SUBMITTED");
        submission.setReportkey("RK-1");
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(submissionRepository.findByReportIdOrderBySubmittedAtDesc(report.getId()))
                .thenReturn(List.of(submission));
        stubConfig();
        when(b2bClient.getReportStatus(any(), any())).thenReturn(new ReportStatus("RK-1", null, null));

        service.refreshStatus(report.getId(), tenantId);

        assertThat(submission.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void refreshStatusWithNoSubmissionThrows() {
        Report report = validReport();
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(submissionRepository.findByReportIdOrderBySubmittedAtDesc(report.getId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.refreshStatus(report.getId(), tenantId))
                .isInstanceOf(SubmissionExceptions.ReportNotSubmittableException.class);
    }

    @Test
    void refreshStatusMapsAcceptedAndPersists() {
        Report report = validReport();
        report.setStatus("SUBMITTED");
        Submission submission = new Submission(UUID.randomUUID(), report.getId(), "SUBMITTED");
        submission.setReportkey("RK-1");
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(submissionRepository.findByReportIdOrderBySubmittedAtDesc(report.getId()))
                .thenReturn(List.of(submission));
        stubConfig();
        when(b2bClient.getReportStatus(any(), any())).thenReturn(new ReportStatus("RK-1", "Accepted", null));

        ReportStatus status = service.refreshStatus(report.getId(), tenantId);

        assertThat(status.status()).isEqualTo("Accepted");
        assertThat(submission.getStatus()).isEqualTo("ACCEPTED");
        assertThat(report.getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    void submitPullsAttachmentsFromS3IntoTheZip() throws Exception {
        Report report = validReport();
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        stubConfig();
        when(b2bClient.postReport(any(), any(), any())).thenReturn("RK-1");

        Attachment att = new Attachment(UUID.randomUUID(), report.getId(), "invoice.pdf",
                "application/pdf", 5L, "k1", actor);
        when(attachmentRepository.findByReportIdOrderByCreatedAt(report.getId())).thenReturn(List.of(att));
        when(s3StorageClient.fetch("k1")).thenReturn("PDF!".getBytes(StandardCharsets.UTF_8));

        service.submit(report.getId(), tenantId, actor);

        ArgumentCaptor<byte[]> zip = ArgumentCaptor.forClass(byte[].class);
        verify(b2bClient).postReport(any(), zip.capture(), any());
        assertThat(entryNames(zip.getValue()))
                .contains("PAY-1.xml")
                .contains("invoice.pdf");
    }

    @Test
    void submitFailsWhenAttachmentsExceedPackagingLimits() {
        Report report = validReport();
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        stubConfig();

        // a single 6 MB file > the 5 MB per-file limit → the packager rejects it at submit
        Attachment big = new Attachment(UUID.randomUUID(), report.getId(), "big.pdf",
                "application/pdf", 6L * 1024 * 1024, "k1", actor);
        when(attachmentRepository.findByReportIdOrderByCreatedAt(report.getId())).thenReturn(List.of(big));
        when(s3StorageClient.fetch("k1")).thenReturn(new byte[6 * 1024 * 1024]);

        assertThatThrownBy(() -> service.submit(report.getId(), tenantId, actor))
                .isInstanceOf(SubmissionExceptions.SubmissionPackagingException.class);

        verify(b2bClient, never()).postReport(any(), any(), any());
        assertThat(report.getStatus()).isEqualTo("VALID"); // unchanged — fix is to drop the attachment
        verify(submissionRepository, never()).save(any());
    }

    private static List<String> entryNames(byte[] zipBytes) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                names.add(e.getName());
            }
        }
        return names;
    }
}
