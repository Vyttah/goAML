package com.vyttah.goaml.config.tenant;

import java.util.UUID;

/**
 * Thread-local current tenant identifier.
 *
 * <p>Set by the auth filter (Phase 2 sub-step 2.4) after JWT validation; consulted by
 * {@link TenantIdentifierResolver} to drive Hibernate's schema-per-tenant routing.
 * When unset, the {@link TenantIdentifierResolver#DEFAULT_TENANT default tenant} is used,
 * which routes to {@code public} so shared-schema entities continue to work during startup,
 * provisioning, and other untenanted operations.
 *
 * <p>{@link #getTenantId() tenantId} is the companion tenant <em>UUID</em>. Unlike the schema
 * (which isolates the per-tenant {@code tenant_<id>} schemas), it drives the row-level tenant
 * filter on shared {@code public} tables such as {@code app_user} — see
 * {@link CurrentTenantFilterResolver}. It is set only where a single concrete end-user tenant is
 * bound (REST/CLI request); left {@code null} for platform/cross-tenant work, which then runs the
 * filter unscoped.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_TENANT_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static String get() {
        return CURRENT.get();
    }

    /** Bind the current tenant UUID (drives the app_user row filter). Null = unscoped/platform. */
    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT_ID.set(tenantId);
    }

    /** The current tenant UUID, or {@code null} for platform/cross-tenant (unscoped) operations. */
    public static UUID getTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    public static void clear() {
        CURRENT.remove();
        CURRENT_TENANT_ID.remove();
    }
}
