package com.vyttah.goaml.config.aws;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.net.URI;

/**
 * AWS SDK v2 beans (Phase 6 + 8). Produces a {@link SecretsManagerClient} (Phase 6) and an
 * {@link S3Client} (Phase 8) that talk to real AWS in production (regional endpoint + the default
 * credentials chain — IRSA on EKS) and to LocalStack when {@code goaml.aws.endpoint} is set
 * (docker-compose, {@code :4566}).
 *
 * <p>When an endpoint override is present we also supply static dummy credentials, because LocalStack
 * accepts any access key and the default chain may not be configured on a developer machine. The S3
 * client additionally uses <em>path-style</em> addressing under LocalStack (it does not serve the
 * virtual-host bucket subdomains the SDK defaults to).
 */
@Configuration
@EnableConfigurationProperties(AwsProperties.class)
public class AwsConfig {

    @Bean
    public SecretsManagerClient secretsManagerClient(AwsProperties props) {
        var builder = SecretsManagerClient.builder()
                .region(Region.of(props.region()));

        if (props.hasEndpointOverride()) {
            builder.endpointOverride(URI.create(props.endpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean
    public S3Client s3Client(AwsProperties props) {
        var builder = S3Client.builder()
                .region(Region.of(props.region()));

        if (props.hasEndpointOverride()) {
            builder.endpointOverride(URI.create(props.endpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")))
                    .forcePathStyle(true);
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }
}
