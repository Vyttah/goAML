package com.vyttah.goaml.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Authentication configuration bound from {@code goaml.auth.*} (Phase 1.5).
 *
 * @param mode which auth on-ramps this deployment exposes — defaults to {@link AuthMode#NATIVE} so a
 *             standalone deployment behaves exactly as before Phase 1.5 (native login only).
 */
@ConfigurationProperties("goaml.auth")
public record AuthProperties(AuthMode mode) {

    public AuthProperties {
        if (mode == null) {
            mode = AuthMode.NATIVE;
        }
    }
}
