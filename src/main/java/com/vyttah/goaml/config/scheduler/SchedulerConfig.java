package com.vyttah.goaml.config.scheduler;

import com.vyttah.goaml.scheduler.Sleeper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling (Phase 9) and binds {@link SchedulerProperties}. The submission-status poller
 * ({@code scheduler/SubmissionStatusPoller}) runs on a {@code @Scheduled} loop; this config turns the
 * scheduling subsystem on, exposes the {@code goaml.scheduler.*} settings, and provides the real
 * {@link Sleeper} (retry backoff) — tests substitute a no-op.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SchedulerProperties.class)
public class SchedulerConfig {

    @Bean
    public Sleeper sleeper() {
        return millis -> {
            if (millis <= 0) {
                return;
            }
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
