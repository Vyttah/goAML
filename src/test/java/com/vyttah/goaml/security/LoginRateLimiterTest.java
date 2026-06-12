package com.vyttah.goaml.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B14 — fixed-window login/federated throttle. N attempts in a window are allowed; the (N+1)th is rejected;
 * once the window rolls over, attempts are allowed again.
 */
class LoginRateLimiterTest {

    @Test
    void allowsUpToLimitThenRejects() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("k")).isTrue();   // 1
        assertThat(limiter.tryAcquire("k")).isTrue();   // 2
        assertThat(limiter.tryAcquire("k")).isTrue();   // 3
        assertThat(limiter.tryAcquire("k")).isFalse();  // 4 — over the limit → 429
        assertThat(limiter.tryAcquire("k")).isFalse();  // stays rejected within the window
    }

    @Test
    void keysAreIndependent() {
        LoginRateLimiter limiter = new LoginRateLimiter(1, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();  // a is exhausted
        assertThat(limiter.tryAcquire("b")).isTrue();   // b is independent
    }

    @Test
    void windowResetReallowsAttempts() throws InterruptedException {
        // A tiny window so the test can observe the reset without sleeping long.
        LoginRateLimiter limiter = new LoginRateLimiter(2, Duration.ofMillis(50));

        assertThat(limiter.tryAcquire("k")).isTrue();
        assertThat(limiter.tryAcquire("k")).isTrue();
        assertThat(limiter.tryAcquire("k")).isFalse();  // exhausted in this window

        Thread.sleep(80);                               // let the window roll over

        assertThat(limiter.tryAcquire("k")).isTrue();   // new window → allowed again
    }

    @Test
    void resetClearsAllCounters() {
        LoginRateLimiter limiter = new LoginRateLimiter(1, Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("k")).isTrue();
        assertThat(limiter.tryAcquire("k")).isFalse();

        limiter.reset();

        assertThat(limiter.tryAcquire("k")).isTrue();   // counters cleared
    }
}
