package com.vyttah.goaml.service.admin;

import com.vyttah.goaml.model.dto.admin.AdminViews.CreateUserRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.service.audit.AuditService;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultAdminService}: repos/encoder/provisioning/audit mocked. Covers user creation
 * (role validation, duplicate email, encoding), and the goAML-config get/upsert branches.
 */
class DefaultAdminServiceTest {

    private final TenantProvisioningService provisioning = mock(TenantProvisioningService.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final TenantGoamlConfigRepository configRepository = mock(TenantGoamlConfigRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuditService auditService = mock(AuditService.class);

    private final DefaultAdminService service = new DefaultAdminService(provisioning, tenantRepository,
            appUserRepository, roleRepository, configRepository, passwordEncoder, auditService);

    private final UUID tenantId = UUID.randomUUID();

    private CreateUserRequest userReq(String email, String role) {
        return new CreateUserRequest(email, "P@ssw0rd!", "First", "Last", role);
    }

    @Test
    void createUserEncodesPasswordAndAssignsRole() {
        when(roleRepository.findByName("ANALYST")).thenReturn(Optional.of(mock(Role.class)));
        when(appUserRepository.existsByEmail("a@t.test")).thenReturn(false);
        when(passwordEncoder.encode("P@ssw0rd!")).thenReturn("ENC");

        service.createUser(tenantId, userReq("a@t.test", "analyst")); // case-insensitive role

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getValue().getEmail()).isEqualTo("a@t.test");
        assertThat(saved.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getValue().getRoles()).hasSize(1);
    }

    @Test
    void createUserRejectsDuplicateEmail() {
        when(roleRepository.findByName("MLRO")).thenReturn(Optional.of(mock(Role.class)));
        when(appUserRepository.existsByEmail("dup@t.test")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(tenantId, userReq("dup@t.test", "MLRO")))
                .isInstanceOf(AdminExceptions.UserEmailExistsException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void createUserRejectsSuperAdminAndUnknownRoles() {
        assertThatThrownBy(() -> service.createUser(tenantId, userReq("x@t.test", "SUPER_ADMIN")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createUser(tenantId, userReq("x@t.test", "WIZARD")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void getGoamlConfigThrowsWhenAbsent() {
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getGoamlConfig(tenantId))
                .isInstanceOf(AdminExceptions.GoamlConfigNotFoundException.class);
    }

    @Test
    void upsertGoamlConfigUpdatesExisting() {
        TenantGoamlConfig existing = new TenantGoamlConfig(UUID.randomUUID(), tenantId);
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        service.upsertGoamlConfig(tenantId,
                new GoamlConfigRequest("AE", 3177, "https://goaml.test/uae", "goaml/x/creds", "TOKEN"));

        ArgumentCaptor<TenantGoamlConfig> saved = ArgumentCaptor.forClass(TenantGoamlConfig.class);
        verify(configRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(saved.getValue().getRentityId()).isEqualTo(3177);
        assertThat(saved.getValue().getBaseUrl()).isEqualTo("https://goaml.test/uae");
        assertThat(saved.getValue().getAuthMode()).isEqualTo("TOKEN");
    }

    @Test
    void upsertGoamlConfigCreatesWhenAbsent() {
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        service.upsertGoamlConfig(tenantId,
                new GoamlConfigRequest("AE", 100, "https://goaml.test/uae", "goaml/y/creds", "TOKEN"));

        ArgumentCaptor<TenantGoamlConfig> saved = ArgumentCaptor.forClass(TenantGoamlConfig.class);
        verify(configRepository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getValue().getRentityId()).isEqualTo(100);
    }
}
