package com.vyttah.goaml.config.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9.1: {@link SchedulerProperties} binds the {@code goaml.scheduler.*} keys (durations parsed from
 * the suffix notation used in {@code application.yml}).
 */
class SchedulerPropertiesTest {

    @Test
    void bindsStatusPollAndRetry() {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "goaml.scheduler.status-poll.enabled", "true",
                "goaml.scheduler.status-poll.interval", "5m",
                "goaml.scheduler.retry.max-attempts", "3",
                "goaml.scheduler.retry.backoff", "2s"));

        SchedulerProperties props = new Binder(source)
                .bind("goaml.scheduler", SchedulerProperties.class).get();

        assertThat(props.statusPoll().enabled()).isTrue();
        assertThat(props.statusPoll().interval()).isEqualTo(Duration.ofMinutes(5));
        assertThat(props.retry().maxAttempts()).isEqualTo(3);
        assertThat(props.retry().backoff()).isEqualTo(Duration.ofSeconds(2));
    }
}
