package com.vyttah.goaml.security;

import com.vyttah.goaml.model.entity.federated.ConsumedAssertion;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.repository.federated.ConsumedAssertionRepository;
import com.vyttah.goaml.repository.federated.TrustedServiceRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

/**
 * Verifies the signed "service assertion" a sibling Vyttah service presents to the federated token-exchange
 * and integration push endpoints (Phase 1.5).
 *
 * <p>The assertion is a short-lived RS256 JWT the caller signs with its registered private key, asserting
 * the end-user identity it has already authenticated:
 * <ul>
 *   <li>{@code iss}   — the source system ({@code ACCOUNTING} | {@code SCREENING}), cross-checked against the
 *                       caller-declared source</li>
 *   <li>{@code sub}   — the user's id in the source system</li>
 *   <li>{@code aud}   — must be {@value #EXPECTED_AUDIENCE}</li>
 *   <li>{@code email} — the user's email (optional)</li>
 *   <li>{@code org}   — the source org reference for tenant resolution (optional)</li>
 *   <li>{@code roles} — advisory role hints (optional; goAML stays authoritative for actual roles)</li>
 *   <li>{@code jti}   — required; the assertion's unique id. Recorded on first use so a second
 *                       presentation of the same {@code jti} before its {@code exp} is rejected as a replay</li>
 *   <li>{@code iat}   — required; together with {@code exp} the lifetime must not exceed
 *                       {@link #MAX_ASSERTION_LIFETIME} so a leaked assertion has a small replay window</li>
 *   <li>{@code exp}   — required</li>
 * </ul>
 *
 * <p>goAML looks up the {@link TrustedService} for the declared source to obtain the public key, then
 * verifies the signature. Any failure → {@link ServiceCredentialException} (→ HTTP 401).
 */
@RequiredArgsConstructor
@Component
public class ServiceCredentialValidator {

    private static final Logger log = LoggerFactory.getLogger(ServiceCredentialValidator.class);

    /** Inbound assertions must target goAML. */
    static final String EXPECTED_AUDIENCE = "goaml";

    /** Cap the assertion lifetime so a captured token can only be replayed briefly. */
    static final Duration MAX_ASSERTION_LIFETIME = Duration.ofMinutes(5);

    private final TrustedServiceRepository trustedServices;
    private final ConsumedAssertionRepository consumedAssertions;

    /**
     * @param sourceSystem the source the caller declares (used to select the verification key)
     * @param assertionJwt the signed service assertion
     * @return the validated assertion content
     * @throws ServiceCredentialException if the source is unknown/disabled or the assertion fails verification
     */
    public VerifiedServiceAssertion verify(SourceSystem sourceSystem, String assertionJwt) {
        if (sourceSystem == null) {
            throw new ServiceCredentialException("Missing source system");
        }
        if (assertionJwt == null || assertionJwt.isBlank()) {
            throw new ServiceCredentialException("Missing service assertion");
        }

        TrustedService service = trustedServices.findBySourceSystem(sourceSystem)
                .orElseThrow(() -> new ServiceCredentialException("Unknown source system: " + sourceSystem));
        if (!service.isActive()) {
            throw new ServiceCredentialException("Source system is disabled: " + sourceSystem);
        }

        PublicKey publicKey = parsePublicKey(service.getPublicKeyPem());

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireAudience(EXPECTED_AUDIENCE)
                    .build()
                    .parseSignedClaims(assertionJwt)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            // Bad signature, expired, wrong audience, malformed — never echo the source token detail.
            throw new ServiceCredentialException("Invalid service assertion");
        }

        if (claims.getExpiration() == null) {
            throw new ServiceCredentialException("Service assertion must set exp");
        }
        // iat is REQUIRED (B10): without it the lifetime cap below could not be enforced, so a signer could
        // mint an assertion with a years-long exp. Reject when absent.
        if (claims.getIssuedAt() == null) {
            throw new ServiceCredentialException("Service assertion must set iat");
        }
        Instant issuedAt = claims.getIssuedAt().toInstant();
        Instant expiresAt = claims.getExpiration().toInstant();
        if (Duration.between(issuedAt, expiresAt).compareTo(MAX_ASSERTION_LIFETIME) > 0) {
            throw new ServiceCredentialException("Service assertion lifetime exceeds the allowed maximum");
        }

        String issuer = claims.getIssuer();
        if (issuer != null && !issuer.equals(sourceSystem.name())) {
            throw new ServiceCredentialException("Service assertion issuer does not match the declared source");
        }

        String externalUserId = claims.getSubject();
        if (externalUserId == null || externalUserId.isBlank()) {
            throw new ServiceCredentialException("Service assertion must carry the external user id (sub)");
        }

        // jti is REQUIRED (B10) — it anchors single-use semantics. Recording it (and rejecting a second use
        // before exp) gives replay protection that survives restarts and holds across replicas.
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) {
            throw new ServiceCredentialException("Service assertion must carry a unique id (jti)");
        }
        consumeOnce(jti, sourceSystem, expiresAt);

        return new VerifiedServiceAssertion(
                service,
                sourceSystem,
                externalUserId,
                claims.get("email", String.class),
                claims.get("org", String.class),
                roleHints(claims));
    }

    /**
     * Records the assertion's {@code jti} as consumed, rejecting a replay. Opportunistically purges already
     * expired rows first so the store stays small. The insert is the authority: a duplicate primary key (a
     * concurrent or sequential replay) surfaces as a {@link DataIntegrityViolationException}, which we map to
     * a replay rejection — closing the read-then-write race without a lock.
     */
    private void consumeOnce(String jti, SourceSystem sourceSystem, Instant expiresAt) {
        try {
            consumedAssertions.deleteExpired(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (DataAccessException e) {
            // Cleanup is best-effort; never let a store hiccup block a legitimate verification — but a
            // narrow catch + a log line means a real failure is at least visible (it used to be silent).
            log.debug("Expired-assertion cleanup failed (continuing): {}", e.getMessage());
        }
        if (consumedAssertions.existsById(jti)) {
            throw new ServiceCredentialException("Service assertion has already been used (replay)");
        }
        try {
            consumedAssertions.save(new ConsumedAssertion(jti, sourceSystem,
                    expiresAt.atOffset(ZoneOffset.UTC)));
        } catch (DataIntegrityViolationException duplicate) {
            // Lost the race — another verification consumed the same jti first.
            throw new ServiceCredentialException("Service assertion has already been used (replay)");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> roleHints(Claims claims) {
        Object raw = claims.get("roles");
        return raw instanceof List<?> list ? (List<String>) list : List.of();
    }

    private static PublicKey parsePublicKey(String pem) {
        try {
            String base64 = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception ex) {
            // A registered key that won't parse is a configuration fault, not a caller error.
            throw new ServiceCredentialException("Registered service key is invalid");
        }
    }
}
