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
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Validates the {@code Authorization: Bearer ...} JWT, populates {@link SecurityContextHolder}
 * with a {@link UserPrincipal}, and pushes the token's {@code schema} claim into
 * {@link TenantContext} so JPA calls during this request route to the correct schema.
 * Both are cleared in {@code finally} so pooled Tomcat threads don't leak state.
 */
@RequiredArgsConstructor
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserStatusCache userStatusCache;

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

        UUID userId;
        try {
            userId = UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            // Malformed/absent subject — treat like an invalid token (→ 401) rather than a 500.
            chain.doFilter(request, response);
            return;
        }

        // B16 — reject a still-valid token held by a disabled/deleted user. The signature + expiry passed,
        // but a 15-minute window is too long to keep honouring a revoked account; a short-TTL cache keeps
        // this off the hot path. Not ACTIVE / not found → leave the context empty (→ downstream 401).
        if (!userStatusCache.isActive(userId)) {
            chain.doFilter(request, response);
            return;
        }

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
