package com.vyttah.goaml.config.scheduler;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling (Phase 9) and binds {@link SchedulerProperties}. The submission-status poller
 * ({@code scheduler/SubmissionStatusPoller}) runs on a {@code @Scheduled} loop; this config turns the
 * scheduling subsystem on and exposes the {@code goaml.scheduler.*} settings.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SchedulerProperties.class)
public class SchedulerConfig {
}
