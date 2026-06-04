package com.vyttah.goaml.integration.aws;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A tenant's goAML B2B credentials, parsed from the JSON document stored in AWS Secrets Manager at
 * {@code tenant_goaml_config.secrets_path}. Shape: {@code {"username","password","clientCode"?}}.
 *
 * <p>{@code clientCode} is optional (some goAML deployments scope auth to a client code). {@link #toString()}
 * is masked so credentials never leak into logs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoamlCredentials(String username, String password, String clientCode) {

    @Override
    public String toString() {
        return "GoamlCredentials[username=" + username + ", password=***, clientCode=" + clientCode + "]";
    }
}
