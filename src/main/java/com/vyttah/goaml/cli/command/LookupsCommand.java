package com.vyttah.goaml.cli.command;

import com.vyttah.goaml.cli.CliAuthenticator;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionRegistry;
import com.vyttah.goaml.engine.lookup.LookupService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Set;

/**
 * {@code goaml lookups} — list the jurisdictions, or (with {@code --jurisdiction} + {@code --set}) the codes
 * in one lookup set. Read-only; any authenticated role.
 */
@Component
@Command(name = "lookups", description = "List jurisdictions, or the codes in a lookup set.")
public class LookupsCommand extends AbstractGoamlCommand {

    private final JurisdictionRegistry jurisdictionRegistry;
    private final LookupService lookupService;

    @Option(names = "--jurisdiction", description = "Jurisdiction code, e.g. 'ae'.")
    String jurisdiction;

    @Option(names = "--set", description = "Lookup set name, e.g. 'currencies' (requires --jurisdiction).")
    String set;

    public LookupsCommand(CliAuthenticator authenticator, JurisdictionRegistry jurisdictionRegistry,
                          LookupService lookupService) {
        super(authenticator);
        this.jurisdictionRegistry = jurisdictionRegistry;
        this.lookupService = lookupService;
    }

    @Override
    protected String[] requiredRoles() {
        return new String[] {"ANALYST", "MLRO", "TENANT_ADMIN", "SUPER_ADMIN"};
    }

    @Override
    protected int execute(CliAuthenticator.CliPrincipal principal) {
        if (jurisdiction != null && set != null) {
            Set<String> codes = lookupService.codes(jurisdiction, set);
            (codes == null ? Set.<String>of() : codes).stream().sorted().forEach(System.out::println);
        } else if (jurisdiction != null) {
            lookupService.setNames(jurisdiction).stream().sorted().forEach(System.out::println);
        } else {
            jurisdictionRegistry.codes().stream().sorted().forEach(System.out::println);
        }
        return 0;
    }
}
