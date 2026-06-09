package com.vyttah.goaml.repository.goamlconfig;

import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlPerson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads/writes per-tenant goAML reporting persons from the shared {@code public.tenant_goaml_person}. The
 * report service injects {@link #findByTenantIdAndActiveTrue(UUID)} (the tenant default MLRO) when a report
 * is created without an explicit reporting person; the admin API manages the list.
 */
public interface TenantGoamlPersonRepository extends JpaRepository<TenantGoamlPerson, UUID> {

    Optional<TenantGoamlPerson> findByTenantIdAndActiveTrue(UUID tenantId);

    List<TenantGoamlPerson> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
