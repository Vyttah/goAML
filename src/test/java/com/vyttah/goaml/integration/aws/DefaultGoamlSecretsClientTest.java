package com.vyttah.goaml.integration.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link DefaultGoamlSecretsClient} — the AWS SDK client is mocked, so every branch
 * (success, optional clientCode, blank path, not-found, SDK error, empty value, bad JSON, missing fields)
 * is covered deterministically without LocalStack.
 */
class DefaultGoamlSecretsClientTest {

    @SuppressWarnings("unchecked")
    private final SecretsManagerClient sdk = mock(SecretsManagerClient.class);
    private final DefaultGoamlSecretsClient client = new DefaultGoamlSecretsClient(sdk, new ObjectMapper());

    private void stubSecret(String json) {
        when(sdk.getSecretValue(any(Consumer.class)))
                .thenReturn(GetSecretValueResponse.builder().secretString(json).build());
    }

    @Test
    void parsesFullCredentials() {
        stubSecret("{\"username\":\"re-3177\",\"password\":\"pw\",\"clientCode\":\"DXB\"}");

        GoamlCredentials creds = client.fetch("goaml/t1");

        assertThat(creds.username()).isEqualTo("re-3177");
        assertThat(creds.password()).isEqualTo("pw");
        assertThat(creds.clientCode()).isEqualTo("DXB");
    }

    @Test
    void clientCodeOptionalAndUnknownFieldsIgnored() {
        stubSecret("{\"username\":\"u\",\"password\":\"p\",\"extra\":\"ignored\"}");

        GoamlCredentials creds = client.fetch("goaml/t1");

        assertThat(creds.clientCode()).isNull();
    }

    @Test
    void blankPathThrows() {
        assertThatThrownBy(() -> client.fetch("  "))
                .isInstanceOf(SecretsAccessException.class);
        assertThatThrownBy(() -> client.fetch(null))
                .isInstanceOf(SecretsAccessException.class);
    }

    @Test
    void notFoundThrows() {
        when(sdk.getSecretValue(any(Consumer.class)))
                .thenThrow(ResourceNotFoundException.builder().message("nope").build());

        assertThatThrownBy(() -> client.fetch("goaml/missing"))
                .isInstanceOf(SecretsAccessException.class);
    }

    @Test
    void sdkErrorThrows() {
        when(sdk.getSecretValue(any(Consumer.class)))
                .thenThrow(SdkClientException.create("boom"));

        assertThatThrownBy(() -> client.fetch("goaml/t1"))
                .isInstanceOf(SecretsAccessException.class);
    }

    @Test
    void emptyValueThrows() {
        stubSecret("   ");
        assertThatThrownBy(() -> client.fetch("goaml/t1"))
                .isInstanceOf(SecretsAccessException.class);
    }

    @Test
    void invalidJsonThrows() {
        stubSecret("not json");
        assertThatThrownBy(() -> client.fetch("goaml/t1"))
                .isInstanceOf(SecretsAccessException.class);
    }

    @Test
    void missingPasswordThrows() {
        stubSecret("{\"username\":\"u\"}");
        assertThatThrownBy(() -> client.fetch("goaml/t1"))
                .isInstanceOf(SecretsAccessException.class);
    }

    @Test
    void missingUsernameThrows() {
        stubSecret("{\"password\":\"p\"}");
        assertThatThrownBy(() -> client.fetch("goaml/t1"))
                .isInstanceOf(SecretsAccessException.class);
    }
}
