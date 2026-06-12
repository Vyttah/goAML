package com.vyttah.goaml.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * B14 — a lightweight in-memory fixed-window throttle for the credential-presenting auth endpoints
 * ({@code POST /api/v1/auth/login} and the federated token-exchange). Keyed by an opaque string (IP +
 * username / source), it caps attempts per fixed window and rejects the overflow so a single host cannot
 * brute-force passwords or hammer the federated exchange.
 *
 * <p>Deliberately dependency-free (no Redis/bucket4j): a {@link ConcurrentHashMap} of per-key counters,
 * each reset when its window rolls over. Good enough for a per-instance defence; with multiple replicas the
 * effective limit is {@code limit × replicas}, which is acceptable for this control. Stale entries are
 * pruned opportunistically so the map can't grow unbounded.
 */
@Component
public class LoginRateLimiter {

    private final int maxAttemptsPerWindow;
    private final Duration window;
    private final Map<String, Window> counters = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${goaml.auth.rate-limit.max-attempts-per-window:10}") int maxAttemptsPerWindow,
            @Value("${goaml.auth.rate-limit.window:PT1M}") Duration window) {
        this.maxAttemptsPerWindow = maxAttemptsPerWindow;
        this.window = window;
    }

    /**
     * Registers one attempt for {@code key} and returns {@code true} if it is within the limit, {@code false}
     * if the limit for the current window is already exhausted (the caller should respond 429).
     */
    public boolean tryAcquire(String key) {
        Instant now = Instant.now();
        pruneIfStale(now);
        Window w = counters.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired(now, window)) {
                return new Window(now);
            }
            return existing;
        });
        return w.count.incrementAndGet() <= maxAttemptsPerWindow;
    }

    /** Clears all counters — test hook so window state never leaks between cases. */
    public void reset() {
        counters.clear();
    }

    private void pruneIfStale(Instant now) {
        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(e -> e.getValue().isExpired(now, window));
        }
    }

    private static final class Window {
        private final Instant startedAt;
        private final AtomicInteger count = new AtomicInteger(0);

        private Window(Instant startedAt) {
            this.startedAt = startedAt;
        }

        private boolean isExpired(Instant now, Duration window) {
            return now.isAfter(startedAt.plus(window));
        }
    }
}
