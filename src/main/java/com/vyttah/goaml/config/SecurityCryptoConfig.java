package com.vyttah.goaml.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Exposes a {@link PasswordEncoder} for BCrypt password hashing.
 *
 * <p>Phase 2 sub-step 2.2 needs this for {@code TenantProvisioningService} to hash the
 * initial admin's password. Full Spring Security wiring (filter chain, JWT, RBAC) lands
 * in sub-step 2.4 and will live in a dedicated {@code SecurityConfig}.
 */
@Configuration
public class SecurityCryptoConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
