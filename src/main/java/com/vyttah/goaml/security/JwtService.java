package com.vyttah.goaml.security;

import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.role.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
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

    /**
     * The committed dev fallback in {@code application.yml} ({@code goaml.jwt.secret}). It passes the
     * 32-byte HS256 length check, so a deployment that forgets {@code GOAML_JWT_SECRET} would otherwise boot
     * and sign tokens with a public-in-VCS key (forgeable SUPER_ADMIN / any-schema tokens → cross-tenant
     * takeover). Booting with it is refused unless a non-prod profile is active.
     */
    static final String KNOWN_DEFAULT_SECRET =
            "dev-secret-please-replace-with-a-long-random-value-min-256-bits";

    /** Profiles where the committed default secret is tolerated (local convenience only). */
    private static final Set<String> NON_PROD_PROFILES = Set.of("dev", "test", "local");

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtService(JwtProperties props, Environment environment) {
        String secret = props.secret();
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "goaml.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        if (KNOWN_DEFAULT_SECRET.equals(secret) && !nonProdProfileActive(environment)) {
            throw new IllegalStateException(
                    "goaml.jwt.secret is the committed default (dev-secret-please-replace…). Refusing to "
                            + "start: set GOAML_JWT_SECRET to a unique ≥256-bit value, or run with a "
                            + "dev/test/local profile. The default is public in VCS — tokens signed with it "
                            + "are forgeable.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = props.issuer();
        this.accessTokenTtl = Duration.ofMinutes(props.accessTokenTtlMinutes());
    }

    /** True if any of dev/test/local is among the active (or, if none active, default) profiles. */
    private static boolean nonProdProfileActive(Environment environment) {
        String[] active = environment.getActiveProfiles();
        String[] profiles = active.length > 0 ? active : environment.getDefaultProfiles();
        return Arrays.stream(profiles).anyMatch(p -> NON_PROD_PROFILES.contains(p));
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
