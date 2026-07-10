package com.vyttah.goaml.config.tenant;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Supplies the {@code tenantId} parameter for the auto-enabled {@code tenantFilter} on shared
 * {@code public} tenant tables (currently {@link com.vyttah.goaml.model.entity.appuser.AppUser}).
 *
 * <p>Hibernate instantiates this and calls {@link #get()} whenever it opens a session, so every query
 * against a filtered entity is transparently scoped to {@link TenantContext#getTenantId() the current
 * tenant} — callers no longer have to remember to add an {@code AndTenantId} predicate.
 *
 * <p>When no concrete tenant is bound (login before auth, SUPER_ADMIN, background/cross-tenant jobs) the
 * context tenant is {@code null}; we return {@link #UNSCOPED}, a fixed sentinel the filter condition treats
 * as "match every row" (an equality filter cannot otherwise express "all"). Real tenant ids are random
 * UUIDv4, so a collision with the all-zero sentinel is impossible.
 */
public class CurrentTenantFilterResolver implements Supplier<UUID> {

    /** Sentinel meaning "no tenant scope" — the filter condition short-circuits to all rows for this value. */
    public static final UUID UNSCOPED = new UUID(0L, 0L);

    @Override
    public UUID get() {
        UUID tenantId = TenantContext.getTenantId();
        return tenantId == null ? UNSCOPED : tenantId;
    }
}
