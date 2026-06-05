package com.vyttah.goaml.config.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Scheduler configuration bound from {@code goaml.scheduler.*} (Phase 9).
 *
 * @param statusPoll the submission-status poller settings
 * @param retry      bounded-retry settings for transient B2B failures during polling
 */
@ConfigurationProperties("goaml.scheduler")
public record SchedulerProperties(StatusPoll statusPoll, Retry retry) {

    /**
     * @param enabled  master switch for the poller (off in tests)
     * @param interval delay between poll cycles (Spring {@code Duration}, e.g. {@code 5m})
     */
    public record StatusPoll(boolean enabled, Duration interval) {
    }

    /**
     * @param maxAttempts total attempts per FIU call (1 = no retry)
     * @param backoff     fixed delay between attempts
     */
    public record Retry(int maxAttempts, Duration backoff) {
    }
}
