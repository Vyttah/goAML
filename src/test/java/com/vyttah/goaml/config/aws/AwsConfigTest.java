package com.vyttah.goaml.config.aws;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import static org.assertj.core.api.Assertions.assertThat;

class AwsConfigTest {

    private final AwsConfig config = new AwsConfig();

    private static AwsProperties props(String region, String endpoint) {
        return new AwsProperties(region, endpoint, new AwsProperties.S3("goaml-attachments"));
    }

    @Test
    void buildsClientWithLocalStackEndpointOverride() {
        AwsProperties props = props("me-central-1", "http://localhost:4566");

        try (SecretsManagerClient client = config.secretsManagerClient(props)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void buildsClientForRealAwsWhenNoEndpoint() {
        AwsProperties props = props("me-central-1", null);

        try (SecretsManagerClient client = config.secretsManagerClient(props)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void buildsS3ClientWithLocalStackEndpointOverride() {
        try (S3Client client = config.s3Client(props("me-central-1", "http://localhost:4566"))) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void buildsS3ClientForRealAwsWhenNoEndpoint() {
        try (S3Client client = config.s3Client(props("me-central-1", null))) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void endpointOverrideDetection() {
        assertThat(props("r", "http://x").hasEndpointOverride()).isTrue();
        assertThat(props("r", "").hasEndpointOverride()).isFalse();
        assertThat(props("r", "   ").hasEndpointOverride()).isFalse();
        assertThat(props("r", null).hasEndpointOverride()).isFalse();
    }
}
