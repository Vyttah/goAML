package com.vyttah.goaml.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * B2 — the committed fallback JWT secret must not boot a production deployment. {@link JwtService}
 * refuses to construct when the configured secret equals the known default unless a dev/test/local
 * profile is active.
 */
class JwtSecretGuardTest {

    private static final String DEFAULT = JwtService.KNOWN_DEFAULT_SECRET;
    private static final String CUSTOM = "a-unique-production-secret-that-is-at-least-32-bytes-long";

    private static JwtProperties props(String secret) {
        return new JwtProperties(secret, "goaml", 15);
    }

    @Test
    void defaultSecretInProdProfileRefusesToStart() {
        MockEnvironment prod = new MockEnvironment().withProperty("x", "y");
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtService(props(DEFAULT), prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("committed default");
    }

    @Test
    void defaultSecretWithNoActiveProfileRefusesToStart() {
        // No active profile at all (the prod posture: nothing sets dev/test/local).
        MockEnvironment noProfile = new MockEnvironment();

        assertThatThrownBy(() -> new JwtService(props(DEFAULT), noProfile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("committed default");
    }

    @Test
    void defaultSecretInDevProfileStartsOk() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");

        assertThatCode(() -> new JwtService(props(DEFAULT), dev)).doesNotThrowAnyException();
    }

    @Test
    void defaultSecretInTestProfileStartsOk() {
        MockEnvironment test = new MockEnvironment();
        test.setActiveProfiles("test");

        assertThatCode(() -> new JwtService(props(DEFAULT), test)).doesNotThrowAnyException();
    }

    @Test
    void customSecretInProdProfileStartsOk() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        JwtService service = new JwtService(props(CUSTOM), prod);
        assertThat(service).isNotNull();
    }

    @Test
    void tooShortSecretAlwaysRefusesToStart() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");

        assertThatThrownBy(() -> new JwtService(props("too-short"), dev))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
