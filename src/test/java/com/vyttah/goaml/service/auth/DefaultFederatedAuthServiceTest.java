package com.vyttah.goaml.service.auth;

import com.vyttah.goaml.model.dto.auth.FederatedTokenRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.federated.ExternalIdentity;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.federated.ExternalIdentityRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.security.AuthMode;
import com.vyttah.goaml.security.AuthProperties;
import com.vyttah.goaml.security.JwtService;
import com.vyttah.goaml.security.ServiceCredentialValidator;
import com.vyttah.goaml.security.VerifiedServiceAssertion;
import com.vyttah.goaml.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 1.5a.4 — {@link DefaultFederatedAuthService} resolution / JIT-provisioning / mode-gating branches.
 */
class DefaultFederatedAuthServiceTest {

    private final ServiceCredentialValidator validator = mock(ServiceCredentialValidator.class);
    private final ExternalIdentityRepository externalIdentities = mock(ExternalIdentityRepository.class);
    private final TenantExternalRefRepository tenantRefs = mock(TenantExternalRefRepository.class);
    private final AppUserRepository appUsers = mock(AppUserRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final AuditService audit = mock(AuditService.class);

    private DefaultFederatedAuthService service(AuthMode mode) {
        PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
        when(txm.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        // Execute the callback inline so provisioning logic runs under test.
        return new DefaultFederatedAuthService(validator, externalIdentities, tenantRefs, appUsers, tenants,
                roles, encoder, jwt, audit, new AuthProperties(mode), txm);
    }

    private static VerifiedServiceAssertion assertion(boolean jit, String userId, String email, String org) {
        TrustedService svc = new TrustedService(UUID.randomUUID(), SourceSystem.ACCOUNTING,
                "acct", "pem", jit, "ACTIVE");
        return new VerifiedServiceAssertion(svc, SourceSystem.ACCOUNTING, userId, email, org,
                List.of("MLRO"));
    }

    private static Tenant tenant(UUID id) {
        return new Tenant(id, "slug", "Name", "AE", "tenant_x", "ACTIVE");
    }

    @Test
    void federatedDisabledInNativeModeNeverTouchesValidator() {
        DefaultFederatedAuthService svc = service(AuthMode.NATIVE);

        assertThatThrownBy(() -> svc.exchange(new FederatedTokenRequest(SourceSystem.ACCOUNTING, "jwt")))
                .isInstanceOf(AuthExceptions.AuthModeDisabledException.class);
        verifyNoInteractions(validator);
    }

    @Test
    void existingMappingIssuesToken() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser user = new AppUser(userId, tenantId, "u@x.com", "h", "U", "X", "ACTIVE");
        when(validator.verify(eq(SourceSystem.ACCOUNTING), any()))
                .thenReturn(assertion(false, "ext-1", "u@x.com", "ORG-1"));
        when(externalIdentities.findBySourceSystemAndExternalUserId(SourceSystem.ACCOUNTING, "ext-1"))
                .thenReturn(Optional.of(new ExternalIdentity(UUID.randomUUID(), SourceSystem.ACCOUNTING,
                        "ext-1", "u@x.com", userId)));
        when(appUsers.findById(userId)).thenReturn(Optional.of(user));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant(tenantId)));
        when(jwt.issueAccessToken(eq(user), eq("tenant_x")))
                .thenReturn(new JwtService.IssuedToken("tok", Instant.now(), 900));

