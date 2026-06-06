package com.vyttah.goaml.config.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 10.1: {@link NotificationProperties} binds the {@code goaml.notifications.*} keys, and email is
 * off unless explicitly enabled.
 */
class NotificationPropertiesTest {

    @Test
    void bindsEmailSettings() {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "goaml.notifications.email.enabled", "true",
                "goaml.notifications.email.from", "no-reply@goaml.vyttah.com"));

        NotificationProperties props = new Binder(source)
                .bind("goaml.notifications", NotificationProperties.class).get();

        assertThat(props.email().enabled()).isTrue();
        assertThat(props.email().from()).isEqualTo("no-reply@goaml.vyttah.com");
    }

    @Test
    void emailDefaultsOffWhenFlagAbsent() {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "goaml.notifications.email.from", "no-reply@goaml.vyttah.com"));

        NotificationProperties props = new Binder(source)
                .bind("goaml.notifications", NotificationProperties.class).get();

        assertThat(props.email().enabled()).isFalse();
    }
}
