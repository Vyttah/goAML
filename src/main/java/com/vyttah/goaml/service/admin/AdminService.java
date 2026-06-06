package com.vyttah.goaml.service.admin;

import com.vyttah.goaml.model.dto.admin.AdminViews.CreateUserRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
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
     * The tenant's goAML config.
     *
     * @throws AdminExceptions.GoamlConfigNotFoundException if none is set yet
     */
    TenantGoamlConfig getGoamlConfig(UUID tenantId);

    /** Create or update the tenant's goAML config (the single per-tenant row). */
    TenantGoamlConfig upsertGoamlConfig(UUID tenantId, GoamlConfigRequest request);
}
