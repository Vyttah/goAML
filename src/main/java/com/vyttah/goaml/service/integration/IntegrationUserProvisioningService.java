package com.vyttah.goaml.service.integration;

import com.vyttah.goaml.model.dto.integration.IntegrationUserUpsertRequest;
import com.vyttah.goaml.model.dto.integration.IntegrationUserUpsertResponse;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.federated.ExternalIdentity;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.federated.ExternalIdentityRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.role.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Creates/updates the goAML {@code app_user} that backs an AML user, driven <strong>explicitly</strong> by the
 * AML admin service when an AML user is granted (or has changed) a goAML role — replacing the old JIT-on-exchange
 * provisioning that matched by email and hit collisions.
 *
 * <p>The user is keyed by {@code external_identity (sourceSystem, externalUserId=AML user id)}, so re-runs update
 * the same goAML user regardless of email changes. The tenant is resolved from the AML companyId via
 * {@code tenant_external_ref}. All writes are to the shared {@code public} schema; no tenant is bound (the
 * auto-enabled {@code tenantFilter} runs unscoped), so the explicit {@code (tenantId, email)} lookups are used.
 */
@RequiredArgsConstructor
@Service
public class IntegrationUserProvisioningService {

    /** goAML roles the AML side may assign to a company user (SUPER_ADMIN is platform-only, never assignable). */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of("ANALYST", "MLRO", "TENANT_ADMIN");

    private final TenantExternalRefRepository tenantExternalRefs;
    private final AppUserRepository appUsers;
    private final ExternalIdentityRepository externalIdentities;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public IntegrationUserUpsertResponse upsert(SourceSystem source, String companyId,
                                                String externalUserId, IntegrationUserUpsertRequest req) {
        UUID tenantId = tenantExternalRefs.findBySourceSystemAndExternalOrgRef(source, companyId)
                .map(TenantExternalRef::getTenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No goAML tenant is mapped to companyId '" + companyId
                                + "' — provision the tenant before creating users"));

        boolean active = req.active() == null || req.active();
        String roleName = normalizeRole(req.role());

        Optional<ExternalIdentity> mapping =
                externalIdentities.findBySourceSystemAndExternalUserId(source, externalUserId);

        // ----- already linked: update in place -----
        if (mapping.isPresent()) {
            AppUser user = appUsers.findById(mapping.get().getAppUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "The mapped goAML user no longer exists"));
            applyProfile(user, req, roleName, active);
            appUsers.save(user);
            return view(user, roleName, active
                    ? "goAML user updated" : "goAML user disabled (no active goAML role)");
        }

        // ----- not linked, and nothing to do (no role / disabled) -----
        if (roleName == null || !active) {
            return new IntegrationUserUpsertResponse(null, req.email(), null, "NONE",
                    "No goAML user created (no goAML role assigned)");
        }

        // ----- not linked, role assigned: adopt an existing same-email user in this tenant, else create -----
        String email = requireEmail(req.email());
        Role role = requireRole(roleName);

        Optional<AppUser> existing = appUsers.findByTenantIdAndEmail(tenantId, email);
        AppUser user;
        String message;
        if (existing.isPresent()) {
            // A goAML user with this email already exists in the tenant but isn't linked yet (e.g. a legacy
            // tenant-admin). Adopt it: this is an explicit, admin-initiated link, so it's safe (unlike JIT).
            user = existing.get();
            applyProfile(user, req, roleName, true);
            appUsers.save(user);
            message = "Linked to the existing goAML user with this email";
        } else {
            user = new AppUser(UUID.randomUUID(), tenantId, email,
                    passwordEncoder.encode(passwordOrRandom(req.password())),
                    firstOr(req.firstName(), localPart(email)), lastOr(req.lastName()), "ACTIVE");
            user.addRole(role);
            appUsers.save(user);
            message = "goAML user created";
        }
        externalIdentities.save(new ExternalIdentity(
                UUID.randomUUID(), source, externalUserId, email, user.getId()));
        return view(user, roleName, message);
    }

    // ----- helpers -----

    private void applyProfile(AppUser user, IntegrationUserUpsertRequest req, String roleName, boolean active) {
        if (req.email() != null && !req.email().isBlank()) {
            user.changeEmail(req.email().trim());
        }
        if (req.firstName() != null || req.lastName() != null) {
            user.rename(firstOr(req.firstName(), user.getFirstName()), lastOr(req.lastName()));
        }
        if (roleName != null) {
            user.setSingleRole(requireRole(roleName));
        }
        if (req.password() != null && !req.password().isBlank()) {
            user.changePassword(passwordEncoder.encode(req.password()));
        }
        user.setStatus(active ? "ACTIVE" : "DISABLED");
    }

    private IntegrationUserUpsertResponse view(AppUser user, String roleName, String message) {
        return new IntegrationUserUpsertResponse(
                user.getId(), user.getEmail(), roleName, user.getStatus(), message);
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!ASSIGNABLE_ROLES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported goAML role '" + role + "' (allowed: " + ASSIGNABLE_ROLES + ")");
        }
        return normalized;
    }

    private Role requireRole(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Missing platform role " + roleName));
    }

    private String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "email is required to create a goAML user");
        }
        return email.trim();
    }

    private static String passwordOrRandom(String password) {
        // Federated users normally authenticate via token-exchange; a missing password gets an unusable one.
        return (password == null || password.isBlank()) ? UUID.randomUUID().toString() : password;
    }

    private static String firstOr(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static String lastOr(String value) {
        return (value == null || value.isBlank()) ? "-" : value.trim();
    }

    private static String localPart(String email) {
        int at = email.indexOf('@');
        String local = at < 0 ? email : email.substring(0, at);
        return local.length() > 100 ? local.substring(0, 100) : local;
    }
}
