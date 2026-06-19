package com.vyttah.goaml.service.admin;

import com.vyttah.goaml.model.dto.admin.AdminViews.CreateTenantExternalRefRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.CreateTrustedServiceRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.CreateUserRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlPersonRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.UpdateTrustedServiceRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.UpdateUserRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlPerson;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;

import java.util.List;
import java.util.UUID;

/**
 * Admin operations behind the admin REST API (Phase 13.2). Tenant management is platform-level
 * (SUPER_ADMIN); user + goAML-config management are tenant-scoped (TENANT_ADMIN over their own tenant).
 * Implemented by {@link DefaultAdminService}.
 */
public interface AdminService {

    /** Provision a new tenant (schema + initial TENANT_ADMIN). SUPER_ADMIN. */
    Tenant provisionTenant(TenantProvisioningRequest request);

    /** All tenants. SUPER_ADMIN. */
    List<Tenant> listTenants();

    /**
     * Create a user in {@code tenantId} with one role (ANALYST|MLRO|TENANT_ADMIN).
     *
     * @throws AdminExceptions.UserEmailExistsException if the email is taken
     */
    AppUser createUser(UUID tenantId, CreateUserRequest request);

    /** Users of a tenant. */
    List<AppUser> listUsers(UUID tenantId);

    /**
     * Reset a user's password (SUPER_ADMIN cross-tenant onboarding / lost-password recovery). Sets a new
     * encoded hash so the user can log in directly; existing access tokens remain valid until they expire.
     *
     * @throws AdminExceptions.UserNotFoundException if no such user in this tenant
     */
    AppUser resetUserPassword(UUID tenantId, UUID userId, String newPassword);

    /**
     * Update a user in {@code tenantId}: profile, single role, and status (ACTIVE|DISABLED). Email is immutable
     * (it is the login identity). {@code actingUserId} is the caller — used to block self-lockout (you cannot
     * disable or demote your own admin account).
     *
     * @throws AdminExceptions.UserNotFoundException if no such user in this tenant
     */
    AppUser updateUser(UUID tenantId, UUID userId, UpdateUserRequest request, UUID actingUserId);

    /**
     * Hard-delete a user from {@code tenantId}. Blocked if the user authored/reviewed any report (disable
     * instead) or if it is the caller themselves.
     *
     * @throws AdminExceptions.UserNotFoundException if no such user in this tenant
     * @throws AdminExceptions.UserReferencedException if the user is referenced by reports
     */
    void deleteUser(UUID tenantId, UUID userId, UUID actingUserId);

    /**
     * The tenant's goAML config.
     *
     * @throws AdminExceptions.GoamlConfigNotFoundException if none is set yet
     */
    TenantGoamlConfig getGoamlConfig(UUID tenantId);

    /** Create or update the tenant's goAML config (the single per-tenant row). */
    TenantGoamlConfig upsertGoamlConfig(UUID tenantId, GoamlConfigRequest request);

    /** The tenant's goAML reporting persons (newest first). The active one is the auto-injected default. */
    List<TenantGoamlPerson> listGoamlPersons(UUID tenantId);

    /** Add a goAML reporting person; if {@code active} (default), it becomes the tenant default. */
    TenantGoamlPerson createGoamlPerson(UUID tenantId, GoamlPersonRequest request);

    /**
     * Update a goAML reporting person in the caller's tenant; activating it deactivates the others.
     *
     * @throws AdminExceptions.GoamlPersonNotFoundException if no such person in this tenant
     */
    TenantGoamlPerson updateGoamlPerson(UUID tenantId, UUID personId, GoamlPersonRequest request);

    /**
     * Delete a goAML reporting person from the caller's tenant.
     *
     * @throws AdminExceptions.GoamlPersonNotFoundException if no such person in this tenant
     */
    void deleteGoamlPerson(UUID tenantId, UUID personId);

    // ----- Suite Connections (SUPER_ADMIN): trusted services + company→tenant links -----

    /** All registered trusted services (sibling apps). */
    List<TrustedService> listTrustedServices();

    /**
     * Register a sibling service's public key for federated trust.
     *
     * @throws AdminExceptions.TrustedServiceExistsException if one already exists for that source system
     */
    TrustedService createTrustedService(CreateTrustedServiceRequest request);

    /**
     * Update a trusted service's key/policy/status.
     *
     * @throws AdminExceptions.TrustedServiceNotFoundException if no such trusted service
     */
    TrustedService updateTrustedService(UUID id, UpdateTrustedServiceRequest request);

    /**
     * Delete a trusted service (revokes the sibling app's trust).
     *
     * @throws AdminExceptions.TrustedServiceNotFoundException if no such trusted service
     */
    void deleteTrustedService(UUID id);

    /** All company→tenant links. */
    List<TenantExternalRef> listTenantExternalRefs();

    /**
     * Map a sibling system's org id to a goAML tenant.
     *
     * @throws AdminExceptions.TenantNotFoundException if the tenant does not exist
     * @throws AdminExceptions.TenantExternalRefExistsException if that (source, org ref) is already mapped
     */
    TenantExternalRef createTenantExternalRef(CreateTenantExternalRefRequest request);

    /**
     * Delete a company→tenant link.
     *
     * @throws AdminExceptions.TenantExternalRefNotFoundException if no such link
     */
    void deleteTenantExternalRef(UUID id);
}
