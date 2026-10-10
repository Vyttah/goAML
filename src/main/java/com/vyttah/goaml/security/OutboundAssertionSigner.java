package com.vyttah.goaml.security;

import com.vyttah.goaml.config.integration.OutboundIntegrationProperties;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Mints the short-lived RS256 service assertion that authenticates <strong>goAML</strong> to the AML platform's
 * integration endpoints (goAML ↔ ERP wiring, Phase 2 / S2, decision D22). The mirror image of
 * {@link ServiceCredentialValidator}, which verifies the assertions arriving the other way, and a direct
 * counterpart of the AML platform's own {@code GoamlAssertionService}.
 *
 * <p>The claim set is the suite-wide contract ({@code 00-decisions.md} §"Service assertion contract"):
 * <ul>
 *   <li>{@code iss} — {@code GOAML}: which {@code trusted_service} row holds the key that verifies this</li>
 *   <li>{@code aud} — {@code aml}: the receiver, which rejects anything else</li>
 *   <li>{@code org} — the caller's company id, here the tenant's <em>AML</em> company id (its
 *       {@code SCREENING} external ref), which the receiver cross-checks against the event body</li>
 *   <li>{@code sub} — the acting user, or {@code system} when a background publisher is the actor</li>
 *   <li>{@code jti} — single-use, remembered by the receiver until {@code exp}</li>
 *   <li>{@code iat} / {@code exp} — lifetime capped at 300 s by the receiver, so goAML signs well under it</li>
 * </ul>
 *
 * <p><strong>Why {@code aud} is set as a single string and not through {@code audience().add(...)}.</strong>
 * goAML is on jjwt 0.12, where an audience is a {@code Set} and {@code add} serialises it as a JSON
 * <em>array</em> — even for one value. The AML platform verifies with jjwt <strong>0.11.5</strong>, whose
 * {@code requireAudience(...)} reads {@code aud} as a {@code String} and fails on an array. The two ends would
 * therefore never interoperate, and the only symptom would be a 401 with no detail (a verifier must not echo
 * token contents). {@code audienceSingle} keeps the legacy string form that every other signer in the suite
 * produces. {@code OutboundAssertionSignerTest} asserts the serialised shape, not just the claim value.
 */
@Slf4j
@Component
public class OutboundAssertionSigner {

    /** The {@code sub} used when no human is behind the event — the contract's value for outbox workers. */
    public static final String SYSTEM_SUBJECT = "system";

    private final OutboundIntegrationProperties properties;

    private final PrivateKey privateKey;

    public OutboundAssertionSigner(OutboundIntegrationProperties properties) {
        this.properties = properties;
        this.privateKey = parsePrivateKeyOrNull(properties.privateKeyPem());
        if (properties.enabled() && privateKey == null) {
            log.warn("goaml.integration.outbound.enabled is true but no private key is configured — "
                    + "report-status events will not be published");
        }
    }

    /** True when a signing key is configured, i.e. this instance can authenticate itself to the AML platform. */
    public boolean isEnabled() {
        return privateKey != null;
    }

    /**
     * Mint an assertion for the {@code X-Service-Assertion} header.
     *
     * @param subject the acting user id, or {@code null} / blank for {@link #SYSTEM_SUBJECT}
     * @param org     the caller's company id for the receiver's tenant cross-check; required
     * @throws IllegalStateException    no signing key is configured
     * @throws IllegalArgumentException {@code org} is blank
     */
    public String mint(String subject, String org) {
        if (privateKey == null) {
            throw new IllegalStateException(
                    "Outbound goAML integration is not configured (set goaml.integration.outbound.private-key-pem)");
        }
        if (org == null || org.isBlank()) {
            throw new IllegalArgumentException("service assertion org is required");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                // Legacy single-string aud — see the class javadoc; do not replace with audience().add(...).
                // Deprecated in jjwt 0.12 *because* it is the legacy single-string form — which is exactly
                // what is required here, so the deprecation is accepted knowingly, not overlooked.
                .audience().single(properties.amlAudience())
                .subject(subject == null || subject.isBlank() ? SYSTEM_SUBJECT : subject)
                .claim("org", org)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.assertionLifetimeSeconds())))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    private static PrivateKey parsePrivateKeyOrNull(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        try {
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Invalid goaml.integration.outbound.private-key-pem (expected a PKCS#8 RSA private key)", e);
        }
    }
}
