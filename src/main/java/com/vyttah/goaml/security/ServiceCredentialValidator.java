package com.vyttah.goaml.security;

import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.repository.federated.TrustedServiceRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
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
 *   <li>{@code exp}/{@code iat} — required; the lifetime must not exceed {@link #MAX_ASSERTION_LIFETIME}
 *                       so a leaked assertion has a small replay window</li>
 * </ul>
 *
 * <p>goAML looks up the {@link TrustedService} for the declared source to obtain the public key, then
 * verifies the signature. Any failure → {@link ServiceCredentialException} (→ HTTP 401).
 */
@RequiredArgsConstructor
@Component
public class ServiceCredentialValidator {

    /** Inbound assertions must target goAML. */
    static final String EXPECTED_AUDIENCE = "goaml";

    /** Cap the assertion lifetime so a captured token can only be replayed briefly. */
    static final Duration MAX_ASSERTION_LIFETIME = Duration.ofMinutes(5);

    private final TrustedServiceRepository trustedServices;

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
        Instant issuedAt = claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant();
        Instant expiresAt = claims.getExpiration().toInstant();
        if (issuedAt != null && Duration.between(issuedAt, expiresAt).compareTo(MAX_ASSERTION_LIFETIME) > 0) {
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

        return new VerifiedServiceAssertion(
                service,
                sourceSystem,
                externalUserId,
                claims.get("email", String.class),
                claims.get("org", String.class),
                roleHints(claims));
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
