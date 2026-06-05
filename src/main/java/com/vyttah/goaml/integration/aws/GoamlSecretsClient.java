package com.vyttah.goaml.integration.aws;

/**
 * Resolves a tenant's goAML B2B {@link GoamlCredentials} from AWS Secrets Manager. This is the per-tenant
 * identity seam every submission/poll depends on — given a {@code secrets_path} (from
 * {@code tenant_goaml_config}), it returns the credentials that tenant submits to the FIU under.
 *
 * <p>Named to avoid a simple-name clash with the AWS SDK's own {@code SecretsManagerClient}.
 */
public interface GoamlSecretsClient {

    /**
     * Fetch and parse the goAML credentials stored at {@code secretsPath}.
     *
     * @throws SecretsAccessException if the secret is missing, unreadable, not valid JSON, or incomplete
     */
    GoamlCredentials fetch(String secretsPath);
}
