package com.vyttah.goaml.mcp;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-level proof that {@link McpIdentity} resolves the caller from the thread's SecurityContext +
 * {@link TenantContext} — the exact state {@code JwtAuthFilter} sets before a tool runs — and that it
 * fails closed (empty) when there is no authenticated {@link UserPrincipal}.
 */
class McpIdentityTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private void authenticate(List<String> roles, String schema) {
        UserPrincipal principal = new UserPrincipal(USER_ID, TENANT_ID, "officer@demo.local", "", true, roles);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        TenantContext.set(schema);
    }

    @Test
    void resolvesIdentityWithBareRolesAndTenantSchema() {
        authenticate(List.of("MLRO", "ANALYST"), "tenant_demo");

        var identity = McpIdentity.current().orElseThrow();

        assertThat(identity.userId()).isEqualTo(USER_ID);
        assertThat(identity.tenantId()).isEqualTo(TENANT_ID);
        assertThat(identity.email()).isEqualTo("officer@demo.local");
        assertThat(identity.tenantSchema()).isEqualTo("tenant_demo");
        // The ROLE_ authority prefix is stripped back to bare role names (matching the JWT claim).
        assertThat(identity.roles()).containsExactlyInAnyOrder("MLRO", "ANALYST");
    }

    @Test
    void emptyWhenNoAuthentication() {
        assertThat(McpIdentity.current()).isEmpty();
    }

    @Test
    void emptyWhenAuthenticationNotAuthenticated() {
        UserPrincipal principal = new UserPrincipal(USER_ID, null, "officer@demo.local", "", true, List.of("MLRO"));
        // Two-arg token → isAuthenticated() == false.
        var auth = new UsernamePasswordAuthenticationToken(principal, null);
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        assertThat(McpIdentity.current()).isEmpty();
    }

    @Test
    void emptyWhenPrincipalIsNotUserPrincipal() {
        var auth = new UsernamePasswordAuthenticationToken("anonymousString", null, List.of());
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);

        assertThat(McpIdentity.current()).isEmpty();
    }

    @Test
    void requireThrowsWhenUnauthenticated() {
        assertThatThrownBy(McpIdentity::require)
                .isInstanceOf(McpAccessDeniedException.class)
                .hasMessageContaining("Authentication is required");
    }

    @Test
    void requireAnyRoleReturnsIdentityWhenRolePresent() {
        authenticate(List.of("ANALYST"), "tenant_demo");

        McpIdentity.Identity identity = McpIdentity.requireAnyRole("MLRO", "ANALYST");

        assertThat(identity.roles()).contains("ANALYST");
    }

    @Test
    void requireAnyRoleThrowsWhenRoleMissing() {
        authenticate(List.of("ANALYST"), "tenant_demo");

        assertThatThrownBy(() -> McpIdentity.requireAnyRole("MLRO"))
                .isInstanceOf(McpAccessDeniedException.class)
                .hasMessageContaining("requires one of roles");
    }

    @Test
    void requireAnyRoleThrowsWhenUnauthenticated() {
        assertThatThrownBy(() -> McpIdentity.requireAnyRole("ANALYST"))
                .isInstanceOf(McpAccessDeniedException.class);
    }

    @Test
    void hasRoleReflectsCallerRoles() {
        authenticate(List.of("ANALYST"), "tenant_demo");

        assertThat(McpIdentity.hasRole("ANALYST")).isTrue();
        assertThat(McpIdentity.hasRole("MLRO")).isFalse();
    }

    @Test
    void hasRoleFalseWhenUnauthenticated() {
        assertThat(McpIdentity.hasRole("ANALYST")).isFalse();
    }
}
