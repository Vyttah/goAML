package com.vyttah.goaml.cli.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.cli.CliAuthenticator;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.service.report.ReportService;
import com.vyttah.goaml.service.report.ReportValidationResult;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code goaml validate <file>} — validate a DPMSR request JSON file without saving it. Prints the verdict +
 * messages as JSON; exit 0 if VALID, 1 if INVALID. Same engine path as the REST/MCP validate.
 */
@Component
@Command(name = "validate", description = "Validate a DPMSR request JSON file (no save). Exit 0=VALID, 1=INVALID.")
public class ValidateCommand extends AbstractGoamlCommand {

    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    @Parameters(index = "0", description = "Path to a DPMSR request JSON file.")
    Path file;

    public ValidateCommand(CliAuthenticator authenticator, ReportService reportService, ObjectMapper objectMapper) {
        super(authenticator);
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected String[] requiredRoles() {
        return new String[] {"ANALYST", "MLRO"};
    }

    @Override
    protected int execute(CliAuthenticator.CliPrincipal principal) throws Exception {
        DpmsrCreateRequest request = objectMapper.readValue(Files.readAllBytes(file), DpmsrCreateRequest.class);
        ReportValidationResult result = reportService.validate(request, principal.tenantId());
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        return "VALID".equals(result.status()) ? 0 : 1;
    }
}
