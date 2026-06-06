package com.vyttah.goaml.integration.aws;

import com.vyttah.goaml.config.notification.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link DefaultSesClient} — the AWS SDK {@link SesV2Client} is mocked, so every branch
 * (send success with the right from/to/subject/body, blank recipient, unconfigured sender, SDK error) is
 * covered deterministically without LocalStack.
 */
class DefaultSesClientTest {

    private final SesV2Client ses = mock(SesV2Client.class);

    private DefaultSesClient client(String from) {
        return new DefaultSesClient(ses, new NotificationProperties(
                new NotificationProperties.Email(true, from)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendBuildsRequestFromConfiguredSender() {
        when(ses.sendEmail(any(Consumer.class))).thenReturn(SendEmailResponse.builder().build());

        client("no-reply@goaml.vyttah.com").send("mlro@tenant.test", "Report accepted", "Body text.");

        ArgumentCaptor<Consumer<SendEmailRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(ses).sendEmail(captor.capture());
        SendEmailRequest.Builder b = SendEmailRequest.builder();
        captor.getValue().accept(b);
        SendEmailRequest req = b.build();
        assertThat(req.fromEmailAddress()).isEqualTo("no-reply@goaml.vyttah.com");
        assertThat(req.destination().toAddresses()).containsExactly("mlro@tenant.test");
        assertThat(req.content().simple().subject().data()).isEqualTo("Report accepted");
        assertThat(req.content().simple().body().text().data()).isEqualTo("Body text.");
    }

    @Test
    void blankRecipientThrows() {
        assertThatThrownBy(() -> client("no-reply@goaml.vyttah.com").send("  ", "s", "b"))
                .isInstanceOf(SesAccessException.class);
    }

    @Test
    void unconfiguredSenderThrows() {
        assertThatThrownBy(() -> client("  ").send("mlro@tenant.test", "s", "b"))
                .isInstanceOf(SesAccessException.class)
                .hasMessageContaining("from");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sdkFailureMapsToSesAccessException() {
        when(ses.sendEmail(any(Consumer.class)))
                .thenThrow(SdkClientException.create("network down"));

        assertThatThrownBy(() -> client("no-reply@goaml.vyttah.com").send("mlro@tenant.test", "s", "b"))
                .isInstanceOf(SesAccessException.class);
    }
}
