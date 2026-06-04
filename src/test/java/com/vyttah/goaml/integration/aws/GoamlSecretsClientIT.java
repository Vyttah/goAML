package com.vyttah.goaml.integration.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for {@link DefaultGoamlSecretsClient} against the docker-compose LocalStack
 * ({@code secretsmanager} on {@code :4566}). Tagged {@code localstack} and gated by a reachability check,
 * so it runs when LocalStack is up (`docker compose up -d localstack`) and <em>skips cleanly</em> otherwise —
 * keeping {@code ./gradlew test} green on a bare checkout.
 */
@Tag("localstack")
class GoamlSecretsClientIT {

    private static final String ENDPOINT = "http://localhost:4566";
    private static final Region REGION = Region.of("me-central-1");

    private static SecretsManagerClient sdk;
    private final GoamlSecretsClient client =
            new DefaultGoamlSecretsClient(sdk, new ObjectMapper());

    @BeforeAll
    static void setUp() {
        assumeTrue(localstackReachable(), "LocalStack not reachable on " + ENDPOINT + " — skipping");
        sdk = SecretsManagerClient.builder()
                .region(REGION)
                .endpointOverride(URI.create(ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build();
    }

    @AfterAll
    static void tearDown() {
        if (sdk != null) {
            sdk.close();
        }
    }

    @Test
    void fetchesAndParsesGoamlCredentials() {
        String path = putSecret("""
                {"username":"re-3177","password":"s3cr3t!","clientCode":"DXB"}""");

        GoamlCredentials creds = client.fetch(path);

        assertThat(creds.username()).isEqualTo("re-3177");
        assertThat(creds.password()).isEqualTo("s3cr3t!");
        assertThat(creds.clientCode()).isEqualTo("DXB");
        // toString must never leak the password
        assertThat(creds.toString()).doesNotContain("s3cr3t!").contains("***");
    }

    @Test
    void clientCodeIsOptional() {
        String path = putSecret("""
                {"username":"re-0001","password":"pw"}""");

        GoamlCredentials creds = client.fetch(path);

        assertThat(creds.username()).isEqualTo("re-0001");
        assertThat(creds.clientCode()).isNull();
    }

    @Test
    void missingSecretThrows() {
        assertThatThrownBy(() -> client.fetch("goaml/test/does-not-exist-" + UUID.randomUUID()))
                .isInstanceOf(SecretsAccessException.class);
    }

    @Test
    void invalidJsonThrows() {
        String path = putSecret("not json at all");

        assertThatThrownBy(() -> client.fetch(path))
                .isInstanceOf(SecretsAccessException.class);
    }

    @Test
    void missingPasswordThrows() {
        String path = putSecret("""
                {"username":"re-9999"}""");

        assertThatThrownBy(() -> client.fetch(path))
                .isInstanceOf(SecretsAccessException.class);
    }

    /** Create a uniquely-named secret holding {@code json} and return its name (used as the secrets path). */
    private static String putSecret(String json) {
        String name = "goaml/test/" + UUID.randomUUID();
        sdk.createSecret(req -> req.name(name).secretString(json));
        return name;
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
