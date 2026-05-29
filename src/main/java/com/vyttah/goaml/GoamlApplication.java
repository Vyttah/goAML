package com.vyttah.goaml;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the goAML platform.
 *
 * <p>Default mode boots the Spring Boot web app (REST API + static SPA + MCP server).
 * Phase 12 will add a {@code --cli} switch that delegates to picocli instead of the web stack,
 * using the same jar.
 */
@SpringBootApplication
public class GoamlApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoamlApplication.class, args);
    }
}
