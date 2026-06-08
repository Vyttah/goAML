package com.vyttah.goaml.security;

import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.repository.federated.TrustedServiceRepository;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 1.5a.3 — {@link ServiceCredentialValidator} RS256 assertion verification, using an in-test RSA keypair.
 */
class ServiceCredentialValidatorTest {

    private static KeyPair keyPair;
    private static KeyPair otherKeyPair;

    private final TrustedServiceRepository repo = mock(TrustedServiceRepository.class);
    private final ServiceCredentialValidator validator = new ServiceCredentialValidator(repo);

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
    }

    @Test
    void verifiesValidAssertionAndExtractsClaims() {
        String jwt = assertion(keyPair.getPrivate(), "ACCOUNTING", "goaml",
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS));

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
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS));

        assertThatThrownBy(() -> validator.verify(SourceSystem.ACCOUNTING, jwt))
                .isInstanceOf(ServiceCredentialException.class)
                .hasMessageContaining("Invalid service assertion");
    }

    @Test
    void rejectsExpiredAssertion() {
        String jwt = assertion(keyPair.getPrivate(), "ACCOUNTING", "goaml",
                Instant.now().minus(10, ChronoUnit.MINUTES), Instant.now().minus(5, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> validator.verify(SourceSystem.ACCOUNTING, jwt))
                .isInstanceOf(ServiceCredentialException.class);
    }

    @Test
    void rejectsUnknownSource() {
        String jwt = assertion(keyPair.getPrivate(), "SCREENING", "goaml",
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS));

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
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS));

        assertThatThrownBy(() -> validator.verify(SourceSystem.SCREENING, jwt))
                .isInstanceOf(ServiceCredentialException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void rejectsWrongAudience() {
        String jwt = assertion(keyPair.getPrivate(), "ACCOUNTING", "someone-else",
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS));

        assertThatThrownBy(() -> validator.verify(SourceSystem.ACCOUNTING, jwt))
                .isInstanceOf(ServiceCredentialException.class);
    }

    @Test
    void rejectsOverlongLifetime() {
        String jwt = assertion(keyPair.getPrivate(), "ACCOUNTING", "goaml",
                Instant.now(), Instant.now().plus(30, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> validator.verify(SourceSystem.ACCOUNTING, jwt))
                .isInstanceOf(ServiceCredentialException.class)
                .hasMessageContaining("lifetime");
    }

    @Test
    void rejectsIssuerMismatch() {
        String jwt = assertion(keyPair.getPrivate(), "SCREENING", "goaml",
                Instant.now(), Instant.now().plus(60, ChronoUnit.SECONDS));

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

    // --- helpers ---

    private static String assertion(PrivateKey signingKey, String issuer, String audience,
                                    Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .issuer(issuer)
                .subject("ext-1")
                .audience().add(audience).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("email", "user@acct.test")
                .claim("org", "ORG-9")
                .claim("roles", List.of("ANALYST"))
                .signWith(signingKey, Jwts.SIG.RS256)
                .compact();
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
