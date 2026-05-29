package com.vyttah.goaml.tenant;

/**
 * Thread-local current tenant identifier.
 *
 * <p>Set by the auth filter (Phase 2 sub-step 2.4) after JWT validation; consulted by
 * {@link TenantIdentifierResolver} to drive Hibernate's schema-per-tenant routing.
 * When unset, the {@link TenantIdentifierResolver#DEFAULT_TENANT default tenant} is used,
 * which routes to {@code public} so shared-schema entities continue to work during startup,
 * provisioning, and other untenanted operations.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
