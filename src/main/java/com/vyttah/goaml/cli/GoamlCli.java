package com.vyttah.goaml.cli;

import com.vyttah.goaml.cli.command.ImportCommand;
import com.vyttah.goaml.cli.command.LookupsCommand;
import com.vyttah.goaml.cli.command.PreviewCommand;
import com.vyttah.goaml.cli.command.StatusCommand;
import com.vyttah.goaml.cli.command.SubmitCommand;
import com.vyttah.goaml.cli.command.ValidateCommand;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

/**
 * Root of the goAML CLI (the {@code --cli} run-mode of the jar). Each subcommand delegates to the same
 * services as the REST API and the MCP tools — parity by construction. The {@code --token} option (or the
 * {@code GOAML_TOKEN} environment variable) supplies the tenant-scoped JWT every subcommand runs under.
 */
@Component
@Command(name = "goaml", mixinStandardHelpOptions = true,
        description = "goAML CLI — build/validate/preview/submit/track UAE FIU reports.",
        subcommands = {
                ValidateCommand.class,
                PreviewCommand.class,
                SubmitCommand.class,
                StatusCommand.class,
                ImportCommand.class,
                LookupsCommand.class
        })
public class GoamlCli {

    @Option(names = "--token", scope = ScopeType.INHERIT,
            description = "goAML JWT (tenant-scoped). May appear before or after the subcommand; falls back "
                    + "to the GOAML_TOKEN environment variable.")
    private String token;

    /** The token from {@code --token} or {@code GOAML_TOKEN}; throws if neither is set. */
    public String requireToken() {
        String resolved = (token != null && !token.isBlank()) ? token : System.getenv("GOAML_TOKEN");
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalStateException("No goAML token: pass --token or set GOAML_TOKEN");
        }
        return resolved;
    }
}
