package com.vyttah.goaml.cli;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.security.JwtProperties;
import com.vyttah.goaml.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link CliAuthenticator}: a minted JWT resolves to a principal and binds the SecurityContext
 * + {@link TenantContext}; role enforcement and cleanup behave.
 */
class CliAuthenticatorTest {

    private static final String SECRET = "cli-test-secret-please-replace-with-a-long-random-value-1234567890";
    private final JwtProperties props = new JwtProperties(SECRET, "goaml", 15);
    private final CliAuthenticator authenticator = new CliAuthenticator(new JwtService(props));

    @AfterEach
    void tearDown() {
        authenticator.clear();
    }

    private String token(UUID userId, UUID tenantId, List<String> roles, String schema) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("goaml")
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .claim("email", "officer@demo.local")
                .claim("tenant", tenantId == null ? null : tenantId.toString())
                .claim("schema", schema)
                .claim("roles", roles)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void authenticateBindsContextAndReturnsPrincipal() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        CliAuthenticator.CliPrincipal principal =
                authenticator.authenticate(token(userId, tenantId, List.of("MLRO", "ANALYST"), "tenant_demo"));

        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.tenantId()).isEqualTo(tenantId);
        assertThat(principal.roles()).containsExactlyInAnyOrder("MLRO", "ANALYST");
        assertThat(TenantContext.get()).isEqualTo("tenant_demo");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void clearRemovesContext() {
        authenticator.authenticate(token(UUID.randomUUID(), UUID.randomUUID(), List.of("ANALYST"), "tenant_demo"));
        authenticator.clear();
        assertThat(TenantContext.get()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void requireRolesPassesAndFails() {
        var principal = new CliAuthenticator.CliPrincipal(UUID.randomUUID(), UUID.randomUUID(),
                "e@x", List.of("ANALYST"));

        authenticator.requireRoles(principal, "MLRO", "ANALYST"); // does not throw

        assertThatThrownBy(() -> authenticator.requireRoles(principal, "MLRO"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires one of roles");
    }

    @Test
    void invalidTokenThrows() {
        assertThatThrownBy(() -> authenticator.authenticate("not-a-jwt"))
                .isInstanceOf(RuntimeException.class);
    }
}
