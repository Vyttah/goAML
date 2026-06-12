package com.vyttah.goaml.security;

import com.vyttah.goaml.repository.appuser.AppUserRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B16 — {@link UserStatusCache}: ACTIVE users pass, DISABLED/missing users fail, and verdicts are cached for
 * the TTL (one DB read, then served from cache) but re-read after an evict.
 */
class UserStatusCacheTest {

    private final AppUserRepository repo = mock(AppUserRepository.class);

    @Test
    void activeUserPassesAndIsCached() {
        UUID id = UUID.randomUUID();
        when(repo.findStatusById(id)).thenReturn(Optional.of("ACTIVE"));
        UserStatusCache cache = new UserStatusCache(repo, Duration.ofSeconds(60));

        assertThat(cache.isActive(id)).isTrue();
        assertThat(cache.isActive(id)).isTrue();

        // Cached after the first lookup — only one DB hit within the TTL.
        verify(repo, times(1)).findStatusById(id);
    }

    @Test
    void disabledUserIsRejected() {
        UUID id = UUID.randomUUID();
        when(repo.findStatusById(id)).thenReturn(Optional.of("DISABLED"));
        UserStatusCache cache = new UserStatusCache(repo, Duration.ofSeconds(60));

        assertThat(cache.isActive(id)).isFalse();
    }

    @Test
    void missingUserIsRejected() {
        UUID id = UUID.randomUUID();
        when(repo.findStatusById(id)).thenReturn(Optional.empty());
        UserStatusCache cache = new UserStatusCache(repo, Duration.ofSeconds(60));

        assertThat(cache.isActive(id)).isFalse();
    }

    @Test
    void evictForcesReread() {
        UUID id = UUID.randomUUID();
        when(repo.findStatusById(id)).thenReturn(Optional.of("ACTIVE"));
        UserStatusCache cache = new UserStatusCache(repo, Duration.ofSeconds(60));

        assertThat(cache.isActive(id)).isTrue();

        // The user is now disabled out-of-band; an evict makes the next check re-read the DB.
        when(repo.findStatusById(id)).thenReturn(Optional.of("DISABLED"));
        cache.evict(id);

        assertThat(cache.isActive(id)).isFalse();
        verify(repo, times(2)).findStatusById(id);
    }

    @Test
    void ttlExpiryForcesReread() throws InterruptedException {
        UUID id = UUID.randomUUID();
        when(repo.findStatusById(id)).thenReturn(Optional.of("ACTIVE"));
        UserStatusCache cache = new UserStatusCache(repo, Duration.ofMillis(40));

        assertThat(cache.isActive(id)).isTrue();
        Thread.sleep(70);                                   // let the cached verdict expire

        when(repo.findStatusById(id)).thenReturn(Optional.of("DISABLED"));
        assertThat(cache.isActive(id)).isFalse();
        verify(repo, times(2)).findStatusById(id);
    }
}
