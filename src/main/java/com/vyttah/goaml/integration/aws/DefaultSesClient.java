package com.vyttah.goaml.integration.aws;

import com.vyttah.goaml.config.notification.NotificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * Default {@link SesClient} backed by the AWS SDK v2 {@link SesV2Client}. Reads the verified sender from
 * {@code goaml.notifications.email.from}. Every failure mode surfaces as {@link SesAccessException}.
 *
 * <p>This bean exists regardless of the {@code email.enabled} flag — the gate lives in the notification
 * service (so it can still write in-app rows when email is off). When email is enabled but the sender is
 * unconfigured, {@link #send} fails fast.
 */
@Component
@RequiredArgsConstructor
public class DefaultSesClient implements SesClient {

    private final SesV2Client sesV2Client;
    private final NotificationProperties props;

    @Override
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            throw new SesAccessException("Refusing to send to a blank recipient address");
        }
        String from = from();
        try {
            sesV2Client.sendEmail(req -> req
                    .fromEmailAddress(from)
                    .destination(d -> d.toAddresses(to))
                    .content(c -> c.simple(m -> m
                            .subject(s -> s.data(subject))
                            .body(b -> b.text(t -> t.data(body))))));
        } catch (SdkException e) {
            throw new SesAccessException("Failed to send email to: " + to, e);
        }
    }

    private String from() {
        if (props.email() == null || props.email().from() == null || props.email().from().isBlank()) {
            throw new SesAccessException("goaml.notifications.email.from is not configured");
        }
        return props.email().from();
    }
}
