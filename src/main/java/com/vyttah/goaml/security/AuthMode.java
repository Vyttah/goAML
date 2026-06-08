package com.vyttah.goaml.security;

/**
 * Which authentication on-ramps a deployment exposes (Phase 1.5). The same binary serves the standalone
 * product and the Vyttah-suite service; this flag (bound from {@code goaml.auth.mode}) decides which paths
 * are live.
 *
 * <ul>
 *   <li>{@link #NATIVE} — only {@code POST /api/v1/auth/login} (email + password). The standalone default.</li>
 *   <li>{@link #FEDERATED} — only {@code POST /api/v1/auth/federated/token} (server-to-server token-exchange);
 *       native login is disabled.</li>
 *   <li>{@link #BOTH} — suite deployments that allow direct logins <em>and</em> token-exchange.</li>
 * </ul>
 */
public enum AuthMode {
    NATIVE,
    FEDERATED,
    BOTH;

    /** True when {@code POST /api/v1/auth/login} (email + password) is available. */
    public boolean nativeLoginEnabled() {
        return this == NATIVE || this == BOTH;
    }

    /** True when {@code POST /api/v1/auth/federated/token} (token-exchange) is available. */
    public boolean federatedEnabled() {
        return this == FEDERATED || this == BOTH;
    }
}
