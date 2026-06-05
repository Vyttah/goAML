package com.vyttah.goaml.integration.aws;

import com.vyttah.goaml.config.aws.AwsProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for {@link DefaultS3StorageClient} against the docker-compose LocalStack ({@code s3} on
 * {@code :4566}). Tagged {@code localstack} and gated by a reachability check, so it runs when LocalStack is
 * up (`docker compose up -d localstack`) and <em>skips cleanly</em> otherwise — keeping
 * {@code ./gradlew test} green on a bare checkout.
 */
@Tag("localstack")
class S3StorageClientIT {

    private static final String ENDPOINT = "http://localhost:4566";
    private static final Region REGION = Region.of("me-central-1");
    private static final String BUCKET = "goaml-attachments-it";

    private static S3Client sdk;
    private static S3StorageClient client;

    @BeforeAll
    static void setUp() {
        assumeTrue(localstackReachable(), "LocalStack not reachable on " + ENDPOINT + " — skipping");
        sdk = S3Client.builder()
                .region(REGION)
                .endpointOverride(URI.create(ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .forcePathStyle(true)
                .build();
        try {
            sdk.createBucket(req -> req.bucket(BUCKET));
        } catch (BucketAlreadyOwnedByYouException ignored) {
            // bucket already exists from a prior run — fine
        }
        var props = new AwsProperties(REGION.id(), ENDPOINT, new AwsProperties.S3(BUCKET));
        client = new DefaultS3StorageClient(sdk, props);
    }

    @AfterAll
    static void tearDown() {
        if (sdk != null) {
            sdk.close();
        }
    }

    @Test
    void putFetchDeleteRoundTrip() {
        String key = "tenants/" + UUID.randomUUID() + "/reports/r1/a1-invoice.pdf";
        byte[] bytes = "pretend-pdf-bytes".getBytes(StandardCharsets.UTF_8);

        client.put(key, bytes, "application/pdf");

        byte[] fetched = client.fetch(key);
        assertThat(fetched).isEqualTo(bytes);

        client.delete(key);
        assertThatThrownBy(() -> client.fetch(key)).isInstanceOf(S3AccessException.class);
    }

    @Test
    void fetchMissingKeyThrows() {
        assertThatThrownBy(() -> client.fetch("tenants/none/reports/none/missing-" + UUID.randomUUID()))
                .isInstanceOf(S3AccessException.class);
    }

    private static boolean localstackReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 4566), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
