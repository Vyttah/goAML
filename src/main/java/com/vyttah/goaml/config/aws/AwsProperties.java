package com.vyttah.goaml.config.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AWS configuration bound from {@code goaml.aws.*}.
 *
 * @param region   AWS region for Secrets Manager (e.g. {@code me-central-1})
 * @param endpoint optional endpoint override for LocalStack (e.g. {@code http://localhost:4566});
 *                 blank/null in real AWS, where the SDK resolves the regional endpoint and uses the
 *                 default credentials chain (IRSA on EKS)
 */
@ConfigurationProperties("goaml.aws")
public record AwsProperties(String region, String endpoint) {

    public boolean hasEndpointOverride() {
        return endpoint != null && !endpoint.isBlank();
    }
}
