package com.vyttah.goaml.integration.aws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

/**
 * Default {@link GoamlSecretsClient} backed by the AWS SDK v2 {@link SecretsManagerClient}. Reads the
 * secret's string value (KMS decryption is transparent on {@code GetSecretValue}) and parses the goAML
 * credentials JSON. Every failure mode surfaces as {@link SecretsAccessException} with no secret material.
 */
@Component
@RequiredArgsConstructor
public class DefaultGoamlSecretsClient implements GoamlSecretsClient {

    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;

    @Override
    public GoamlCredentials fetch(String secretsPath) {
        if (secretsPath == null || secretsPath.isBlank()) {
            throw new SecretsAccessException("secretsPath is blank");
        }

        String secretString;
        try {
            secretString = secretsManagerClient
                    .getSecretValue(req -> req.secretId(secretsPath))
                    .secretString();
        } catch (ResourceNotFoundException e) {
            throw new SecretsAccessException("Secret not found: " + secretsPath, e);
        } catch (SdkException e) {
            throw new SecretsAccessException("Failed to fetch secret: " + secretsPath, e);
        }

        if (secretString == null || secretString.isBlank()) {
            throw new SecretsAccessException("Secret has no string value: " + secretsPath);
        }

        GoamlCredentials credentials;
        try {
            credentials = objectMapper.readValue(secretString, GoamlCredentials.class);
        } catch (JsonProcessingException e) {
            throw new SecretsAccessException("Secret is not valid goAML credentials JSON: " + secretsPath, e);
        }

        if (isBlank(credentials.username()) || isBlank(credentials.password())) {
            throw new SecretsAccessException("Secret missing username/password: " + secretsPath);
        }
        return credentials;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
