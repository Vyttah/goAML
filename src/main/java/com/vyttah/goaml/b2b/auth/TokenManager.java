package com.vyttah.goaml.b2b.auth;

import com.vyttah.goaml.b2b.B2bTenantConfig;

/**
 * Manages goAML B2B session tokens ({@code SqlAuthCookie}) for {@link com.vyttah.goaml.b2b.B2bAuthMode#TOKEN}
 * tenants: authenticates against the tenant's endpoint, caches the token per tenant (in Redis), and serves
 * it to the client. On a 401 the client calls {@link #refresh} to re-authenticate, then retries once.
 */
public interface TokenManager {

    /** The cached token for this tenant, authenticating (and caching) on a miss. */
    String token(B2bTenantConfig config);

    /** Force a fresh authentication, replacing any cached token. Called after a 401. */
    String refresh(B2bTenantConfig config);

    /** Drop the cached token for a tenant (e.g. on logout / credential rotation). */
    void invalidate(String tenantId);
}
