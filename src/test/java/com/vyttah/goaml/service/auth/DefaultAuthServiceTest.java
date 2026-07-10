package com.vyttah.goaml.service.auth;

import com.vyttah.goaml.model.dto.auth.LoginRequest;
import com.vyttah.goaml.model.dto.auth.LoginResponse;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.security.AuthMode;
import com.vyttah.goaml.security.AuthProperties;
import com.vyttah.goaml.security.JwtService;
import com.vyttah.goaml.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
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
 * 1.5a.1 — native-login auth-mode gating + company-id-scoped credential check in {@link DefaultAuthService}.
 */
class DefaultAuthServiceTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final AuditService audit = mock(AuditService.class);

    private DefaultAuthService service(AuthMode mode) {
        return new DefaultAuthService(users, tenants, passwordEncoder, jwt, audit, new AuthProperties(mode));
    }

    @Test
    void platformCompanyIdLogsInSuperAdmin() {
        UUID id = UUID.randomUUID();
        AppUser user = new AppUser(id, null, "a@b.com", "hash", "A", "B", "ACTIVE");
        when(users.findByEmailAndTenantIdIsNull("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(jwt.issueAccessToken(eq(user), any()))
                .thenReturn(new JwtService.IssuedToken("tok", Instant.now(), 900));

        LoginResponse res = service(AuthMode.NATIVE).login(new LoginRequest("PLATFORM", "a@b.com", "pw"));

        assertThat(res.accessToken()).isEqualTo("tok");
        verify(audit).recordLogin(eq(id), eq("a@b.com"), any());
    }

    @Test
    void companyIdResolvesTenantAndScopesLookup() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Tenant tenant = new Tenant(tenantId, "acme", "Acme", "AE", "tenant_acme", "ACTIVE");
        AppUser user = new AppUser(userId, tenantId, "a@b.com", "hash", "A", "B", "ACTIVE");
        when(tenants.findBySlug("acme")).thenReturn(Optional.of(tenant));
        when(users.findByTenantIdAndEmail(tenantId, "a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(jwt.issueAccessToken(eq(user), eq("tenant_acme")))
                .thenReturn(new JwtService.IssuedToken("tok", Instant.now(), 900));

        assertThat(service(AuthMode.BOTH).login(new LoginRequest("acme", "a@b.com", "pw")).accessToken())
                .isEqualTo("tok");
        verify(audit).recordLogin(eq(userId), eq("a@b.com"), eq("tenant_acme"));
    }

    @Test
    void wrongPasswordIsRejected() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant(tenantId, "acme", "Acme", "AE", "tenant_acme", "ACTIVE");
        AppUser user = new AppUser(UUID.randomUUID(), tenantId, "a@b.com", "hash", "A", "B", "ACTIVE");
        when(tenants.findBySlug("acme")).thenReturn(Optional.of(tenant));
        when(users.findByTenantIdAndEmail(tenantId, "a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service(AuthMode.NATIVE).login(new LoginRequest("acme", "a@b.com", "pw")))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        verifyNoInteractions(jwt, audit);
    }

    @Test
    void unknownCompanyIdIsRejectedWithoutEnumeration() {
        when(tenants.findBySlug("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(AuthMode.NATIVE).login(new LoginRequest("nope", "a@b.com", "pw")))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        verifyNoInteractions(jwt, audit);
    }

    @Test
    void federatedModeDisablesNativeLoginWithoutTouchingDependencies() {
        DefaultAuthService svc = service(AuthMode.FEDERATED);

        assertThatThrownBy(() -> svc.login(new LoginRequest("acme", "a@b.com", "pw")))
                .isInstanceOf(AuthExceptions.AuthModeDisabledException.class);

        verifyNoInteractions(users, tenants, passwordEncoder, jwt, audit);
    }
}
