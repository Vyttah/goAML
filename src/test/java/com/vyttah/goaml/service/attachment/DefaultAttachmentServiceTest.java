package com.vyttah.goaml.service.attachment;

import com.vyttah.goaml.integration.aws.S3StorageClient;
import com.vyttah.goaml.model.entity.attachment.Attachment;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.repository.attachment.AttachmentRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.service.audit.AuditService;
import com.vyttah.goaml.service.report.ReportExceptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultAttachmentService}: repos + S3 client + audit mocked. Covers add (success +
 * every validation/guard reject), list, and remove (success + not-found + frozen).
 */
class DefaultAttachmentServiceTest {

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
    private final S3StorageClient s3StorageClient = mock(S3StorageClient.class);
    private final AuditService auditService = mock(AuditService.class);

    private final DefaultAttachmentService service = new DefaultAttachmentService(
            reportRepository, attachmentRepository, s3StorageClient, auditService);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private Report report(String status) {
        return new Report(UUID.randomUUID(), "PAY-1", "DPMSR", 3177, status, "{}", actor);
    }

    private byte[] pdf() {
        return "pretend-pdf".getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void addStoresInS3AndPersistsRow() {
        Report r = report("VALID");
        when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));

        Attachment saved = service.add(r.getId(), tenantId, actor, "invoice.pdf", "application/pdf", pdf());

        assertThat(saved.getFilename()).isEqualTo("invoice.pdf");
        assertThat(saved.getSizeBytes()).isEqualTo(pdf().length);
        assertThat(saved.getS3Key())
                .startsWith("tenants/" + tenantId + "/reports/" + r.getId() + "/")
                .endsWith("-invoice.pdf");

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(s3StorageClient).put(eq(saved.getS3Key()), bytes.capture(), eq("application/pdf"));
        assertThat(bytes.getValue()).isEqualTo(pdf());
        verify(attachmentRepository).save(any(Attachment.class));
        verify(auditService).record(eq("ATTACHMENT.ADD"), eq(actor), any(), any(), any());
    }

    @Test
    void addToMissingReportThrowsAndSkipsS3() {
        UUID id = UUID.randomUUID();
        when(reportRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(id, tenantId, actor, "a.pdf", "application/pdf", pdf()))
                .isInstanceOf(ReportExceptions.ReportNotFoundException.class);
        verify(s3StorageClient, never()).put(any(), any(), any());
    }

    @Test
    void addToSubmittedReportIsFrozen() {
        Report r = report("SUBMITTED");
        when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.add(r.getId(), tenantId, actor, "a.pdf", "application/pdf", pdf()))
                .isInstanceOf(AttachmentExceptions.ReportNotEditableException.class);
        verify(s3StorageClient, never()).put(any(), any(), any());
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void addRejectsBlankFilenameEmptyOversizeAndBadExtension() {
        Report r = report("DRAFT");
        when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.add(r.getId(), tenantId, actor, "  ", "application/pdf", pdf()))
                .isInstanceOf(AttachmentExceptions.AttachmentRejectedException.class);
        assertThatThrownBy(() -> service.add(r.getId(), tenantId, actor, "a.pdf", "application/pdf", new byte[0]))
                .isInstanceOf(AttachmentExceptions.AttachmentRejectedException.class);
        assertThatThrownBy(() -> service.add(r.getId(), tenantId, actor, "a.pdf", "application/pdf",
                new byte[6 * 1024 * 1024]))
                .isInstanceOf(AttachmentExceptions.AttachmentRejectedException.class);
        assertThatThrownBy(() -> service.add(r.getId(), tenantId, actor, "evil.exe", "application/octet-stream", pdf()))
                .isInstanceOf(AttachmentExceptions.AttachmentRejectedException.class);
        assertThatThrownBy(() -> service.add(r.getId(), tenantId, actor, "noext", "text/plain", pdf()))
                .isInstanceOf(AttachmentExceptions.AttachmentRejectedException.class);

        verify(s3StorageClient, never()).put(any(), any(), any());
    }

    @Test
    void listReturnsRepoResult() {
        Report r = report("VALID");
        when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));
        Attachment a = new Attachment(UUID.randomUUID(), r.getId(), "x.pdf", "application/pdf", 3L, "k", actor);
        when(attachmentRepository.findByReportIdOrderByCreatedAt(r.getId())).thenReturn(List.of(a));

        assertThat(service.list(r.getId())).containsExactly(a);
    }

    @Test
    void listMissingReportThrows() {
        UUID id = UUID.randomUUID();
        when(reportRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.list(id))
                .isInstanceOf(ReportExceptions.ReportNotFoundException.class);
    }

    @Test
    void downloadFetchesBytesFromS3WithMetadata() {
        Report r = report("SUBMITTED"); // download allowed even when frozen
        when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));
        UUID attId = UUID.randomUUID();
        Attachment a = new Attachment(attId, r.getId(), "x.pdf", "application/pdf", 3L, "the-key", actor);
        when(attachmentRepository.findByIdAndReportId(attId, r.getId())).thenReturn(Optional.of(a));
        when(s3StorageClient.fetch("the-key")).thenReturn(pdf());

        AttachmentService.AttachmentDownload dl = service.download(r.getId(), attId);

        assertThat(dl.filename()).isEqualTo("x.pdf");
        assertThat(dl.contentType()).isEqualTo("application/pdf");
        assertThat(dl.bytes()).isEqualTo(pdf());
    }

    @Test
    void downloadMissingAttachmentThrowsAndSkipsS3() {
        Report r = report("VALID");
        when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));
        UUID attId = UUID.randomUUID();
        when(attachmentRepository.findByIdAndReportId(attId, r.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(r.getId(), attId))
                .isInstanceOf(AttachmentExceptions.AttachmentNotFoundException.class);
        verify(s3StorageClient, never()).fetch(any());
    }

    @Test
    void removeDeletesFromS3AndRepo() {
        Report r = report("VALID");
        when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));
        UUID attId = UUID.randomUUID();
        Attachment a = new Attachment(attId, r.getId(), "x.pdf", "application/pdf", 3L, "the-key", actor);
        when(attachmentRepository.findByIdAndReportId(attId, r.getId())).thenReturn(Optional.of(a));

        service.remove(r.getId(), attId, actor);

        verify(s3StorageClient).delete("the-key");
        verify(attachmentRepository).delete(a);
        verify(auditService).record(eq("ATTACHMENT.REMOVE"), eq(actor), any(), any(), any());
    }

    @Test
    void removeMissingAttachmentThrows() {
        Report r = report("VALID");
        when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));
        UUID attId = UUID.randomUUID();
        when(attachmentRepository.findByIdAndReportId(attId, r.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(r.getId(), attId, actor))
                .isInstanceOf(AttachmentExceptions.AttachmentNotFoundException.class);
        verify(s3StorageClient, never()).delete(any());
    }

    @Test
    void removeOnSubmittedReportIsFrozen() {
        Report r = report("SUBMITTED");
        when(reportRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.remove(r.getId(), UUID.randomUUID(), actor))
                .isInstanceOf(AttachmentExceptions.ReportNotEditableException.class);
        verify(s3StorageClient, never()).delete(any());
    }
}
