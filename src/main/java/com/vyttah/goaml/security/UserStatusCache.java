package com.vyttah.goaml.security;

import com.vyttah.goaml.repository.appuser.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B16 — a tiny TTL-cached "is this user still ACTIVE?" lookup for {@link JwtAuthFilter}. A goAML access
 * token lives 15 minutes; without this, a user disabled or deleted mid-window keeps full access until the
 * token expires. The filter consults this cache on every authenticated request: a {@code DISABLED} or
 * deleted (missing) user is rejected even though the token's signature + expiry are still valid.
 *
 * <p>To avoid a DB round-trip per request, each {@code (userId → active)} verdict is cached for a short TTL
 * (default 60s) — small enough that the disable takes effect within a minute, cheap enough to be a no-op
 * for the common steady-state. {@code public.app_user} is in the shared schema, so the lookup is independent
 * of any bound tenant {@code search_path}.
 */
@Component
public class UserStatusCache {

    private static final String ACTIVE = "ACTIVE";

    private final AppUserRepository appUsers;
    private final Duration ttl;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public UserStatusCache(AppUserRepository appUsers,
                           @Value("${goaml.auth.user-status-cache-ttl:PT60S}") Duration ttl) {
        this.appUsers = appUsers;
        this.ttl = ttl;
    }

    /**
     * @return {@code true} if the user currently exists and is ACTIVE (served from cache within the TTL),
     *         {@code false} if disabled or no longer present.
     */
    public boolean isActive(UUID userId) {
        Instant now = Instant.now();
        Entry cached = cache.get(userId);
        if (cached != null && !cached.isExpired(now, ttl)) {
            return cached.active;
        }
        boolean active = appUsers.findStatusById(userId)
                .map(ACTIVE::equals)
                .orElse(false);
        cache.put(userId, new Entry(active, now));
        return active;
    }

    /** Drop a cached verdict so the next check re-reads the DB (e.g. right after an admin disable/delete). */
    public void evict(UUID userId) {
        cache.remove(userId);
    }

    /** Clears the whole cache — test hook. */
    public void clear() {
        cache.clear();
    }

    private record Entry(boolean active, Instant cachedAt) {
        boolean isExpired(Instant now, Duration ttl) {
            return now.isAfter(cachedAt.plus(ttl));
        }
    }
}
