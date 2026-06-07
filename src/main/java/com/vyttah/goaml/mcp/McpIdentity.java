package com.vyttah.goaml.mcp;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the identity behind the current MCP tool call from the thread's {@code SecurityContext} +
 * {@link TenantContext}.
 *
 * <p>Both are set by {@code JwtAuthFilter} on the servlet thread that handles the {@code /mcp/message}
 * POST and that <em>synchronously</em> executes the tool. That is exactly why an MCP call runs as the
 * caller's tenant + role — identical to a REST request — with no separate auth code and no DB hit.
 * Tools use this to gate on role and to scope work to the resolved tenant.
 */
public final class McpIdentity {

    private static final String ROLE_PREFIX = "ROLE_";

    private McpIdentity() {}

    /** The authenticated caller of the current MCP tool invocation. */
    public record Identity(UUID userId, String email, String tenantSchema, List<String> roles) {}

    /**
     * The identity bound to the current thread, or empty when the call is unauthenticated (no
     * {@link UserPrincipal} in the {@code SecurityContext}). Roles are returned bare (the
     * {@code ROLE_} authority prefix is stripped) to match the JWT {@code roles} claim.
     */
    public static Optional<Identity> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith(ROLE_PREFIX) ? a.substring(ROLE_PREFIX.length()) : a)
                .toList();
        return Optional.of(new Identity(principal.getUserId(), principal.getUsername(),
                TenantContext.get(), roles));
    }

    /** Like {@link #current()} but throws when there is no authenticated caller. */
    public static Identity require() {
        return current().orElseThrow(() ->
                new IllegalStateException("No authenticated MCP identity on the current call"));
    }

    /** Whether the current caller holds the given bare role name (e.g. {@code "MLRO"}). */
    public static boolean hasRole(String role) {
        return current().map(identity -> identity.roles().contains(role)).orElse(false);
    }
}