        assertThat(service(AuthMode.BOTH)
                .exchange(new FederatedTokenRequest(SourceSystem.ACCOUNTING, "jwt")).accessToken())
                .isEqualTo("tok");
        verify(audit).record(eq("AUTH.FEDERATED_TOKEN"), eq(userId), eq("u@x.com"), eq("tenant_x"), any());
    }

    @Test
    void unknownUserWithJitDisabledIsForbidden() {
        when(validator.verify(eq(SourceSystem.ACCOUNTING), any()))
                .thenReturn(assertion(false, "ext-2", "n@x.com", "ORG-1"));
        when(externalIdentities.findBySourceSystemAndExternalUserId(SourceSystem.ACCOUNTING, "ext-2"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(AuthMode.FEDERATED)
                .exchange(new FederatedTokenRequest(SourceSystem.ACCOUNTING, "jwt")))
                .isInstanceOf(AuthExceptions.FederatedExchangeException.class)
                .hasMessageContaining("JIT provisioning is disabled");
    }

    @Test
    void jitProvisionsNewUserAndIssuesToken() {
        UUID tenantId = UUID.randomUUID();
        when(validator.verify(eq(SourceSystem.ACCOUNTING), any()))
                .thenReturn(assertion(true, "ext-3", "new@x.com", "ORG-1"));
        when(externalIdentities.findBySourceSystemAndExternalUserId(SourceSystem.ACCOUNTING, "ext-3"))
                .thenReturn(Optional.empty());
        when(tenantRefs.findBySourceSystemAndExternalOrgRef(SourceSystem.ACCOUNTING, "ORG-1"))
                .thenReturn(Optional.of(new TenantExternalRef(UUID.randomUUID(), tenantId,
                        SourceSystem.ACCOUNTING, "ORG-1")));
        when(appUsers.existsByTenantIdAndEmail(tenantId, "new@x.com")).thenReturn(false);
        when(roles.findByName("ANALYST")).thenReturn(Optional.of(mock(Role.class)));
        when(encoder.encode(any())).thenReturn("hashed");
        when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant(tenantId)));
        when(jwt.issueAccessToken(any(), eq("tenant_x")))
                .thenReturn(new JwtService.IssuedToken("tok", Instant.now(), 900));

        assertThat(service(AuthMode.BOTH)
                .exchange(new FederatedTokenRequest(SourceSystem.ACCOUNTING, "jwt")).accessToken())
                .isEqualTo("tok");
        verify(appUsers).save(any(AppUser.class));
        verify(externalIdentities).save(any(ExternalIdentity.class));
    }

    @Test
    void jitWithUnresolvedOrgRefIsForbidden() {
        when(validator.verify(eq(SourceSystem.ACCOUNTING), any()))
                .thenReturn(assertion(true, "ext-4", "new@x.com", "ORG-UNKNOWN"));
        when(externalIdentities.findBySourceSystemAndExternalUserId(SourceSystem.ACCOUNTING, "ext-4"))
                .thenReturn(Optional.empty());
        when(tenantRefs.findBySourceSystemAndExternalOrgRef(SourceSystem.ACCOUNTING, "ORG-UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(AuthMode.BOTH)
                .exchange(new FederatedTokenRequest(SourceSystem.ACCOUNTING, "jwt")))
                .isInstanceOf(AuthExceptions.FederatedExchangeException.class)
                .hasMessageContaining("No goAML tenant mapped");
    }

    @Test
    void jitWithExistingEmailIsForbidden() {
        UUID tenantId = UUID.randomUUID();
        when(validator.verify(eq(SourceSystem.ACCOUNTING), any()))
                .thenReturn(assertion(true, "ext-5", "taken@x.com", "ORG-1"));
        when(externalIdentities.findBySourceSystemAndExternalUserId(SourceSystem.ACCOUNTING, "ext-5"))
                .thenReturn(Optional.empty());
        when(tenantRefs.findBySourceSystemAndExternalOrgRef(SourceSystem.ACCOUNTING, "ORG-1"))
                .thenReturn(Optional.of(new TenantExternalRef(UUID.randomUUID(), tenantId,
                        SourceSystem.ACCOUNTING, "ORG-1")));
        when(appUsers.existsByTenantIdAndEmail(tenantId, "taken@x.com")).thenReturn(true);

        assertThatThrownBy(() -> service(AuthMode.BOTH)
                .exchange(new FederatedTokenRequest(SourceSystem.ACCOUNTING, "jwt")))
                .isInstanceOf(AuthExceptions.FederatedExchangeException.class)
                .hasMessageContaining("already exists with email");
    }

    @Test
    void danglingMappingIsForbidden() {
        UUID userId = UUID.randomUUID();
        when(validator.verify(eq(SourceSystem.ACCOUNTING), any()))
                .thenReturn(assertion(false, "ext-6", "u@x.com", "ORG-1"));
        when(externalIdentities.findBySourceSystemAndExternalUserId(SourceSystem.ACCOUNTING, "ext-6"))
                .thenReturn(Optional.of(new ExternalIdentity(UUID.randomUUID(), SourceSystem.ACCOUNTING,
                        "ext-6", "u@x.com", userId)));
        when(appUsers.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(AuthMode.BOTH)
                .exchange(new FederatedTokenRequest(SourceSystem.ACCOUNTING, "jwt")))
                .isInstanceOf(AuthExceptions.FederatedExchangeException.class)
                .hasMessageContaining("no longer exists");
    }
}
