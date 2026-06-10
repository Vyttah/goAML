package com.vyttah.goaml.service.auth;

import com.vyttah.goaml.model.dto.auth.FederatedTokenRequest;
import com.vyttah.goaml.model.dto.auth.LoginResponse;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.federated.ExternalIdentity;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.federated.ExternalIdentityRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.security.AuthProperties;
import com.vyttah.goaml.security.JwtService;
import com.vyttah.goaml.security.ServiceCredentialValidator;
import com.vyttah.goaml.security.VerifiedServiceAssertion;
import com.vyttah.goaml.service.audit.AuditService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link FederatedAuthService} — verifies the service assertion, resolves (or just-in-time
 * provisions) the external user to a goAML {@code app_user} + tenant, and issues the <em>standard</em> goAML
 * JWT via the existing {@link JwtService}. Everything downstream ({@code JwtAuthFilter} → {@code TenantContext}
 * → RBAC) is unchanged; goAML stays authoritative for the user's roles (the source's role hints are advisory).
 *
 * <p>Not {@code @Transactional}: the audit write runs in its own tenant-scoped transaction (see
 * {@link com.vyttah.goaml.service.audit.DefaultAuditService}); a surrounding transaction would pin the
 * connection to the {@code public} schema before the audit could switch it. The only multi-write step —
 * JIT provisioning — is wrapped in its own {@code public}-scoped transaction for atomicity.
 */
@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DefaultFederatedAuthService implements FederatedAuthService {

    /** JIT-provisioned federated users get least privilege; an admin can elevate them later. */
    private static final String DEFAULT_ROLE = "ANALYST";

    private final ServiceCredentialValidator credentialValidator;
    private final ExternalIdentityRepository externalIdentities;
    private final TenantExternalRefRepository tenantExternalRefs;
    private final AppUserRepository appUsers;
    private final TenantRepository tenants;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final AuthProperties authProperties;
    private final TransactionTemplate transactionTemplate;

    public DefaultFederatedAuthService(ServiceCredentialValidator credentialValidator,
                                       ExternalIdentityRepository externalIdentities,
                                       TenantExternalRefRepository tenantExternalRefs,
                                       AppUserRepository appUsers,
                                       TenantRepository tenants,
                                       RoleRepository roles,
                                       PasswordEncoder passwordEncoder,
                                       JwtService jwtService,
                                       AuditService auditService,
                                       AuthProperties authProperties,
                                       PlatformTransactionManager txManager) {
        this.credentialValidator = credentialValidator;
        this.externalIdentities = externalIdentities;
        this.tenantExternalRefs = tenantExternalRefs;
        this.appUsers = appUsers;
        this.tenants = tenants;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.authProperties = authProperties;
        this.transactionTemplate = new TransactionTemplate(txManager);
    }

    @Override
    public LoginResponse exchange(FederatedTokenRequest request) {
        if (!authProperties.mode().federatedEnabled()) {
            throw new AuthExceptions.AuthModeDisabledException(
                    "Federated token-exchange is disabled in this deployment (auth mode: "
                            + authProperties.mode() + ")");
        }

        VerifiedServiceAssertion assertion =
                credentialValidator.verify(request.sourceSystem(), request.assertion());

        AppUser user = resolveOrProvision(assertion);

        if (user.getTenantId() == null) {
            throw new AuthExceptions.FederatedExchangeException(
                    "Federated identity must map to a tenant-scoped user");
        }
        String schema = tenants.findById(user.getTenantId())
                .map(Tenant::getSchemaName)
                .orElseThrow(() -> new AuthExceptions.FederatedExchangeException(
                        "Tenant not found for federated user"));

        JwtService.IssuedToken token = jwtService.issueAccessToken(user, schema);
        auditService.record("AUTH.FEDERATED_TOKEN", user.getId(), user.getEmail(), schema,
                "federated token issued via " + assertion.sourceSystem());
        return new LoginResponse(token.token(), "Bearer", token.expiresInSeconds());
    }

    private AppUser resolveOrProvision(VerifiedServiceAssertion a) {
        Optional<ExternalIdentity> mapping = externalIdentities
                .findBySourceSystemAndExternalUserId(a.sourceSystem(), a.externalUserId());
        if (mapping.isPresent()) {
            return appUsers.findById(mapping.get().getAppUserId())
                    .orElseThrow(() -> new AuthExceptions.FederatedExchangeException(
                            "Mapped goAML user no longer exists"));
        }
        if (!a.service().isJitProvisioning()) {
            throw new AuthExceptions.FederatedExchangeException(
                    "No goAML identity for this " + a.sourceSystem()
                            + " user and JIT provisioning is disabled");
        }
        return transactionTemplate.execute(status -> provision(a));
    }

    private AppUser provision(VerifiedServiceAssertion a) {
        if (a.externalOrgRef() == null || a.externalOrgRef().isBlank()) {
            throw new AuthExceptions.FederatedExchangeException(
                    "Cannot resolve tenant: the assertion carries no org reference");
        }
        UUID tenantId = tenantExternalRefs
                .findBySourceSystemAndExternalOrgRef(a.sourceSystem(), a.externalOrgRef())
                .map(TenantExternalRef::getTenantId)
                .orElseThrow(() -> new AuthExceptions.FederatedExchangeException(
                        "No goAML tenant mapped to org reference " + a.externalOrgRef()));

        String email = a.externalEmail();
        if (email == null || email.isBlank()) {
            throw new AuthExceptions.FederatedExchangeException(
                    "Cannot provision a goAML user without an email");
        }
        if (appUsers.existsByEmail(email)) {
            // Never silently link by email (account-takeover risk) — require an explicit external_identity map.
            throw new AuthExceptions.FederatedExchangeException(
                    "A goAML user already exists with email " + email
                            + "; map the external identity explicitly");
        }
        // The source may declare the role its users land with (e.g. the AML cockpit → MLRO); else least-priv.
        String declaredRole = a.service().getDefaultRole();
        String roleName = (declaredRole == null || declaredRole.isBlank()) ? DEFAULT_ROLE : declaredRole;
        Role role = roles.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Missing platform role " + roleName));

        AppUser user = new AppUser(UUID.randomUUID(), tenantId, email,
                // Unusable password — federated users authenticate only via token-exchange, never /login.
                passwordEncoder.encode(UUID.randomUUID().toString()),
                localPart(email), "Federated", "ACTIVE");
        user.addRole(role);
        appUsers.save(user);

        externalIdentities.save(new ExternalIdentity(UUID.randomUUID(), a.sourceSystem(),
                a.externalUserId(), email, user.getId()));
        return user;
    }

    private static String localPart(String email) {
        String local = email.substring(0, email.indexOf('@') < 0 ? email.length() : email.indexOf('@'));
        return local.length() > 100 ? local.substring(0, 100) : local;
    }
}
