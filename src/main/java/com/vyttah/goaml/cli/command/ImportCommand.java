package com.vyttah.goaml.cli.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.cli.CliAuthenticator;
import com.vyttah.goaml.model.dto.ingestion.ImportJobView;
import com.vyttah.goaml.model.entity.ingestion.ImportJob;
import com.vyttah.goaml.service.ingestion.IngestionService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * {@code goaml import <file>} — import a goAML XML or DPMSR CSV file as draft reports. Format is taken from
 * the file extension unless {@code --format} is given. Prints the job + per-row results as JSON. Exit 0 if no
 * rows failed, 1 otherwise.
 */
@Component
@Command(name = "import", description = "Import a goAML XML or DPMSR CSV file as draft reports.")
public class ImportCommand extends AbstractGoamlCommand {

    private final IngestionService ingestionService;
    private final ObjectMapper objectMapper;

    @Parameters(index = "0", description = "Path to a .xml (goAML) or .csv (DPMSR) file.")
    Path file;

    @Option(names = "--format", description = "xml | csv (defaults to the file extension).")
    String format;

    public ImportCommand(CliAuthenticator authenticator, IngestionService ingestionService,
                         ObjectMapper objectMapper) {
        super(authenticator);
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected String[] requiredRoles() {
        return new String[] {"ANALYST", "MLRO"};
    }

    @Override
    protected int execute(CliAuthenticator.CliPrincipal principal) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        String filename = file.getFileName().toString();
        String fmt = (format != null) ? format.toLowerCase(Locale.ROOT)
                : (filename.toLowerCase(Locale.ROOT).endsWith(".csv") ? "csv" : "xml");

        ImportJob job = "csv".equals(fmt)
                ? ingestionService.importCsv(bytes, filename, principal.tenantId(), principal.userId())
                : ingestionService.importXml(bytes, filename, principal.tenantId(), principal.userId());

        ImportJobView view = ImportJobView.from(job, objectMapper);
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(view));
        return view.failed() == 0 ? 0 : 1;
    }
}
