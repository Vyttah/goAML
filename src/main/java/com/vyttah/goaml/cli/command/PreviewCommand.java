package com.vyttah.goaml.cli.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.cli.CliAuthenticator;
import com.vyttah.goaml.model.dto.report.DpmsrCreateRequest;
import com.vyttah.goaml.service.report.ReportPreview;
import com.vyttah.goaml.service.report.ReportService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code goaml preview <file>} — build a DPMSR request JSON file to the goAML XML that would be submitted,
 * without saving. Prints the XML to stdout; exit 0 if VALID, 1 if INVALID.
 */
@Component
@Command(name = "preview", description = "Build a DPMSR request JSON file to its goAML XML (no save).")
public class PreviewCommand extends AbstractGoamlCommand {

    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    @Parameters(index = "0", description = "Path to a DPMSR request JSON file.")
    Path file;

    public PreviewCommand(CliAuthenticator authenticator, ReportService reportService, ObjectMapper objectMapper) {
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
        ReportPreview preview = reportService.previewXml(request, principal.tenantId());
        if (preview.xml() != null) {
            System.out.println(preview.xml());
        }
        System.err.println("status=" + preview.status() + " messages=" + preview.messages().size());
        return "VALID".equals(preview.status()) ? 0 : 1;
    }
}
