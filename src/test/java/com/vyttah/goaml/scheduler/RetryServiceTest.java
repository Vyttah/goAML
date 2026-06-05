package com.vyttah.goaml.scheduler;

import com.vyttah.goaml.b2b.error.B2bAuthException;
import com.vyttah.goaml.b2b.error.B2bTransportException;
import com.vyttah.goaml.b2b.error.B2bValidationException;
import com.vyttah.goaml.config.scheduler.SchedulerProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RetryService}: a recording {@link Sleeper} (no real waits) lets us assert both the
 * retry behaviour and the number of backoff sleeps deterministically.
 */
class RetryServiceTest {

    private final AtomicInteger sleeps = new AtomicInteger();
    private final Sleeper recordingSleeper = millis -> sleeps.incrementAndGet();

    private RetryService service(int maxAttempts) {
        SchedulerProperties props = new SchedulerProperties(
                new SchedulerProperties.StatusPoll(true, Duration.ZERO),
                new SchedulerProperties.Retry(maxAttempts, Duration.ofSeconds(2)));
        return new RetryService(props, recordingSleeper);
    }

    @Test
    void returnsOnFirstSuccessWithoutSleeping() {
        String result = service(3).retryTransient("ok", () -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(sleeps).hasValue(0);
    }

    @Test
    void succeedsAfterTransientFailures() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> flaky = () -> {
            if (calls.incrementAndGet() < 3) {
                throw new B2bTransportException("blip");
            }
            return "ok";
        };

        String result = service(3).retryTransient("flaky", flaky);

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
        assertThat(sleeps).hasValue(2); // slept between the two failures
    }

    @Test
    void retriesAuthFailuresToo() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> flaky = () -> {
            if (calls.incrementAndGet() < 2) {
                throw new B2bAuthException("401");
            }
            return "ok";
        };

        assertThat(service(3).retryTransient("auth", flaky)).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    void exhaustsAttemptsThenRethrowsLast() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> always = () -> {
            calls.incrementAndGet();
            throw new B2bTransportException("down");
        };

        assertThatThrownBy(() -> service(3).retryTransient("down", always))
                .isInstanceOf(B2bTransportException.class);
        assertThat(calls).hasValue(3);
        assertThat(sleeps).hasValue(2); // 3 attempts → 2 inter-attempt sleeps
    }

    @Test
    void doesNotRetryNonTransientExceptions() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> bad = () -> {
            calls.incrementAndGet();
            throw new B2bValidationException("rejected", "<error/>");
        };

        assertThatThrownBy(() -> service(3).retryTransient("bad", bad))
                .isInstanceOf(B2bValidationException.class);
        assertThat(calls).hasValue(1); // not retried
        assertThat(sleeps).hasValue(0);
    }

    @Test
    void singleAttemptNeverSleeps() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> always = () -> {
            calls.incrementAndGet();
            throw new B2bTransportException("down");
        };

        assertThatThrownBy(() -> service(1).retryTransient("once", always))
                .isInstanceOf(B2bTransportException.class);
        assertThat(calls).hasValue(1);
        assertThat(sleeps).hasValue(0);
    }
}
