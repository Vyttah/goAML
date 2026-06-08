package com.vyttah.goaml.service.auth;

import com.vyttah.goaml.model.dto.auth.LoginRequest;
import com.vyttah.goaml.model.dto.auth.LoginResponse;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.security.AuthMode;
import com.vyttah.goaml.security.AuthProperties;
import com.vyttah.goaml.security.JwtService;
import com.vyttah.goaml.service.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;

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
 * 1.5a.1 — native-login auth-mode gating in {@link DefaultAuthService}.
 */
class DefaultAuthServiceTest {

    private final AuthenticationManager authManager = mock(AuthenticationManager.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final JwtService jwt = mock(JwtService.class);
    private final AuditService audit = mock(AuditService.class);

    private DefaultAuthService service(AuthMode mode) {
        return new DefaultAuthService(authManager, users, tenants, jwt, audit, new AuthProperties(mode));
    }

    @Test
    void nativeModeIssuesToken() {
        UUID id = UUID.randomUUID();
        AppUser user = new AppUser(id, null, "a@b.com", "hash", "A", "B", "ACTIVE");
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(jwt.issueAccessToken(eq(user), any()))
                .thenReturn(new JwtService.IssuedToken("tok", Instant.now(), 900));

        LoginResponse res = service(AuthMode.NATIVE).login(new LoginRequest("a@b.com", "pw"));

        assertThat(res.accessToken()).isEqualTo("tok");
        verify(authManager).authenticate(any());
        verify(audit).recordLogin(eq(id), eq("a@b.com"), any());
    }

    @Test
    void bothModeAllowsNativeLogin() {
        UUID id = UUID.randomUUID();
        AppUser user = new AppUser(id, null, "a@b.com", "hash", "A", "B", "ACTIVE");
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(jwt.issueAccessToken(eq(user), any()))
                .thenReturn(new JwtService.IssuedToken("tok", Instant.now(), 900));

        assertThat(service(AuthMode.BOTH).login(new LoginRequest("a@b.com", "pw")).accessToken())
                .isEqualTo("tok");
    }

    @Test
    void federatedModeDisablesNativeLoginWithoutTouchingDependencies() {
        DefaultAuthService svc = service(AuthMode.FEDERATED);

        assertThatThrownBy(() -> svc.login(new LoginRequest("a@b.com", "pw")))
                .isInstanceOf(AuthExceptions.AuthModeDisabledException.class);

        verifyNoInteractions(authManager, users, jwt, audit);
    }
}
