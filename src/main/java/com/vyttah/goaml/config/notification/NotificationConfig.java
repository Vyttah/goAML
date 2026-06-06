package com.vyttah.goaml.config.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link NotificationProperties} (Phase 10) so {@code goaml.notifications.*} is available to the
 * notification service. Email send is gated by {@code goaml.notifications.email.enabled} (default off).
 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {
}
