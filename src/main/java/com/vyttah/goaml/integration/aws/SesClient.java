package com.vyttah.goaml.integration.aws;

/**
 * Sends a plain-text email via Amazon SES. The single outbound-email seam the notification feature
 * (Phase 10) depends on: the service layer decides <em>whether</em> to send (the
 * {@code goaml.notifications.email.enabled} gate) and <em>who</em> to send to; this client only delivers a
 * message from the configured verified sender ({@code goaml.notifications.email.from}).
 *
 * <p>Named to avoid a simple-name clash with the AWS SDK's own {@code SesV2Client}.
 */
public interface SesClient {

    /**
     * Send a plain-text email to {@code to} with the given {@code subject} and {@code body}, from the
     * configured sender address.
     *
     * @throws SesAccessException if the sender is unconfigured or the SDK send call fails
     */
    void send(String to, String subject, String body);
}
