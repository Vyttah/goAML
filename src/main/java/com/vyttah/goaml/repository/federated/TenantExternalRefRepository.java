package com.vyttah.goaml.repository.federated;

import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a source system's org reference to a goAML tenant from the shared
 * {@code public.tenant_external_ref}, during federated token-exchange and integration push.
 */
public interface TenantExternalRefRepository extends JpaRepository<TenantExternalRef, UUID> {

    Optional<TenantExternalRef> findBySourceSystemAndExternalOrgRef(SourceSystem sourceSystem,
                                                                    String externalOrgRef);

    /**
     * The reverse direction, added for S2: given a tenant, which org ref does a source system know it by?
     * Used to decide whether a tenant's reports are filed by the ERP at all (an {@code ACCOUNTING} ref exists)
     * and to put both company ids on a published report-status event.
     */
    Optional<TenantExternalRef> findByTenantIdAndSourceSystem(UUID tenantId, SourceSystem sourceSystem);
}
