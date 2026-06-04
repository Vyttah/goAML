package com.vyttah.goaml.config.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which tenant the current session belongs to.
 *
 * <p>Reads from {@link TenantContext}. If no tenant is bound to the thread (startup, async
 * Flyway, super-admin platform queries) returns {@value #DEFAULT_TENANT} so the connection
 * provider routes to the {@code public} (shared) schema.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    public static final String DEFAULT_TENANT = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenant = TenantContext.get();
        return tenant != null ? tenant : DEFAULT_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
