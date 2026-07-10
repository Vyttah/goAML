package com.vyttah.goaml.cli;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.config.tenant.TenantIdentifierResolver;
import com.vyttah.goaml.security.JwtService;
import com.vyttah.goaml.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Establishes the per-command security + tenant context for the CLI from a goAML JWT — the same resolution
 * {@code JwtAuthFilter} (REST) and the MCP auth path use, so a CLI invocation runs as the token's tenant and
 * role. The token is supplied by the operator ({@code --token} or {@code GOAML_TOKEN}); credentials never
 * live in the CLI.
 */
@Component
public class CliAuthenticator {

    private final JwtService jwtService;

    public CliAuthenticator(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /** The identity resolved from the CLI token. */
    public record CliPrincipal(UUID userId, UUID tenantId, String email, List<String> roles) {}

    /** Parse + validate the token and bind the SecurityContext + {@link TenantContext} to this thread. */
    public CliPrincipal authenticate(String token) {
        Claims claims = jwtService.parse(token);
        UUID userId = UUID.fromString(claims.getSubject());
        String tenantStr = claims.get("tenant", String.class);
        UUID tenantId = tenantStr == null ? null : UUID.fromString(tenantStr);
        String schema = claims.get("schema", String.class);
        String email = claims.get("email", String.class);
        List<String> roles = JwtService.rolesFromClaims(claims);

        UserPrincipal principal = new UserPrincipal(userId, tenantId, email, "", true, roles);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        TenantContext.set(schema == null ? TenantIdentifierResolver.DEFAULT_TENANT : schema);
        // Drive the row-level app_user tenant filter (null → unscoped, for a platform token).
        TenantContext.setTenantId(tenantId);
        return new CliPrincipal(userId, tenantId, email, roles);
    }

    /** Clear the bound context (call in a finally after the command runs). */
    public void clear() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    /** Enforce that the caller holds at least one of the given bare role names. */
    public void requireRoles(CliPrincipal principal, String... roles) {
        for (String role : roles) {
            if (principal.roles().contains(role)) {
                return;
            }
        }
        throw new IllegalStateException("This command requires one of roles " + Arrays.toString(roles)
                + "; your roles are " + principal.roles());
    }
}
