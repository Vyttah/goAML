package com.vyttah.goaml.b2b;

/**
 * How a tenant authenticates to its goAML B2B endpoint, mirroring {@code tenant_goaml_config.auth_mode}.
 *
 * <ul>
 *   <li>{@link #TOKEN} — call {@code GetToken} once, cache the {@code SqlAuthCookie} session token
 *       (in Redis) and attach it to subsequent requests; re-auth on expiry/401.</li>
 *   <li>{@link #BASIC} — send HTTP Basic credentials on every request (no session token cached).</li>
 * </ul>
 */
public enum B2bAuthMode {
    TOKEN,
    BASIC
}
