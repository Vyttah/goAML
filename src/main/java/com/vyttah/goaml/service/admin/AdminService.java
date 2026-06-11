package com.vyttah.goaml.service.admin;

import com.vyttah.goaml.model.dto.admin.AdminViews.CreateUserRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlPersonRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.UpdateUserRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
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
}
