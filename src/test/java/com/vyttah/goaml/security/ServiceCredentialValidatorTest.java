package com.vyttah.goaml.security;

import com.vyttah.goaml.model.entity.federated.ConsumedAssertion;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.repository.federated.ConsumedAssertionRepository;
import com.vyttah.goaml.repository.federated.TrustedServiceRepository;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 1.5a.3 — {@link ServiceCredentialValidator} RS256 assertion verification, using an in-test RSA keypair.
 *
 * <p>B10 additions: {@code iat} and {@code jti} are required, the 5-minute lifetime cap is unconditional,
 * and a {@code jti} can be used only once (replay protection). The consumed-assertion store is mocked with
 * an in-memory {@link Set} so a second use of the same {@code jti} is rejected.
 */
class ServiceCredentialValidatorTest {

    private static KeyPair keyPair;
    private static KeyPair otherKeyPair;

    private final TrustedServiceRepository repo = mock(TrustedServiceRepository.class);
    private final ConsumedAssertionRepository consumed = mock(ConsumedAssertionRepository.class);
    private final ServiceCredentialValidator validator = new ServiceCredentialValidator(repo, consumed);

    /** In-memory stand-in for the consumed_assertion table so replay tests behave realistically. */
    private final Set<String> consumedJtis = new HashSet<>();

    @BeforeEach
    void registerActiveAccountingService() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
        otherKeyPair = gen.generateKeyPair();

        TrustedService svc = new TrustedService(UUID.randomUUID(), SourceSystem.ACCOUNTING,
                "accounting", pem(keyPair.getPublic()), true, "ACTIVE");
        when(repo.findBySourceSystem(SourceSystem.ACCOUNTING)).thenReturn(Optional.of(svc));
        when(repo.findBySourceSystem(SourceSystem.SCREENING)).thenReturn(Optional.empty());

