package com.vyttah.goaml;

import com.vyttah.goaml.cli.GoamlCliRunner;
import com.vyttah.goaml.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;

/**
 * Entry point for the goAML platform.
 *
 * <p>Default mode boots the Spring Boot web app (REST API + static SPA + MCP server). With {@code --cli}
 * as the first argument the <em>same jar</em> runs the picocli terminal surface instead (no web server):
 * it boots a non-web Spring context (so the CLI commands share the real engine/services), executes the
 * command, and exits with its status code. The web MCP autoconfig is conditional on a servlet context, so
 * it is simply absent in CLI mode.
 */
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class GoamlApplication {

    public static void main(String[] args) {
        if (args.length > 0 && "--cli".equals(args[0])) {
            String[] cliArgs = Arrays.copyOfRange(args, 1, args.length);
            SpringApplication app = new SpringApplication(GoamlApplication.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
            int exitCode;
            try (ConfigurableApplicationContext context = app.run()) {
                exitCode = context.getBean(GoamlCliRunner.class).run(cliArgs);
            }
            System.exit(exitCode);
        }
        SpringApplication.run(GoamlApplication.class, args);
    }
}
