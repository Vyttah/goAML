package com.vyttah.goaml.security;

import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.role.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Issues and parses JWT access tokens (HS256).
 *
 * <p>Claims on every token:
 * <ul>
 *   <li>{@code sub}      — user UUID</li>
 *   <li>{@code email}    — user email (convenience)</li>
 *   <li>{@code tenant}   — tenant UUID (null for SUPER_ADMIN platform users)</li>
 *   <li>{@code schema}   — Postgres schema name; {@code JwtAuthFilter} pushes this into
 *                          {@link com.vyttah.goaml.config.tenant.TenantContext}</li>
 *   <li>{@code roles}    — list of role names (e.g. {@code ["TENANT_ADMIN","MLRO"]})</li>
 * </ul>
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtService(JwtProperties props) {
        byte[] keyBytes = props.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "goaml.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = props.issuer();
        this.accessTokenTtl = Duration.ofMinutes(props.accessTokenTtlMinutes());
    }

    public IssuedToken issueAccessToken(AppUser user, String tenantSchema) {
        Instant now = Instant.now();
        Instant expiry = now.plus(accessTokenTtl);
        List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();

        String token = Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("email", user.getEmail())
                .claim("tenant", user.getTenantId() == null ? null : user.getTenantId().toString())
                .claim("schema", tenantSchema)
                .claim("roles", roleNames)
                .signWith(signingKey)
                .compact();

        return new IssuedToken(token, expiry, accessTokenTtl.toSeconds());
    }

    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {}

    @SuppressWarnings("unchecked")
    public static List<String> rolesFromClaims(Claims claims) {
        Object raw = claims.get("roles");
        if (raw instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    public static Set<String> rolesAsSet(Claims claims) {
        return Set.copyOf(rolesFromClaims(claims));
    }
}
