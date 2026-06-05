package com.vyttah.goaml.config.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AWS configuration bound from {@code goaml.aws.*}.
 *
 * @param region   AWS region for Secrets Manager + S3 (e.g. {@code me-central-1})
 * @param endpoint optional endpoint override for LocalStack (e.g. {@code http://localhost:4566});
 *                 blank/null in real AWS, where the SDK resolves the regional endpoint and uses the
 *                 default credentials chain (IRSA on EKS)
 * @param s3       S3 settings (the attachment bucket); see {@link S3}
 */
@ConfigurationProperties("goaml.aws")
public record AwsProperties(String region, String endpoint, S3 s3) {

    /**
     * S3 settings bound from {@code goaml.aws.s3.*}.
     *
     * @param bucket the bucket holding report attachments (per-tenant key prefixes)
     */
    public record S3(String bucket) {
    }

    public boolean hasEndpointOverride() {
        return endpoint != null && !endpoint.isBlank();
    }
}
