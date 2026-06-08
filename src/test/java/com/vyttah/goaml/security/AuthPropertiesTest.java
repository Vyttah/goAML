package com.vyttah.goaml.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1.5a.1 — {@link AuthProperties} defaulting and {@link AuthMode} on-ramp predicates.
 */
class AuthPropertiesTest {

    @Test
    void defaultsToNativeWhenUnset() {
        assertThat(new AuthProperties(null).mode()).isEqualTo(AuthMode.NATIVE);
    }

    @Test
    void keepsExplicitMode() {
        assertThat(new AuthProperties(AuthMode.BOTH).mode()).isEqualTo(AuthMode.BOTH);
    }

    @Test
    void nativeLoginEnabledForNativeAndBothOnly() {
        assertThat(AuthMode.NATIVE.nativeLoginEnabled()).isTrue();
        assertThat(AuthMode.BOTH.nativeLoginEnabled()).isTrue();
        assertThat(AuthMode.FEDERATED.nativeLoginEnabled()).isFalse();
    }

    @Test
    void federatedEnabledForFederatedAndBothOnly() {
        assertThat(AuthMode.FEDERATED.federatedEnabled()).isTrue();
        assertThat(AuthMode.BOTH.federatedEnabled()).isTrue();
        assertThat(AuthMode.NATIVE.federatedEnabled()).isFalse();
    }
}
