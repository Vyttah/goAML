package com.vyttah.goaml.mcp;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
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
    public record Identity(UUID userId, UUID tenantId, String email, String tenantSchema, List<String> roles) {}

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
        return Optional.of(new Identity(principal.getUserId(), principal.getTenantId(),
                principal.getUsername(), TenantContext.get(), roles));
    }

    /** Like {@link #current()} but throws {@link McpAccessDeniedException} when unauthenticated. */
    public static Identity require() {
        return current().orElseThrow(() ->
                new McpAccessDeniedException("Authentication is required for this goAML MCP tool"));
    }

    /**
     * Require the caller to hold at least one of the given bare role names; returns the identity, or throws
     * {@link McpAccessDeniedException} if unauthenticated or lacking every role. The MCP edge's equivalent
     * of {@code @PreAuthorize("hasAnyRole(...)")}.
     */
    public static Identity requireAnyRole(String... roles) {
        Identity identity = require();
        for (String role : roles) {
            if (identity.roles().contains(role)) {
                return identity;
            }
        }
        throw new McpAccessDeniedException("This goAML MCP tool requires one of roles "
                + Arrays.toString(roles) + "; your roles are " + identity.roles());
    }

    /** Whether the current caller holds the given bare role name (e.g. {@code "MLRO"}). */
    public static boolean hasRole(String role) {
        return current().map(identity -> identity.roles().contains(role)).orElse(false);
    }
}
