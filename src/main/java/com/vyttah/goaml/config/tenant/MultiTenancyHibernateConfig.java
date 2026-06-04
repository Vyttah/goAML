package com.vyttah.goaml.config.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Wires our schema-per-tenant beans into Hibernate.
 *
 * <p>Setting {@code spring.jpa.properties.hibernate.multiTenancy=SCHEMA} alone isn't enough —
 * Hibernate needs the actual bean instances under
 * {@link AvailableSettings#MULTI_TENANT_CONNECTION_PROVIDER} and
 * {@link AvailableSettings#MULTI_TENANT_IDENTIFIER_RESOLVER}. A
 * {@link HibernatePropertiesCustomizer} is Spring Boot's supported hook for that.
 */
@Configuration
public class MultiTenancyHibernateConfig implements HibernatePropertiesCustomizer {

    private final MultiTenantConnectionProvider<String> connectionProvider;
    private final CurrentTenantIdentifierResolver<String> tenantResolver;

    public MultiTenancyHibernateConfig(MultiTenantConnectionProvider<String> connectionProvider,
                                       CurrentTenantIdentifierResolver<String> tenantResolver) {
        this.connectionProvider = connectionProvider;
        this.tenantResolver = tenantResolver;
    }

    @Override
    public void customize(java.util.Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
    }
}
