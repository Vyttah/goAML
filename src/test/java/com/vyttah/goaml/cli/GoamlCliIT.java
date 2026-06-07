package com.vyttah.goaml.cli;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 12.6 — the CLI run-mode boots a non-web Spring context and runs picocli commands through the
 * Spring-backed factory (so commands share the real engine/services). Proves the wiring + token auth + a real
 * read command end-to-end (lookups needs no tenant row — it reads the bundled jurisdiction config), and the
 * no-token / unknown-command failure paths.
 */
@SpringBootTest(classes = GoamlApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class GoamlCliIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    GoamlCliRunner runner;

    @Autowired
    JwtProperties jwtProperties;

    private String token(List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .claim("email", "cli@demo.local")
                .claim("schema", "public")
                .claim("roles", roles)
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void lookupsCommandRunsThroughSpringWiring() {
        int code = runner.run("lookups", "--token", token(List.of("ANALYST")));
        assertThat(code).isZero();
    }

    @Test
    void missingTokenIsAnError() {
        // No --token and no GOAML_TOKEN → the command's requireToken throws → handler maps to exit 2.
        int code = runner.run("lookups");
        assertThat(code).isEqualTo(2);
    }

    @Test
    void unknownCommandIsAUsageError() {
        int code = runner.run("definitely-not-a-command");
        assertThat(code).isNotZero();
    }
}
