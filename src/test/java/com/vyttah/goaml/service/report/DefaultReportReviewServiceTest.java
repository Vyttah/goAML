package com.vyttah.goaml.service.report;

import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.service.audit.AuditService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultReportReviewService} — focused on the A5 segregation-of-duties check on
 * {@code approve()} (collaborators mocked; no DB).
 */
class DefaultReportReviewServiceTest {

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final TenantGoamlConfigRepository configRepository = mock(TenantGoamlConfigRepository.class);
    private final AuditService auditService = mock(AuditService.class);

    private final DefaultReportReviewService service =
            new DefaultReportReviewService(reportRepository, configRepository, auditService);

    private final UUID tenantId = UUID.randomUUID();

    private Report pendingReview(UUID author) {
        Report r = new Report(UUID.randomUUID(), "REV-1", "DPMSR", 3177, "PENDING_REVIEW", "{}", author);
        return r;
    }

    @Test
    void approveRejectsWhenApproverIsTheAuthor() {
        UUID author = UUID.randomUUID();
        Report report = pendingReview(author);
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.approve(report.getId(), tenantId, author, "self-approve"))
                .isInstanceOf(ReportExceptions.SelfApprovalNotAllowedException.class);
        assertThat(report.getStatus()).isEqualTo("PENDING_REVIEW"); // unchanged
        verify(reportRepository, never()).save(any());
    }

    @Test
    void approveSucceedsWhenApproverDiffersFromAuthor() {
        UUID author = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        Report report = pendingReview(author);
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        var result = service.approve(report.getId(), tenantId, approver, "looks good");

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(report.getStatus()).isEqualTo("APPROVED");
        assertThat(report.getReviewedBy()).isEqualTo(approver);
        verify(reportRepository).save(report);
    }
}
