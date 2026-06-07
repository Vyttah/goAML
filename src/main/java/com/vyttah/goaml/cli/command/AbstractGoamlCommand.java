package com.vyttah.goaml.cli.command;

import com.vyttah.goaml.cli.CliAuthenticator;
import com.vyttah.goaml.cli.GoamlCli;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * Base for goAML CLI subcommands: authenticate from the root's token, enforce the command's required roles,
 * run, and always clear the bound context. Subclasses implement only {@link #requiredRoles()} and
 * {@link #execute(CliAuthenticator.CliPrincipal)} (returning the process exit code).
 */
public abstract class AbstractGoamlCommand implements Callable<Integer> {

    @ParentCommand
    protected GoamlCli root;

    protected final CliAuthenticator authenticator;

    protected AbstractGoamlCommand(CliAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    /** Bare role names, any of which authorizes this command (mirrors the REST/MCP RBAC). */
    protected abstract String[] requiredRoles();

    /** The command body, run with the SecurityContext + TenantContext already bound. Returns the exit code. */
    protected abstract int execute(CliAuthenticator.CliPrincipal principal) throws Exception;

    @Override
    public Integer call() throws Exception {
        CliAuthenticator.CliPrincipal principal = authenticator.authenticate(root.requireToken());
        try {
            authenticator.requireRoles(principal, requiredRoles());
            return execute(principal);
        } finally {
            authenticator.clear();
        }
    }
}
