package com.vyttah.goaml.scheduler;

/**
 * Indirection over {@link Thread#sleep} so retry backoff is injectable — the real bean sleeps; tests pass
 * a no-op so they never actually wait.
 */
@FunctionalInterface
public interface Sleeper {

    /** Sleep for {@code millis} (no-op for {@code <= 0}); swallows/flags interruption rather than throwing. */
    void sleep(long millis);
}
