package com.vyttah.goaml.repository.appuser;

import com.vyttah.goaml.model.entity.appuser.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * B6: re-read a user row {@code FOR UPDATE} so the reference-checks → delete sequence is serialized
     * against a concurrent admin op on the same user (closes the check-then-delete TOCTOU race). Must run
     * inside the transactional {@code deleteUser} call.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") UUID id);

    /**
     * The user's status only ({@code ACTIVE}/{@code DISABLED}), or empty if the user no longer exists.
     * A cheap projection used by {@code JwtAuthFilter} (B16) to reject a still-valid token held by a
     * disabled/deleted user without loading the full entity + roles.
     */
    @Query("select u.status from AppUser u where u.id = :id")
    Optional<String> findStatusById(@Param("id") UUID id);

    /**
     * Users of a tenant in a given status holding a given role — used by notifications (Phase 10) to
     * resolve a tenant's active MLRO recipients. {@code app_user} is in the shared {@code public} schema,
     * so this is independent of the bound tenant {@code search_path}.
     */
    List<AppUser> findByTenantIdAndStatusAndRoles_Name(UUID tenantId, String status, String roleName);

    /** All users of a tenant — used by the admin user-management UI (Phase 13.2). */
    List<AppUser> findByTenantId(UUID tenantId);
}
