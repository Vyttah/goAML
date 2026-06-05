package com.vyttah.goaml.integration.aws;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoamlCredentialsTest {

    @Test
    void toStringMasksThePassword() {
        GoamlCredentials creds = new GoamlCredentials("re-3177", "super-secret", "DXB");

        String s = creds.toString();

        assertThat(s).contains("re-3177").contains("DXB").contains("***");
        assertThat(s).doesNotContain("super-secret");
    }
}
