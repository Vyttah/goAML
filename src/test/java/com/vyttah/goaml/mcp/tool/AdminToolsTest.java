package com.vyttah.goaml.mcp.tool;

import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.mcp.McpAccessDeniedException;
import com.vyttah.goaml.model.dto.admin.AdminViews.CreateUserRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigView;
import com.vyttah.goaml.model.dto.admin.AdminViews.TenantView;
import com.vyttah.goaml.model.dto.admin.AdminViews.UserView;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.security.UserPrincipal;
import com.vyttah.goaml.service.admin.AdminService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminTools}: the {@link AdminService} is mocked, a real bean {@link Validator} runs.
 * Verifies the SUPER_ADMIN vs TENANT_ADMIN gating, that tenant-scoped tools pass the caller's own tenant id,
 * and the bean-validation of request bodies.
 */
class AdminToolsTest {

    private static ValidatorFactory validatorFactory;

    private final AdminService adminService = mock(AdminService.class);
    private final AdminTools tools = new AdminTools(adminService, validatorFactory.getValidator());

    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticate(List<String> roles) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), TENANT_ID, "admin@demo.local", "", true, roles);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        TenantContext.set("tenant_demo");
    }

    // ----- tenants (SUPER_ADMIN) -----

    @Test
    void provisionTenantRequiresSuperAdmin() {
        authenticate(List.of("TENANT_ADMIN"));

        assertThatThrownBy(() -> tools.provisionTenant(tenantRequest()))
                .isInstanceOf(McpAccessDeniedException.class);
        verify(adminService, never()).provisionTenant(any());
    }

    @Test
    void provisionTenantDelegates() {
        authenticate(List.of("SUPER_ADMIN"));
        Tenant tenant = mock(Tenant.class);
        when(tenant.getSlug()).thenReturn("acme");
        when(adminService.provisionTenant(any())).thenReturn(tenant);

        TenantView view = tools.provisionTenant(tenantRequest());

        assertThat(view.slug()).isEqualTo("acme");
        verify(adminService).provisionTenant(any());
    }

    @Test
    void listTenantsRequiresSuperAdmin() {
        authenticate(List.of("ANALYST"));
        assertThatThrownBy(tools::listTenants).isInstanceOf(McpAccessDeniedException.class);
    }

    @Test
    void listTenantsDelegates() {
        authenticate(List.of("SUPER_ADMIN"));
        Tenant tenant = mock(Tenant.class);
        when(adminService.listTenants()).thenReturn(List.of(tenant));

        assertThat(tools.listTenants()).hasSize(1);
    }

    // ----- users (TENANT_ADMIN, own tenant) -----

    @Test
    void createUserScopedToCallerTenant() {
        authenticate(List.of("TENANT_ADMIN"));
        AppUser user = mock(AppUser.class);
        when(user.getEmail()).thenReturn("new@demo.local");
        when(user.getRoles()).thenReturn(Set.of());
        when(adminService.createUser(eq(TENANT_ID), any())).thenReturn(user);

        UserView view = tools.createUser(new CreateUserRequest("new@demo.local", "pw", "New", "User", "ANALYST"));

        assertThat(view.email()).isEqualTo("new@demo.local");
        verify(adminService).createUser(eq(TENANT_ID), any());
    }

    @Test
    void createUserValidationFails() {
        authenticate(List.of("TENANT_ADMIN"));

        assertThatThrownBy(() -> tools.createUser(new CreateUserRequest("", "", "", "", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid request");
        verify(adminService, never()).createUser(any(), any());
    }

    @Test
    void createUserRequiresTenantAdmin() {
        authenticate(List.of("ANALYST"));
        assertThatThrownBy(() -> tools.createUser(new CreateUserRequest("a@b.com", "pw", "A", "B", "ANALYST")))
                .isInstanceOf(McpAccessDeniedException.class);
    }

    @Test
    void listUsersDelegatesScopedToTenant() {
        authenticate(List.of("TENANT_ADMIN"));
        AppUser user = mock(AppUser.class);
        when(user.getRoles()).thenReturn(Set.of());
        when(adminService.listUsers(TENANT_ID)).thenReturn(List.of(user));

        assertThat(tools.listUsers()).hasSize(1);
        verify(adminService).listUsers(TENANT_ID);
    }

    // ----- goAML config (TENANT_ADMIN, own tenant) -----

    @Test
    void getGoamlConfigDelegates() {
        authenticate(List.of("TENANT_ADMIN"));
        TenantGoamlConfig config = mock(TenantGoamlConfig.class);
        when(config.getBaseUrl()).thenReturn("https://goaml.test/uae");
        when(adminService.getGoamlConfig(TENANT_ID)).thenReturn(config);

        GoamlConfigView view = tools.getGoamlConfig();

        assertThat(view.baseUrl()).isEqualTo("https://goaml.test/uae");
    }

    @Test
    void setGoamlConfigDelegatesAndValidates() {
        authenticate(List.of("TENANT_ADMIN"));
        TenantGoamlConfig config = mock(TenantGoamlConfig.class);
        when(config.getRentityId()).thenReturn(3177);
        when(adminService.upsertGoamlConfig(eq(TENANT_ID), any())).thenReturn(config);

        GoamlConfigView view = tools.setGoamlConfig(
                new GoamlConfigRequest("AE", 3177, "https://goaml.test/uae", "goaml/t1/creds", "TOKEN"));

        assertThat(view.rentityId()).isEqualTo(3177);
        verify(adminService).upsertGoamlConfig(eq(TENANT_ID), any());
    }

    @Test
    void setGoamlConfigValidationFails() {
        authenticate(List.of("TENANT_ADMIN"));

        assertThatThrownBy(() -> tools.setGoamlConfig(new GoamlConfigRequest("", null, "", "", "")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(adminService, never()).upsertGoamlConfig(any(), any());
    }

    @Test
    void goamlConfigRequiresTenantAdmin() {
        authenticate(List.of("ANALYST"));
        assertThatThrownBy(tools::getGoamlConfig).isInstanceOf(McpAccessDeniedException.class);
    }

    private static TenantProvisioningRequest tenantRequest() {
        return new TenantProvisioningRequest("acme", "ACME Bank", "AE",
                "admin@acme.com", "pw", "Ada", "Min");
    }
}
