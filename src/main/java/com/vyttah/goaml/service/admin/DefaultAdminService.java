package com.vyttah.goaml.service.admin;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.model.dto.admin.AdminViews.CreateUserRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigRequest;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.service.audit.AuditService;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Default {@link AdminService}. Tenant provisioning reuses {@link TenantProvisioningService}; user creation
 * + goAML-config upsert work over the shared {@code public} tables. Writes are audited under the caller's
 * bound tenant (platform-level SUPER_ADMIN actions are skipped by the audit service, by design).
 */
@Service
@RequiredArgsConstructor
public class DefaultAdminService implements AdminService {

    /** Roles a tenant admin may assign — never the platform role SUPER_ADMIN. */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of("ANALYST", "MLRO", "TENANT_ADMIN");

    private final TenantProvisioningService provisioningService;
    private final TenantRepository tenantRepository;
    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final TenantGoamlConfigRepository configRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Override
    public Tenant provisionTenant(TenantProvisioningRequest request) {
        Tenant tenant = provisioningService.provision(request);
        auditService.record("ADMIN.TENANT_PROVISION", null, null, TenantContext.get(),
                "provisioned tenant " + tenant.getSlug());
        return tenant;
    }

    @Override
    public List<Tenant> listTenants() {
        return tenantRepository.findAll();
    }

    @Override
    public AppUser createUser(UUID tenantId, CreateUserRequest request) {
        String roleName = request.role() == null ? "" : request.role().toUpperCase();
        if (!ASSIGNABLE_ROLES.contains(roleName)) {
            throw new IllegalArgumentException(
                    "role must be one of " + ASSIGNABLE_ROLES + ", was: " + request.role());
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleName));
        if (appUserRepository.existsByEmail(request.email())) {
            throw new AdminExceptions.UserEmailExistsException(
                    "A user already exists with email " + request.email());
        }
        AppUser user = new AppUser(UUID.randomUUID(), tenantId, request.email(),
                passwordEncoder.encode(request.password()), request.firstName(), request.lastName(), "ACTIVE");
        user.addRole(role);
        appUserRepository.save(user);
        auditService.record("ADMIN.USER_CREATE", null, null, TenantContext.get(),
                "created user " + request.email() + " [" + roleName + "]");
        return user;
    }

    @Override
    public List<AppUser> listUsers(UUID tenantId) {
        return appUserRepository.findByTenantId(tenantId);
    }

    @Override
    public TenantGoamlConfig getGoamlConfig(UUID tenantId) {
        return configRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new AdminExceptions.GoamlConfigNotFoundException(
                        "No goAML config for this tenant"));
    }

    @Override
    public TenantGoamlConfig upsertGoamlConfig(UUID tenantId, GoamlConfigRequest request) {
        TenantGoamlConfig config = configRepository.findByTenantId(tenantId)
                .orElseGet(() -> new TenantGoamlConfig(UUID.randomUUID(), tenantId));
        config.setJurisdictionCode(request.jurisdictionCode());
        config.setRentityId(request.rentityId());
        config.setBaseUrl(request.baseUrl());
        config.setSecretsPath(request.secretsPath());
        config.setAuthMode(request.authMode());
        configRepository.save(config);
        auditService.record("ADMIN.GOAML_CONFIG_SET", null, null, TenantContext.get(),
                "set goAML config (rentity " + request.rentityId() + ", " + request.jurisdictionCode() + ")");
        return config;
    }
}
