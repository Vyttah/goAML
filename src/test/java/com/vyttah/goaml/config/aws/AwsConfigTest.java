package com.vyttah.goaml.config.aws;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import static org.assertj.core.api.Assertions.assertThat;

class AwsConfigTest {

    private final AwsConfig config = new AwsConfig();

    @Test
    void buildsClientWithLocalStackEndpointOverride() {
        AwsProperties props = new AwsProperties("me-central-1", "http://localhost:4566");

        try (SecretsManagerClient client = config.secretsManagerClient(props)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void buildsClientForRealAwsWhenNoEndpoint() {
        AwsProperties props = new AwsProperties("me-central-1", null);

        try (SecretsManagerClient client = config.secretsManagerClient(props)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void endpointOverrideDetection() {
        assertThat(new AwsProperties("r", "http://x").hasEndpointOverride()).isTrue();
        assertThat(new AwsProperties("r", "").hasEndpointOverride()).isFalse();
        assertThat(new AwsProperties("r", "   ").hasEndpointOverride()).isFalse();
        assertThat(new AwsProperties("r", null).hasEndpointOverride()).isFalse();
    }
}
