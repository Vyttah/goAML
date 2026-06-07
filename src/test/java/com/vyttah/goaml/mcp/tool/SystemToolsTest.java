package com.vyttah.goaml.mcp.tool;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the baseline MCP tools: {@code goaml_ping} echoes the configured server identity, and
 * {@code goaml_whoami} reflects the authenticated caller (or reports unauthenticated when no principal
 * is bound to the thread).
 */
class SystemToolsTest {

    private final SystemTools tools = new SystemTools("goaml-mcp", "0.1.0");

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void pingReturnsServerIdentity() {
        SystemTools.PingResult result = tools.ping();

        assertThat(result.server()).isEqualTo("goaml-mcp");
        assertThat(result.version()).isEqualTo("0.1.0");
        assertThat(result.status()).isEqualTo("ok");
    }

    @Test
    void whoamiReflectsAuthenticatedCaller() {
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(), null, "officer@demo.local", "", true, List.of("MLRO"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        TenantContext.set("tenant_demo");

        SystemTools.WhoAmIResult result = tools.whoami();

        assertThat(result.authenticated()).isTrue();
        assertThat(result.email()).isEqualTo("officer@demo.local");
        assertThat(result.tenantSchema()).isEqualTo("tenant_demo");
        assertThat(result.roles()).containsExactly("MLRO");
    }

    @Test
    void whoamiReportsUnauthenticatedWhenNoPrincipal() {
        SystemTools.WhoAmIResult result = tools.whoami();

        assertThat(result.authenticated()).isFalse();
        assertThat(result.email()).isNull();
        assertThat(result.tenantSchema()).isNull();
        assertThat(result.roles()).isEmpty();
    }
}
