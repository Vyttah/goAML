package com.vyttah.goaml.repository.appuser;

import com.vyttah.goaml.model.entity.appuser.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * Users of a tenant in a given status holding a given role — used by notifications (Phase 10) to
     * resolve a tenant's active MLRO recipients. {@code app_user} is in the shared {@code public} schema,
     * so this is independent of the bound tenant {@code search_path}.
     */
    List<AppUser> findByTenantIdAndStatusAndRoles_Name(UUID tenantId, String status, String roleName);

    /** All users of a tenant — used by the admin user-management UI (Phase 13.2). */
    List<AppUser> findByTenantId(UUID tenantId);
}
