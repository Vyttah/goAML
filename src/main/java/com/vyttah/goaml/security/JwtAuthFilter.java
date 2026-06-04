package com.vyttah.goaml.security;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.config.tenant.TenantIdentifierResolver;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Validates the {@code Authorization: Bearer ...} JWT, populates {@link SecurityContextHolder}
 * with a {@link UserPrincipal}, and pushes the token's {@code schema} claim into
 * {@link TenantContext} so JPA calls during this request route to the correct schema.
 * Both are cleared in {@code finally} so pooled Tomcat threads don't leak state.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length()).trim();
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtException ex) {
            // Invalid / expired — leave SecurityContext empty; downstream returns 401.
            chain.doFilter(request, response);
            return;
        }

        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        String tenantIdStr = claims.get("tenant", String.class);
        UUID tenantId = tenantIdStr == null ? null : UUID.fromString(tenantIdStr);
        String schema = claims.get("schema", String.class);
        List<String> roles = JwtService.rolesFromClaims(claims);

        UserPrincipal principal = new UserPrincipal(userId, tenantId, email, "", true, roles);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        // Use createEmptyContext + setContext (rather than mutating getContext()) — the recommended
        // pattern with Spring Security 6's deferred SecurityContextHolderFilter.
        org.springframework.security.core.context.SecurityContext ctx =
                SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        TenantContext.set(schema == null ? TenantIdentifierResolver.DEFAULT_TENANT : schema);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
