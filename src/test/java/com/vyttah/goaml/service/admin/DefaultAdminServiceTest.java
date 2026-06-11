package com.vyttah.goaml.service.admin;

import com.vyttah.goaml.engine.jurisdiction.JurisdictionRegistry;
import com.vyttah.goaml.model.dto.admin.AdminViews.CreateUserRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlPersonRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.UpdateUserRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlPerson;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlPersonRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.service.audit.AuditService;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
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
    private final TenantGoamlPersonRepository personRepository = mock(TenantGoamlPersonRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuditService auditService = mock(AuditService.class);

    private final JurisdictionRegistry jurisdictionRegistry = new JurisdictionRegistry();

    private final DefaultAdminService service = new DefaultAdminService(provisioning, tenantRepository,
            appUserRepository, roleRepository, configRepository, personRepository, reportRepository,
            jurisdictionRegistry, passwordEncoder, auditService);

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

    // ----- user update / delete -----

    private AppUser existingUser(UUID id, String status) {
        AppUser u = new AppUser(id, tenantId, "u@t.test", "ENC", "Old", "Name", status);
        return u;
    }

    @Test
    void updateUserChangesProfileRoleAndStatus() {
        UUID uid = UUID.randomUUID();
        AppUser user = existingUser(uid, "ACTIVE");
        when(appUserRepository.findById(uid)).thenReturn(Optional.of(user));
        when(roleRepository.findByName("MLRO")).thenReturn(Optional.of(mock(Role.class)));

        service.updateUser(tenantId, uid, new UpdateUserRequest("New", "Name", "mlro", "disabled"),
                UUID.randomUUID());

        assertThat(user.getFirstName()).isEqualTo("New");
        assertThat(user.getStatus()).isEqualTo("DISABLED"); // normalized + actually persisted
        assertThat(user.getRoles()).hasSize(1);
        verify(appUserRepository).save(user);
    }

    @Test
    void updateUserRejectsUnknownTenantRoleOrStatus() {
        UUID uid = UUID.randomUUID();
        // unknown tenant
        when(appUserRepository.findById(uid)).thenReturn(Optional.of(existingUser(uid, "ACTIVE")));
        assertThatThrownBy(() -> service.updateUser(UUID.randomUUID(), uid,
                new UpdateUserRequest("A", "B", "MLRO", "ACTIVE"), UUID.randomUUID()))
                .isInstanceOf(AdminExceptions.UserNotFoundException.class);
        // bad role / status fail before the lookup
        assertThatThrownBy(() -> service.updateUser(tenantId, uid,
                new UpdateUserRequest("A", "B", "SUPER_ADMIN", "ACTIVE"), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateUser(tenantId, uid,
                new UpdateUserRequest("A", "B", "MLRO", "BANISHED"), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void updateUserCannotDisableOrDemoteSelf() {
        UUID uid = UUID.randomUUID();
        when(appUserRepository.findById(uid)).thenReturn(Optional.of(existingUser(uid, "ACTIVE")));
        // self + disable
        assertThatThrownBy(() -> service.updateUser(tenantId, uid,
                new UpdateUserRequest("A", "B", "TENANT_ADMIN", "DISABLED"), uid))
                .isInstanceOf(IllegalArgumentException.class);
        // self + demote off TENANT_ADMIN
        assertThatThrownBy(() -> service.updateUser(tenantId, uid,
                new UpdateUserRequest("A", "B", "ANALYST", "ACTIVE"), uid))
                .isInstanceOf(IllegalArgumentException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void deleteUserRemovesWhenUnreferenced() {
        UUID uid = UUID.randomUUID();
        when(appUserRepository.findById(uid)).thenReturn(Optional.of(existingUser(uid, "ACTIVE")));
        when(reportRepository.existsByCreatedBy(uid)).thenReturn(false);
        when(reportRepository.existsByReviewedBy(uid)).thenReturn(false);

        service.deleteUser(tenantId, uid, UUID.randomUUID());

        verify(appUserRepository).delete(any(AppUser.class));
    }

    @Test
    void deleteUserBlockedWhenReferencedByReports() {
        UUID uid = UUID.randomUUID();
        when(appUserRepository.findById(uid)).thenReturn(Optional.of(existingUser(uid, "ACTIVE")));
        when(reportRepository.existsByCreatedBy(uid)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteUser(tenantId, uid, UUID.randomUUID()))
                .isInstanceOf(AdminExceptions.UserReferencedException.class);
        verify(appUserRepository, never()).delete(any(AppUser.class));
    }

    @Test
    void deleteUserCannotDeleteSelfOrUnknownTenant() {
        UUID uid = UUID.randomUUID();
        when(appUserRepository.findById(uid)).thenReturn(Optional.of(existingUser(uid, "ACTIVE")));
        assertThatThrownBy(() -> service.deleteUser(tenantId, uid, uid))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.deleteUser(UUID.randomUUID(), uid, UUID.randomUUID()))
                .isInstanceOf(AdminExceptions.UserNotFoundException.class);
        verify(appUserRepository, never()).delete(any(AppUser.class));
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

    @Test
    void upsertGoamlConfigNormalizesAuthModeCase() {
        when(configRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        service.upsertGoamlConfig(tenantId,
                new GoamlConfigRequest("ae", 100, "https://goaml.test/uae", "goaml/y/creds", "basic"));

        ArgumentCaptor<TenantGoamlConfig> saved = ArgumentCaptor.forClass(TenantGoamlConfig.class);
        verify(configRepository).save(saved.capture());
        assertThat(saved.getValue().getAuthMode()).isEqualTo("BASIC");
    }

    @Test
    void upsertGoamlConfigRejectsUnknownAuthModeWithoutSaving() {
        assertThatThrownBy(() -> service.upsertGoamlConfig(tenantId,
                new GoamlConfigRequest("AE", 100, "https://goaml.test/uae", "goaml/y/creds", "PASSWORD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authMode");
        verify(configRepository, never()).save(any());
    }

    @Test
    void upsertGoamlConfigRejectsUnknownJurisdictionWithoutSaving() {
        assertThatThrownBy(() -> service.upsertGoamlConfig(tenantId,
                new GoamlConfigRequest("ZZ", 100, "https://goaml.test/uae", "goaml/y/creds", "TOKEN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jurisdictionCode");
        verify(configRepository, never()).save(any());
    }

    // ----- goAML reporting person -----

    private static GoamlPersonRequest personReq(String first, String last, Boolean active) {
        return new GoamlPersonRequest(first, last, "M", "SSN-1", "ID-9", "AE", "mlro@t.test", "Officer", active);
    }

    @Test
    void createGoamlPersonActiveDeactivatesOthersThenSaves() {
        TenantGoamlPerson existingActive = new TenantGoamlPerson(UUID.randomUUID(), tenantId, "Old", "Mlro");
        when(personRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(existingActive));

        service.createGoamlPerson(tenantId, personReq("New", "Mlro", true));

        assertThat(existingActive.isActive()).isFalse();          // the prior default was cleared
        verify(personRepository).saveAll(any());
        verify(personRepository).flush();
        ArgumentCaptor<TenantGoamlPerson> saved = ArgumentCaptor.forClass(TenantGoamlPerson.class);
        verify(personRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getFirstName()).isEqualTo("New");
        assertThat(saved.getValue().getNationality()).isEqualTo("AE");
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    void createGoamlPersonInactiveLeavesOthersUntouched() {
        service.createGoamlPerson(tenantId, personReq("Spare", "Mlro", false));

        verify(personRepository, never()).findByTenantIdOrderByCreatedAtDesc(tenantId);
        verify(personRepository, never()).saveAll(any());
        verify(personRepository).saveAndFlush(any());
    }

    @Test
    void updateGoamlPersonAppliesFieldsAndActivates() {
        UUID pid = UUID.randomUUID();
        TenantGoamlPerson person = new TenantGoamlPerson(pid, tenantId, "Old", "Name");
        when(personRepository.findById(pid)).thenReturn(Optional.of(person));
        when(personRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(person));

        service.updateGoamlPerson(tenantId, pid, personReq("New", "Name", true));

        assertThat(person.getFirstName()).isEqualTo("New");
        assertThat(person.getIdNumber()).isEqualTo("ID-9");
        assertThat(person.isActive()).isTrue();
        verify(personRepository).saveAndFlush(person);
    }

    @Test
    void updateGoamlPersonRejectsUnknownOrOtherTenant() {
        UUID pid = UUID.randomUUID();
        when(personRepository.findById(pid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateGoamlPerson(tenantId, pid, personReq("A", "B", null)))
                .isInstanceOf(AdminExceptions.GoamlPersonNotFoundException.class);

        TenantGoamlPerson otherTenant = new TenantGoamlPerson(pid, UUID.randomUUID(), "A", "B");
        when(personRepository.findById(pid)).thenReturn(Optional.of(otherTenant));
        assertThatThrownBy(() -> service.updateGoamlPerson(tenantId, pid, personReq("A", "B", null)))
                .isInstanceOf(AdminExceptions.GoamlPersonNotFoundException.class);
        verify(personRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteGoamlPersonRemovesWhenOwnedElseThrows() {
        UUID pid = UUID.randomUUID();
        when(personRepository.findById(pid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteGoamlPerson(tenantId, pid))
                .isInstanceOf(AdminExceptions.GoamlPersonNotFoundException.class);

        TenantGoamlPerson person = new TenantGoamlPerson(pid, tenantId, "Del", "Me");
        when(personRepository.findById(pid)).thenReturn(Optional.of(person));
        service.deleteGoamlPerson(tenantId, pid);
        verify(personRepository).delete(person);
    }

    @Test
    void listGoamlPersonsDelegatesToRepo() {
        when(personRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of());
        assertThat(service.listGoamlPersons(tenantId)).isEmpty();
    }
}
