package com.vyttah.goaml.repository.goamlconfig;

import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads per-tenant goAML B2B config from the shared {@code public.tenant_goaml_config}. The submission
 * service resolves a tenant's {@code B2bTenantConfig} (base URL, secrets path, auth mode) + {@code rentity_id}
 * via {@link #findByTenantId(UUID)}.
 */
public interface TenantGoamlConfigRepository extends JpaRepository<TenantGoamlConfig, UUID> {

    Optional<TenantGoamlConfig> findByTenantId(UUID tenantId);
}
