package com.vyttah.goaml.mcp.tool;

import com.vyttah.goaml.mcp.McpIdentity;
import com.vyttah.goaml.model.dto.admin.AdminViews.CreateUserRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigView;
import com.vyttah.goaml.model.dto.admin.AdminViews.TenantView;
import com.vyttah.goaml.model.dto.admin.AdminViews.UserView;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.service.admin.AdminService;
import jakarta.validation.Validator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Administrative MCP tools, role-gated exactly like {@code AdminController}: tenant provisioning + listing are
 * <strong>SUPER_ADMIN</strong>; user management + goAML B2B config are <strong>TENANT_ADMIN</strong> and
 * always scoped to the caller's own tenant (the tenant id comes from {@link McpIdentity}, never a parameter)
 * — a tenant admin can never touch another tenant. The goAML-config views expose only references
 * ({@code baseUrl}, {@code secretsPath}); FIU credentials live in Secrets Manager and never pass through here.
 *
 * <p>Requests are bean-validated (mirroring the REST {@code @Valid}); a violation throws with a clear message.
 */
@Component
public class AdminTools {

    private final AdminService adminService;
    private final Validator validator;

    public AdminTools(AdminService adminService, Validator validator) {
        this.adminService = adminService;
        this.validator = validator;
    }

    // ----- tenants (SUPER_ADMIN) -----

    @Tool(name = "goaml_provision_tenant",
            description = "Provision a new client tenant (its schema + an initial TENANT_ADMIN user). "
                    + "Requires the SUPER_ADMIN role.")
    public TenantView provisionTenant(TenantProvisioningRequest request) {
        McpIdentity.requireAnyRole("SUPER_ADMIN");
        validate(request);
        return TenantView.from(adminService.provisionTenant(request));
    }

    @Tool(name = "goaml_list_tenants",
            description = "List all client tenants on the platform (id, slug, name, jurisdiction, status). "
                    + "Read-only. Requires the SUPER_ADMIN role.")
    public List<TenantView> listTenants() {
        McpIdentity.requireAnyRole("SUPER_ADMIN");
        return adminService.listTenants().stream().map(TenantView::from).toList();
    }

    // ----- users in the caller's own tenant (TENANT_ADMIN) -----

    @Tool(name = "goaml_create_user",
            description = "Create a user in YOUR tenant with a role (ANALYST/MLRO/TENANT_ADMIN). Requires the "
                    + "TENANT_ADMIN role. The password is set here — handle it carefully.")
    public UserView createUser(CreateUserRequest request) {
        McpIdentity.Identity identity = McpIdentity.requireAnyRole("TENANT_ADMIN");
        validate(request);
        return UserView.from(adminService.createUser(identity.tenantId(), request));
    }

    @Tool(name = "goaml_list_users",
            description = "List the users in YOUR tenant (id, email, name, roles, status). Read-only. "
                    + "Requires the TENANT_ADMIN role.")
    public List<UserView> listUsers() {
        McpIdentity.Identity identity = McpIdentity.requireAnyRole("TENANT_ADMIN");
        return adminService.listUsers(identity.tenantId()).stream().map(UserView::from).toList();
    }

    // ----- goAML B2B config for the caller's own tenant (TENANT_ADMIN) -----

    @Tool(name = "goaml_get_goaml_config",
            description = "Get YOUR tenant's goAML B2B configuration (jurisdiction, rentity_id, base URL, "
                    + "secrets path reference, auth mode — NOT the credentials). Read-only. Requires TENANT_ADMIN.")
    public GoamlConfigView getGoamlConfig() {
        McpIdentity.Identity identity = McpIdentity.requireAnyRole("TENANT_ADMIN");
        return GoamlConfigView.from(adminService.getGoamlConfig(identity.tenantId()));
    }

    @Tool(name = "goaml_set_goaml_config",
            description = "Create or update YOUR tenant's goAML B2B configuration (jurisdiction, rentity_id, "
                    + "base URL, secrets path reference, auth mode). The secrets path is a REFERENCE to AWS "
                    + "Secrets Manager — never put real FIU credentials here. Requires TENANT_ADMIN.")
    public GoamlConfigView setGoamlConfig(GoamlConfigRequest request) {
        McpIdentity.Identity identity = McpIdentity.requireAnyRole("TENANT_ADMIN");
        validate(request);
        return GoamlConfigView.from(adminService.upsertGoamlConfig(identity.tenantId(), request));
    }

    private <T> void validate(T request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException("Invalid request: " + detail);
        }
    }
}
