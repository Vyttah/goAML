package com.vyttah.goaml.cli;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

/**
 * Runs the picocli {@link GoamlCli} command tree with a Spring-backed factory, so every command (and its
 * injected services) is a managed bean. Thrown exceptions (bad token, role denial, IO) are reported as a
 * single clean line and mapped to a non-zero exit code, rather than a stack trace.
 */
@Component
public class GoamlCliRunner {

    private final ApplicationContext context;

    public GoamlCliRunner(ApplicationContext context) {
        this.context = context;
    }

    public int run(String... args) {
        GoamlCli root = context.getAutowireCapableBeanFactory().createBean(GoamlCli.class);
        CommandLine commandLine = new CommandLine(root, springFactory());
        commandLine.setExecutionExceptionHandler((ex, cmd, parseResult) -> {
            cmd.getErr().println("Error: " + ex.getMessage());
            return 2;
        });
        return commandLine.execute(args);
    }

    private CommandLine.IFactory springFactory() {
        return new CommandLine.IFactory() {
            @Override
            public <K> K create(Class<K> cls) throws Exception {
                return GoamlCliRunner.this.create(cls);
            }
        };
    }

    /**
     * A FRESH, fully-injected instance per call — picocli commands hold per-invocation parse state (options,
     * the parent reference), so they must not be reused singletons. {@code createBean} autowires the
     * constructor/services without registering a managed singleton; non-bean types fall back to picocli.
     */
    private <K> K create(Class<K> cls) throws Exception {
        try {
            return context.getAutowireCapableBeanFactory().createBean(cls);
        } catch (Exception notAManagedBean) {
            return CommandLine.defaultFactory().create(cls);
        }
    }
}
