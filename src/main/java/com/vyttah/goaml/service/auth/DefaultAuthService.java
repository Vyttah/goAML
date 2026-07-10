package com.vyttah.goaml.service.auth;

import com.vyttah.goaml.config.tenant.TenantIdentifierResolver;
import com.vyttah.goaml.model.dto.auth.LoginRequest;
import com.vyttah.goaml.model.dto.auth.LoginResponse;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.security.AuthProperties;
import com.vyttah.goaml.security.JwtService;
import com.vyttah.goaml.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Default {@link AuthService} — verifies credentials, resolves the user's tenant from the supplied
 * <em>company id</em> (the tenant slug; {@code PLATFORM} for a SUPER_ADMIN), issues the JWT, and records
 * the login.
 *
 * <p>Because email is unique only <em>within</em> a tenant, the company id is required to identify the user:
 * we resolve the tenant first, then look the user up scoped to it. Credentials are checked explicitly
 * (tenant-scoped lookup + {@link PasswordEncoder#matches}) rather than through the {@code AuthenticationManager},
 * whose {@code UserDetailsService} only receives the email and cannot disambiguate across tenants.
 */
@RequiredArgsConstructor
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DefaultAuthService implements AuthService {

    private final AppUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final AuthProperties authProperties;

    @Override
    public LoginResponse login(LoginRequest request) {
        // Native login is only available when the deployment's auth mode exposes it (native | both). On a
        // federated-only deployment this endpoint is effectively absent → 404.
        if (!authProperties.mode().nativeLoginEnabled()) {
            throw new AuthExceptions.AuthModeDisabledException(
                    "Native login is disabled in this deployment (auth mode: " + authProperties.mode() + ")");
        }

        Resolved resolved = resolve(request.companyId(), request.email());
        AppUser user = resolved.user();
        // One generic failure for unknown company / unknown user / bad password / disabled — no enumeration.
        if (user == null
                || !"ACTIVE".equals(user.getStatus())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        JwtService.IssuedToken token = jwtService.issueAccessToken(user, resolved.schema());
        auditService.recordLogin(user.getId(), user.getEmail(), resolved.schema());
        return new LoginResponse(token.token(), "Bearer", token.expiresInSeconds());
    }

    /** Resolve the tenant (schema) from the company id and load the matching user, or a null-user miss. */
    private Resolved resolve(String companyId, String email) {
        if (TenantIdentifierResolver.PLATFORM_COMPANY_ID.equalsIgnoreCase(companyId)) {
            // Platform (SUPER_ADMIN) login — no tenant; users live in the NULL-tenant partition.
            return new Resolved(userRepository.findByEmailAndTenantIdIsNull(email).orElse(null),
                    TenantIdentifierResolver.DEFAULT_TENANT);
        }
        Optional<Tenant> tenant = tenantRepository.findBySlug(companyId.toLowerCase())
                .filter(t -> "ACTIVE".equals(t.getStatus()));
        if (tenant.isEmpty()) {
            return new Resolved(null, TenantIdentifierResolver.DEFAULT_TENANT);
        }
        AppUser user = userRepository.findByTenantIdAndEmail(tenant.get().getId(), email).orElse(null);
        return new Resolved(user, tenant.get().getSchemaName());
    }

    private record Resolved(AppUser user, String schema) {}
}
