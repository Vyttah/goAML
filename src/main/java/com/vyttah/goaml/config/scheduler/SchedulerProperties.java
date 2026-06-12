package com.vyttah.goaml.config.scheduler;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Scheduler configuration bound from {@code goaml.scheduler.*} (Phase 9).
 *
 * @param enabled    B8 top-level master switch (default {@code true}). Bound from env
 *                  {@code GOAML_SCHEDULER_ENABLED} via Spring relaxed binding. The Helm chart sets it
 *                  {@code false} on the web replicas (and {@code true} on a single dedicated poller pod) so
 *                  the FIU is polled exactly once per cycle regardless of replica count. When {@code false}
 *                  the {@code @Scheduled} poll method no-ops.
 * @param statusPoll the submission-status poller settings (finer-grained toggle + interval)
 * @param retry      bounded-retry settings for transient B2B failures during polling
 */
@ConfigurationProperties("goaml.scheduler")
public record SchedulerProperties(@DefaultValue("true") boolean enabled, StatusPoll statusPoll, Retry retry) {

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
