package com.vyttah.goaml.service.auth;

import com.vyttah.goaml.config.tenant.TenantIdentifierResolver;
import com.vyttah.goaml.model.dto.auth.LoginRequest;
import com.vyttah.goaml.model.dto.auth.LoginResponse;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.security.JwtService;
import com.vyttah.goaml.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * Default {@link AuthService} — verifies credentials via the {@link AuthenticationManager},
 * resolves the user's tenant schema, issues the JWT, and records the login. (Moved out of
 * {@code AuthController} so the controller stays thin and does no repository access.)
 */
@RequiredArgsConstructor
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DefaultAuthService implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtService jwtService;
    private final AuditService auditService;

    @Override
    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException ex) {
            // Either bad credentials or unknown user — same shape, no enumeration.
            throw new BadCredentialsException("Invalid email or password");
        }

        AppUser user = userRepository.findByEmail(request.email()).orElseThrow();
        String schema = user.getTenantId() == null
                ? TenantIdentifierResolver.DEFAULT_TENANT
                : tenantRepository.findById(user.getTenantId())
                    .map(Tenant::getSchemaName)
                    .orElse(TenantIdentifierResolver.DEFAULT_TENANT);

        JwtService.IssuedToken token = jwtService.issueAccessToken(user, schema);
        auditService.recordLogin(user.getId(), user.getEmail(), schema);
        return new LoginResponse(token.token(), "Bearer", token.expiresInSeconds());
    }
}
