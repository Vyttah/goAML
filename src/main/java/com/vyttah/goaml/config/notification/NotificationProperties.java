package com.vyttah.goaml.config.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Notification configuration bound from {@code goaml.notifications.*} (Phase 10).
 *
 * @param email outbound-email (Amazon SES) settings; see {@link Email}
 */
@ConfigurationProperties("goaml.notifications")
public record NotificationProperties(Email email) {

    /**
     * Outbound-email settings.
     *
     * @param enabled master switch for SES sends — <b>default {@code false}</b>. In-app notifications are
     *                always written; email is only dispatched when this is on (and a verified sender exists).
     * @param from    the verified sender address used as the SES {@code From} (e.g. {@code no-reply@…}).
     */
    public record Email(boolean enabled, String from) {
    }
}
