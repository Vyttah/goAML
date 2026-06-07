package com.vyttah.goaml.cli.command;

import com.vyttah.goaml.cli.CliAuthenticator;
import com.vyttah.goaml.cli.GoamlCli;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.service.report.ReportService;
import com.vyttah.goaml.service.submission.SubmissionResult;
import com.vyttah.goaml.service.submission.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link SubmitCommand} (and the {@link AbstractGoamlCommand} flow): proves the CLI carries the
 * same safety harness as the MCP submit tool — dry-run by default, real send needs {@code --send --confirm},
 * only VALID submits — with the right exit codes. Auth is mocked; the context is always cleared.
 */
class SubmitCommandTest {

    private final CliAuthenticator authenticator = mock(CliAuthenticator.class);
    private final GoamlCli root = mock(GoamlCli.class);
    private final ReportService reportService = mock(ReportService.class);
    private final SubmissionService submissionService = mock(SubmissionService.class);

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();

    private SubmitCommand command;

    @BeforeEach
    void setUp() {
        when(root.requireToken()).thenReturn("tok");
        when(authenticator.authenticate("tok")).thenReturn(
                new CliAuthenticator.CliPrincipal(USER_ID, TENANT_ID, "mlro@demo.local", List.of("MLRO")));
        command = new SubmitCommand(authenticator, reportService, submissionService);
        command.root = root;
        command.reportId = reportId.toString();
    }

    private Report validReport() {
        Report r = new Report(reportId, "PAY-1", "DPMSR", 3177, "VALID", "{}", USER_ID);
        r.setReportXml("<report/>");
        return r;
    }

    @Test
    void dryRunByDefaultSendsNothing() throws Exception {
        when(reportService.get(reportId)).thenReturn(validReport());

        int code = command.call();

        assertThat(code).isZero();
        verify(submissionService, never()).submit(any(), any(), any());
        verify(authenticator).clear();
    }

    @Test
    void sendWithoutConfirmIsRefused() throws Exception {
        command.send = true;

        int code = command.call();

        assertThat(code).isEqualTo(2);
        verify(reportService, never()).get(any());
        verify(submissionService, never()).submit(any(), any(), any());
    }

    @Test
    void nonValidReportIsNotSubmittable() throws Exception {
        command.send = true;
        command.confirm = true;
        Report invalid = validReport();
        invalid.setStatus("SUBMITTED");
        when(reportService.get(reportId)).thenReturn(invalid);

        int code = command.call();

        assertThat(code).isEqualTo(1);
        verify(submissionService, never()).submit(any(), any(), any());
    }

    @Test
    void confirmedSendFiles() throws Exception {
        command.send = true;
        command.confirm = true;
        when(reportService.get(reportId)).thenReturn(validReport());
        when(submissionService.submit(eq(reportId), eq(TENANT_ID), eq(USER_ID)))
                .thenReturn(new SubmissionResult(UUID.randomUUID(), "RK-1", "SUBMITTED"));

        int code = command.call();

        assertThat(code).isZero();
        verify(submissionService).submit(reportId, TENANT_ID, USER_ID);
    }
}
