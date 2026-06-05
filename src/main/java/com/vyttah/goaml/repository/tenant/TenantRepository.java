package com.vyttah.goaml.repository.tenant;

import com.vyttah.goaml.model.entity.tenant.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findBySlug(String slug);
    boolean existsBySlug(String slug);

    /** Tenants in the given status (the poller enumerates {@code ACTIVE} tenants to scan). */
    List<Tenant> findByStatus(String status);
}
