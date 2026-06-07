package com.vyttah.goaml.cli.command;

import com.vyttah.goaml.b2b.ReportStatus;
import com.vyttah.goaml.cli.CliAuthenticator;
import com.vyttah.goaml.service.submission.SubmissionService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.UUID;

/**
 * {@code goaml status <reportId>} — poll the FIU for a report's latest status and print it.
 */
@Component
@Command(name = "status", description = "Fetch a submitted report's latest FIU status.")
public class StatusCommand extends AbstractGoamlCommand {

    private final SubmissionService submissionService;

    @Parameters(index = "0", description = "The report's UUID.")
    String reportId;

    public StatusCommand(CliAuthenticator authenticator, SubmissionService submissionService) {
        super(authenticator);
        this.submissionService = submissionService;
    }

    @Override
    protected String[] requiredRoles() {
        return new String[] {"ANALYST", "MLRO", "TENANT_ADMIN"};
    }

    @Override
    protected int execute(CliAuthenticator.CliPrincipal principal) {
        ReportStatus status = submissionService.refreshStatus(UUID.fromString(reportId), principal.tenantId());
        System.out.println("reportKey=" + status.reportKey() + " status=" + status.status()
                + (status.errors() != null ? " errors=" + status.errors() : ""));
        return 0;
    }
}
