package com.vyttah.goaml.security;

import com.vyttah.goaml.config.integration.OutboundIntegrationProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S2 — the outbound assertion's claim set and, above all, its <strong>wire shape</strong>.
 *
 * <p>The claims are verified by reading the serialised payload rather than by parsing with jjwt, because the
 * thing most likely to break this integration is not a wrong value but a wrong JSON <em>type</em>. goAML signs
 * with jjwt 0.12, where an audience is a {@code Set} and the modern {@code audience().add(...)} serialises
 * {@code aud} as an array even for a single value. The AML platform verifies with jjwt <strong>0.11.5</strong>,
 * whose {@code requireAudience(...)} reads {@code aud} as a {@code String}. An array would therefore fail
 * verification on every single event, and the only symptom visible anywhere would be a 401 with no detail —
 * a verifier must never echo token contents. Parsing with the local 0.12 library would happily accept both
 * shapes and prove nothing about the receiver, so these tests look at the bytes.
 */
class OutboundAssertionSignerTest {

    private static KeyPair keys;

    private static final String ORG = "acme-aml";

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();
    }

    private static String pem(KeyPair pair) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    private static OutboundIntegrationProperties properties(String privateKeyPem) {
        return new OutboundIntegrationProperties(true, privateKeyPem, "GOAML", "http://localhost:8081",
                "aml", 15000L, 120L, 50);
    }

    private static OutboundAssertionSigner signer() {
        return new OutboundAssertionSigner(properties(pem(keys)));
    }

    /** The decoded JWT payload, as the characters the receiver's JSON parser will see. */
    private static String payloadJson(String jwt) {
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("aud is a single JSON string, not an array - the AML platform's jjwt 0.11.5 cannot read an array")
    void audIsASingleStringOnTheWire() {
        String payload = payloadJson(signer().mint("u-1", ORG));

        assertThat(payload).contains("\"aud\":\"aml\"");
        assertThat(payload).doesNotContain("\"aud\":[");
    }

    @Test
    @DisplayName("the claim set is the suite contract: iss, aud, org, sub, jti, iat, exp")
    void claimSetIsTheContract() {
        String payload = payloadJson(signer().mint("u-1", ORG));

        assertThat(payload).contains("\"iss\":\"GOAML\"");
        assertThat(payload).contains("\"aud\":\"aml\"");
        assertThat(payload).contains("\"org\":\"" + ORG + "\"");
        assertThat(payload).contains("\"sub\":\"u-1\"");
        assertThat(payload).contains("\"jti\":");
        assertThat(payload).contains("\"iat\":");
        assertThat(payload).contains("\"exp\":");
    }

    @Test
    @DisplayName("a blank subject becomes 'system' - the contract's actor for a background publisher")
    void blankSubjectBecomesSystem() {
        assertThat(payloadJson(signer().mint(null, ORG)))
                .contains("\"sub\":\"" + OutboundAssertionSigner.SYSTEM_SUBJECT + "\"");
        assertThat(payloadJson(signer().mint("   ", ORG)))
                .contains("\"sub\":\"" + OutboundAssertionSigner.SYSTEM_SUBJECT + "\"");
    }

    @Test
    @DisplayName("every assertion carries a fresh jti, so the receiver's replay guard never rejects a real event")
    void jtiIsUniquePerAssertion() {
        OutboundAssertionSigner signer = signer();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            seen.add(payloadJson(signer.mint("u-1", ORG)).replaceAll(".*\"jti\":\"([^\"]+)\".*", "$1"));
        }
        assertThat(seen).hasSize(20);
    }

    @Test
    @DisplayName("the lifetime stays under the receiver's 300s cap")
    void lifetimeIsUnderTheReceiversCap() {
        String payload = payloadJson(signer().mint("u-1", ORG));
        long iat = Long.parseLong(payload.replaceAll(".*\"iat\":(\\d+).*", "$1"));
        long exp = Long.parseLong(payload.replaceAll(".*\"exp\":(\\d+).*", "$1"));

        assertThat(exp - iat).isEqualTo(120L).isLessThanOrEqualTo(300L);
    }

    @Test
    @DisplayName("the configured lifetime is honoured, so a misconfiguration over the cap is visible here")
    void configuredLifetimeIsUsed() {
        OutboundIntegrationProperties overCap = new OutboundIntegrationProperties(true, pem(keys), "GOAML",
                "http://localhost:8081", "aml", 15000L, 400L, 50);

        String payload = payloadJson(new OutboundAssertionSigner(overCap).mint("u-1", ORG));
        long iat = Long.parseLong(payload.replaceAll(".*\"iat\":(\\d+).*", "$1"));
        long exp = Long.parseLong(payload.replaceAll(".*\"exp\":(\\d+).*", "$1"));

        // Not clamped here: the receiver is the authority on its own cap, and silently shortening a
        // configured lifetime would hide the misconfiguration instead of surfacing it as a 401.
        assertThat(exp - iat).isEqualTo(400L);
    }

    @Test
    @DisplayName("no key configured - the signer is inert and says so, rather than minting something unusable")
    void withoutAKeyTheSignerIsInert() {
        OutboundAssertionSigner inert = new OutboundAssertionSigner(properties(""));

        assertThat(inert.isEnabled()).isFalse();
        assertThatThrownBy(() -> inert.mint("u-1", ORG))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("private-key-pem");
    }

    @Test
    @DisplayName("a blank org is refused - without it the receiver cannot tie the event to a tenant")
    void blankOrgIsRefused() {
        assertThatThrownBy(() -> signer().mint("u-1", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("org");
    }

    @Test
    @DisplayName("a malformed key fails at construction, not at the first event")
    void malformedKeyFailsFast() {
        assertThatThrownBy(() -> new OutboundAssertionSigner(properties("-----BEGIN PRIVATE KEY-----\nnope\n")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("private-key-pem");
    }

    @Test
    @DisplayName("usable() needs the switch, a key and a destination - any one missing means not configured")
    void usableRequiresAllThree() {
        assertThat(properties(pem(keys)).usable()).isTrue();
        assertThat(new OutboundIntegrationProperties(false, pem(keys), "GOAML", "http://x", "aml", 1L, 120L, 50)
                .usable()).isFalse();
        assertThat(new OutboundIntegrationProperties(true, "", "GOAML", "http://x", "aml", 1L, 120L, 50)
                .usable()).isFalse();
        assertThat(new OutboundIntegrationProperties(true, pem(keys), "GOAML", "", "aml", 1L, 120L, 50)
                .usable()).isFalse();
    }
}
