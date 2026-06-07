package com.vyttah.goaml.mcp.tool;

import com.vyttah.goaml.b2b.ReportStatus;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.mcp.McpAccessDeniedException;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.security.UserPrincipal;
import com.vyttah.goaml.service.report.ReportService;
import com.vyttah.goaml.service.submission.SubmissionExceptions;
import com.vyttah.goaml.service.submission.SubmissionResult;
import com.vyttah.goaml.service.submission.SubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
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
 * Unit tests for {@link SubmissionTools} — the safety harness. Proves the layered guards on
 * {@code goaml_submit_report}: MLRO-only, dry-run-by-default, confirmation-required, validate-first, and the
 * structured mapping of FIU rejection / transport failures. The {@link SubmissionService} is never called
 * unless every guard passes.
 */
class SubmissionToolsTest {

    private final ReportService reportService = mock(ReportService.class);
    private final SubmissionService submissionService = mock(SubmissionService.class);
    private final SubmissionTools tools = new SubmissionTools(reportService, submissionService);

    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID reportId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticate(List<String> roles) {
        UserPrincipal principal = new UserPrincipal(USER_ID, TENANT_ID, "mlro@demo.local", "", true, roles);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        TenantContext.set("tenant_demo");
    }

    private Report validReport() {
        Report r = new Report(reportId, "PAY-1", "DPMSR", 3177, "VALID", "{}", USER_ID);
        r.setReportXml("<report><report_code>DPMSR</report_code></report>");
        return r;
    }

    // ---------- submit: RBAC ----------

    @Test
    void submitRequiresMlro() {
        authenticate(List.of("ANALYST"));

        assertThatThrownBy(() -> tools.submitReport(reportId.toString(), null, null))
                .isInstanceOf(McpAccessDeniedException.class);
        verify(reportService, never()).get(any());
        verify(submissionService, never()).submit(any(), any(), any());
    }

    // ---------- submit: confirmation gate (before any lookup) ----------

    @Test
    void realSubmitWithoutConfirmationIsRefusedWithoutLookup() {
        authenticate(List.of("MLRO"));

        SubmissionTools.SubmitResult result = tools.submitReport(reportId.toString(), false, false);

        assertThat(result.submitted()).isFalse();
        assertThat(result.message()).contains("without explicit confirmation");
        verify(reportService, never()).get(any());
        verify(submissionService, never()).submit(any(), any(), any());
    }

    // ---------- submit: dry run (default) ----------

    @Test
    void dryRunByDefaultPreviewsXmlAndSendsNothing() {
        authenticate(List.of("MLRO"));
        when(reportService.get(reportId)).thenReturn(validReport());

        SubmissionTools.SubmitResult result = tools.submitReport(reportId.toString(), null, null);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.submitted()).isFalse();
        assertThat(result.xmlPreview()).contains("<report_code>DPMSR</report_code>");
        assertThat(result.message()).contains("DRY RUN");
        verify(submissionService, never()).submit(any(), any(), any());
    }

    @Test
    void dryRunOnNonValidReportRefusesWithStatus() {
        authenticate(List.of("MLRO"));
        Report submitted = validReport();
        submitted.setStatus("SUBMITTED");
        when(reportService.get(reportId)).thenReturn(submitted);

        SubmissionTools.SubmitResult result = tools.submitReport(reportId.toString(), null, null);

        assertThat(result.submitted()).isFalse();
        assertThat(result.reportStatus()).isEqualTo("SUBMITTED");
        assertThat(result.message()).contains("only VALID reports");
        verify(submissionService, never()).submit(any(), any(), any());
    }

    // ---------- submit: real (confirmed) ----------

    @Test
    void confirmedSubmitOfValidReportFilesAndReturnsKey() {
        authenticate(List.of("MLRO"));
        when(reportService.get(reportId)).thenReturn(validReport());
        when(submissionService.submit(eq(reportId), eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(new SubmissionResult(UUID.randomUUID(), "RK-9", "SUBMITTED"));

        SubmissionTools.SubmitResult result = tools.submitReport(reportId.toString(), false, true);

        assertThat(result.submitted()).isTrue();
        assertThat(result.reportKey()).isEqualTo("RK-9");
        assertThat(result.reportStatus()).isEqualTo("SUBMITTED");
        verify(submissionService).submit(reportId, TENANT_ID, USER_ID);
    }

    @Test
    void confirmedSubmitMapsFiuRejectionToStructuredResult() {
        authenticate(List.of("MLRO"));
        when(reportService.get(reportId)).thenReturn(validReport());
        when(submissionService.submit(any(), any(), any()))
                .thenThrow(new SubmissionExceptions.SubmissionRejectedException("rejected", "<error>bad date</error>"));

        SubmissionTools.SubmitResult result = tools.submitReport(reportId.toString(), false, true);

        assertThat(result.submitted()).isFalse();
        assertThat(result.reportStatus()).isEqualTo("REJECTED");
        assertThat(result.message()).contains("REJECTED").contains("bad date");
    }

    @Test
    void confirmedSubmitMapsTransportFailureToStructuredResult() {
        authenticate(List.of("MLRO"));
        when(reportService.get(reportId)).thenReturn(validReport());
        when(submissionService.submit(any(), any(), any()))
                .thenThrow(new SubmissionExceptions.SubmissionTransportException("boom", new RuntimeException()));

        SubmissionTools.SubmitResult result = tools.submitReport(reportId.toString(), false, true);

        assertThat(result.submitted()).isFalse();
        assertThat(result.reportStatus()).isEqualTo("FAILED");
        assertThat(result.message()).contains("retry");
    }

    // ---------- status ----------

    @Test
    void getFiuStatusDelegates() {
        authenticate(List.of("ANALYST"));
        when(submissionService.refreshStatus(reportId, TENANT_ID))
                .thenReturn(new ReportStatus("RK-1", "ACCEPTED", null));

        SubmissionTools.FiuStatusResult result = tools.getFiuStatus(reportId.toString());

        assertThat(result.reportKey()).isEqualTo("RK-1");
        assertThat(result.status()).isEqualTo("ACCEPTED");
    }

    @Test
    void getFiuStatusDeniedWithoutAReportRole() {
        authenticate(List.of("SUPER_ADMIN"));

        assertThatThrownBy(() -> tools.getFiuStatus(reportId.toString()))
                .isInstanceOf(McpAccessDeniedException.class);
    }

    // ---------- post message ----------

    @Test
    void postMessageRequiresMlro() {
        authenticate(List.of("ANALYST"));

        assertThatThrownBy(() -> tools.postMessage("hi", true))
                .isInstanceOf(McpAccessDeniedException.class);
        verify(submissionService, never()).postMessage(any(), any(), any());
    }

    @Test
    void postMessageWithoutConfirmationIsRefused() {
        authenticate(List.of("MLRO"));

        SubmissionTools.MessageResult result = tools.postMessage("hi", null);

        assertThat(result.sent()).isFalse();
        verify(submissionService, never()).postMessage(any(), any(), any());
    }

    @Test
    void confirmedPostMessageSends() {
        authenticate(List.of("MLRO"));
        when(submissionService.postMessage(eq("hi"), eq(TENANT_ID), eq(USER_ID))).thenReturn("OK");

        SubmissionTools.MessageResult result = tools.postMessage("hi", true);

        assertThat(result.sent()).isTrue();
        assertThat(result.response()).isEqualTo("OK");
        verify(submissionService).postMessage("hi", TENANT_ID, USER_ID);
    }
}
