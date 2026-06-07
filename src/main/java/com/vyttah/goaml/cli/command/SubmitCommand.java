package com.vyttah.goaml.cli.command;

import com.vyttah.goaml.cli.CliAuthenticator;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.service.report.ReportService;
import com.vyttah.goaml.service.submission.SubmissionExceptions;
import com.vyttah.goaml.service.submission.SubmissionResult;
import com.vyttah.goaml.service.submission.SubmissionService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.UUID;

/**
 * {@code goaml submit <reportId>} — submit a report to the FIU, with the same safety harness as the MCP tool:
 * MLRO-only, dry-run unless {@code --send}, and a real send additionally requires {@code --confirm}; only a
 * VALID report is submittable. Exit codes: 0 success/dry-run, 1 not-submittable/FIU-rejected/transport,
 * 2 refused (no confirm).
 */
@Component
@Command(name = "submit", description = "Submit a report to the FIU (dry-run by default; --send --confirm to file).")
public class SubmitCommand extends AbstractGoamlCommand {

    private final ReportService reportService;
    private final SubmissionService submissionService;

    @Parameters(index = "0", description = "The report's UUID.")
    String reportId;

    @Option(names = "--send", description = "Perform a REAL submission (default is a dry run).")
    boolean send;

    @Option(names = "--confirm", description = "Required with --send to actually file (irreversible).")
    boolean confirm;

    public SubmitCommand(CliAuthenticator authenticator, ReportService reportService,
                         SubmissionService submissionService) {
        super(authenticator);
        this.reportService = reportService;
        this.submissionService = submissionService;
    }

    @Override
    protected String[] requiredRoles() {
        return new String[] {"MLRO"};
    }

    @Override
    protected int execute(CliAuthenticator.CliPrincipal principal) {
        if (send && !confirm) {
            System.err.println("Refusing to submit without --confirm. Filing to the FIU is irreversible.");
            return 2;
        }
        UUID id = UUID.fromString(reportId);
        Report report = reportService.get(id);
        if (!"VALID".equals(report.getStatus())) {
            System.err.println("Report " + id + " is " + report.getStatus() + " — only VALID reports submit.");
            return 1;
        }
        if (!send) {
            System.out.println(report.getReportXml());
            System.err.println("DRY RUN — nothing sent. Re-run with --send --confirm to file " + id + ".");
            return 0;
        }
        try {
            SubmissionResult result = submissionService.submit(id, principal.tenantId(), principal.userId());
            System.out.println("SUBMITTED reportKey=" + result.reportKey());
            return 0;
        } catch (SubmissionExceptions.SubmissionRejectedException e) {
            System.err.println("FIU REJECTED: " + e.responseBody());
            return 1;
        } catch (SubmissionExceptions.SubmissionTransportException e) {
            System.err.println("Submission failed (transient): " + e.getMessage());
            return 1;
        }
    }
}
