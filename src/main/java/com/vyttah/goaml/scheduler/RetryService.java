package com.vyttah.goaml.scheduler;

import com.vyttah.goaml.b2b.error.B2bAuthException;
import com.vyttah.goaml.b2b.error.B2bTransportException;
import com.vyttah.goaml.config.scheduler.SchedulerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Bounded retry for <em>transient</em> goAML B2B failures during polling. Only {@link B2bTransportException}
 * and {@link B2bAuthException} are retried (network blips / token expiry); a {@code B2bValidationException}
 * or any other exception is <strong>not</strong> retried and propagates immediately — those are not made
 * better by waiting. Attempt count + backoff come from {@code goaml.scheduler.retry.*}; the actual wait goes
 * through an injected {@link Sleeper} so tests don't sleep.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class RetryService {

    private final SchedulerProperties properties;
    private final Sleeper sleeper;

    /**
     * Run {@code call}, retrying transient B2B failures up to {@code retry.max-attempts} with a fixed
     * backoff between attempts. Rethrows the last transient failure once attempts are exhausted.
     *
     * @param label short description for logs (e.g. {@code "poll report <id>"})
     */
    public <T> T retryTransient(String label, Supplier<T> call) {
        int maxAttempts = Math.max(1, properties.retry().maxAttempts());
        long backoffMs = properties.retry().backoff().toMillis();
        RuntimeException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (B2bTransportException | B2bAuthException e) {
                last = e;
                if (attempt < maxAttempts) {
                    log.warn("{} — transient failure on attempt {}/{}: {}; retrying",
                            label, attempt, maxAttempts, e.getMessage());
                    sleeper.sleep(backoffMs);
                }
            }
        }
        log.warn("{} — giving up after {} attempt(s)", label, maxAttempts);
        throw last;
    }
}
