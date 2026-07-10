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
     * Look up a tenant user by (tenant, email) — the native-login path (a company id resolves the tenant
     * first). Explicit rather than relying on the {@code tenantFilter}, because login runs before any tenant
     * is bound (the filter is unscoped there) and email is only unique <em>within</em> a tenant.
     */
    Optional<AppUser> findByTenantIdAndEmail(UUID tenantId, String email);

    /** Look up a platform (SUPER_ADMIN) user — the reserved {@code PLATFORM} login; these rows have no tenant. */
    Optional<AppUser> findByEmailAndTenantIdIsNull(String email);

    /**
     * Whether the given tenant already has a user with this email — enforced explicitly on user creation
     * (SUPER_ADMIN can create into any tenant, so the caller's bound filter is not necessarily the target
     * tenant). Mirrors the per-tenant unique index.
     */
    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

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