        // Mock the replay store with the in-memory set.
        when(consumed.existsById(anyString()))
                .thenAnswer(inv -> consumedJtis.contains(inv.getArgument(0, String.class)));
        when(consumed.save(any(ConsumedAssertion.class))).thenAnswer(inv -> {
            ConsumedAssertion a = inv.getArgument(0);
            if (!consumedJtis.add(a.getJti())) {
                throw new DataIntegrityViolationException("duplicate jti " + a.getJti());
            }
            return a;
        });
    }

    @Test
    void verifiesValidAssertionAndExtractsClaims() {
        String jwt = assertion(keyPair.getPrivate(), "ACCOUNTING", "goaml",
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS), "jti-1");

        VerifiedServiceAssertion result = validator.verify(SourceSystem.ACCOUNTING, jwt);

        assertThat(result.externalUserId()).isEqualTo("ext-1");
        assertThat(result.externalEmail()).isEqualTo("user@acct.test");
        assertThat(result.externalOrgRef()).isEqualTo("ORG-9");
        assertThat(result.roleHints()).containsExactly("ANALYST");
        assertThat(result.sourceSystem()).isEqualTo(SourceSystem.ACCOUNTING);
    }

    @Test
    void rejectsWrongSigningKey() {
        String jwt = assertion(otherKeyPair.getPrivate(), "ACCOUNTING", "goaml",
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS), "jti-x");

        assertThatThrownBy(() -> validator.verify(SourceSystem.ACCOUNTING, jwt))
                .isInstanceOf(ServiceCredentialException.class)
                .hasMessageContaining("Invalid service assertion");
    }

    @Test
    void rejectsExpiredAssertion() {
        String jwt = assertion(keyPair.getPrivate(), "ACCOUNTING", "goaml",
                Instant.now().minus(10, ChronoUnit.MINUTES), Instant.now().minus(5, ChronoUnit.MINUTES),
                "jti-exp");

        assertThatThrownBy(() -> validator.verify(SourceSystem.ACCOUNTING, jwt))
                .isInstanceOf(ServiceCredentialException.class);
    }

    @Test
    void rejectsUnknownSource() {
        String jwt = assertion(keyPair.getPrivate(), "SCREENING", "goaml",
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS), "jti-u");

        assertThatThrownBy(() -> validator.verify(SourceSystem.SCREENING, jwt))
                .isInstanceOf(ServiceCredentialException.class)
                .hasMessageContaining("Unknown source system");
    }

    @Test
    void rejectsDisabledSource() {
        TrustedService disabled = new TrustedService(UUID.randomUUID(), SourceSystem.SCREENING,
                "screening", pem(keyPair.getPublic()), false, "DISABLED");
        when(repo.findBySourceSystem(SourceSystem.SCREENING)).thenReturn(Optional.of(disabled));
        String jwt = assertion(keyPair.getPrivate(), "SCREENING", "goaml",
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS), "jti-d");

        assertThatThrownBy(() -> validator.verify(SourceSystem.SCREENING, jwt))
                .isInstanceOf(ServiceCredentialException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void rejectsWrongAudience() {
        String jwt = assertion(keyPair.getPrivate(), "ACCOUNTING", "someone-else",
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS), "jti-a");

        assertThatThrownBy(() -> validator.verify(SourceSystem.ACCOUNTING, jwt))
                .isInstanceOf(ServiceCredentialException.class);
    }

    @Test
    void rejectsOverlongLifetime() {
        String jwt = assertion(keyPair.getPrivate(), "ACCOUNTING", "goaml",
                Instant.now(), Instant.now().plus(30, ChronoUnit.MINUTES), "jti-long");

        assertThatThrownBy(() -> validator.verify(SourceSystem.ACCOUNTING, jwt))
                .isInstanceOf(ServiceCredentialException.class)
                .hasMessageContaining("lifetime");
    }

    @Test
    void rejectsIssuerMismatch() {
        String jwt = assertion(keyPair.getPrivate(), "SCREENING", "goaml",
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS), "jti-iss");

        // declared ACCOUNTING but assertion iss=SCREENING
        assertThatThrownBy(() -> validator.verify(SourceSystem.ACCOUNTING, jwt))
                .isInstanceOf(ServiceCredentialException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void rejectsBlankAssertion() {
        assertThatThrownBy(() -> validator.verify(SourceSystem.ACCOUNTING, "  "))
                .isInstanceOf(ServiceCredentialException.class)
                .hasMessageContaining("Missing service assertion");
    }

    // --- B10: iat required, lifetime cap unconditional, jti required + single-use ---

    @Test
    void rejectsMissingIat() {
        String jwt = assertion(keyPair.getPrivate(), "ACCOUNTING", "goaml",
                null, Instant.now().plus(60, ChronoUnit.SECONDS), "jti-noiat");

        assertThatThrownBy(() -> validator.verify(SourceSystem.ACCOUNTING, jwt))
                .isInstanceOf(ServiceCredentialException.class)
                .hasMessageContaining("iat");
    }

    @Test
    void rejectsMissingJti() {
        String jwt = assertion(keyPair.getPrivate(), "ACCOUNTING", "goaml",
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS), null);

        assertThatThrownBy(() -> validator.verify(SourceSystem.ACCOUNTING, jwt))
                .isInstanceOf(ServiceCredentialException.class)
                .hasMessageContaining("jti");
    }

    @Test
    void freshJtiSucceeds() {
        String jwt = assertion(keyPair.getPrivate(), "ACCOUNTING", "goaml",
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS), "jti-fresh");

        VerifiedServiceAssertion result = validator.verify(SourceSystem.ACCOUNTING, jwt);
        assertThat(result.externalUserId()).isEqualTo("ext-1");
        assertThat(consumedJtis).contains("jti-fresh");
    }

    @Test
    void replayedJtiIsRejected() {
        String jwt = assertion(keyPair.getPrivate(), "ACCOUNTING", "goaml",
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS), "jti-replay");

        // First use is accepted and records the jti.
        validator.verify(SourceSystem.ACCOUNTING, jwt);

        // Second use of the same jti (same token, still in lifetime) is a replay → rejected.
        assertThatThrownBy(() -> validator.verify(SourceSystem.ACCOUNTING, jwt))
                .isInstanceOf(ServiceCredentialException.class)
                .hasMessageContaining("replay");
    }

    // --- helpers ---

    private static String assertion(PrivateKey signingKey, String issuer, String audience,
                                    Instant issuedAt, Instant expiresAt, String jti) {
        var builder = Jwts.builder()
                .issuer(issuer)
                .subject("ext-1")
                .audience().add(audience).and()
                .expiration(java.util.Date.from(expiresAt))
                .claim("email", "user@acct.test")
                .claim("org", "ORG-9")
                .claim("roles", List.of("ANALYST"))
                .signWith(signingKey, Jwts.SIG.RS256);
        if (issuedAt != null) {
            builder.issuedAt(java.util.Date.from(issuedAt));
        }
        if (jti != null) {
            builder.id(jti);
        }
        return builder.compact();
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
