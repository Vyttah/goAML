package com.vyttah.goaml.integration.aws;

import com.vyttah.goaml.config.notification.NotificationProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.AlreadyExistsException;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for {@link DefaultSesClient} against the docker-compose LocalStack ({@code ses} on
 * {@code :4566}). Tagged {@code localstack} and gated by a reachability check, so it runs when LocalStack is
 * up (`docker compose up -d localstack`) and <em>skips cleanly</em> otherwise — keeping
 * {@code ./gradlew test} green on a bare checkout. Verifies the sender identity, then sends through the
 * client (SES requires a verified {@code From}).
 *
 * <p>Note: {@code sesv2} is a LocalStack <em>Pro</em> feature — community LocalStack returns "not yet
 * implemented". This IT detects that during setup and skips cleanly, so it exercises the real client only
 * where SESv2 is genuinely available (LocalStack Pro or real AWS). The deterministic branch coverage lives
 * in {@code DefaultSesClientTest}.
 */
@Tag("localstack")
class SesClientIT {

    private static final String ENDPOINT = "http://localhost:4566";
    private static final Region REGION = Region.of("me-central-1");
    private static final String FROM = "no-reply@goaml-it.test";

    private static SesV2Client sdk;
    private static SesClient client;

    @BeforeAll
    static void setUp() {
        assumeTrue(localstackReachable(), "LocalStack not reachable on " + ENDPOINT + " — skipping");
        sdk = SesV2Client.builder()
                .region(REGION)
                .endpointOverride(URI.create(ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build();
        try {
            sdk.createEmailIdentity(req -> req.emailIdentity(FROM));
        } catch (AlreadyExistsException ignored) {
            // identity verified on a prior run — fine
        } catch (SesV2Exception e) {
            // community LocalStack: "API for service 'sesv2' not yet implemented or pro feature" — skip
            assumeTrue(false, "LocalStack SESv2 unavailable (Pro feature): " + e.awsErrorDetails().errorMessage());
        }
        var props = new NotificationProperties(new NotificationProperties.Email(true, FROM));
        client = new DefaultSesClient(sdk, props);
    }

    @AfterAll
    static void tearDown() {
        if (sdk != null) {
            sdk.close();
        }
    }

    @Test
    void sendsThroughLocalStack() {
        assertThatCode(() -> client.send("mlro@tenant-it.test", "Report accepted",
                "Your report was accepted by the FIU."))
                .doesNotThrowAnyException();
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
