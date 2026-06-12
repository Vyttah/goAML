package com.vyttah.goaml.service.admin;

import com.vyttah.goaml.b2b.B2bAuthMode;
import com.vyttah.goaml.config.tenant.TenantContext;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionRegistry;
import com.vyttah.goaml.model.dto.admin.AdminViews.CreateUserRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlPersonRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.UpdateUserRequest;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlPerson;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.attachment.AttachmentRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlConfigRepository;
import com.vyttah.goaml.repository.goamlconfig.TenantGoamlPersonRepository;
import com.vyttah.goaml.repository.ingestion.ImportJobRepository;
import com.vyttah.goaml.repository.notification.NotificationRepository;
import com.vyttah.goaml.repository.report.ReportRepository;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.repository.tenant.TenantRepository;
import com.vyttah.goaml.security.UserStatusCache;
import com.vyttah.goaml.service.audit.AuditService;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link AdminService}. Tenant provisioning reuses {@link TenantProvisioningService}; user creation
 * + goAML-config upsert work over the shared {@code public} tables. Writes are audited under the caller's
 * bound tenant (platform-level SUPER_ADMIN actions are skipped by the audit service, by design).
 */
@Service
@RequiredArgsConstructor
public class DefaultAdminService implements AdminService {

    /** Roles a tenant admin may assign — never the platform role SUPER_ADMIN. */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of("ANALYST", "MLRO", "TENANT_ADMIN");
    /** Valid user lifecycle states (a DISABLED user is retained for audit but cannot log in). */
    private static final Set<String> USER_STATUSES = Set.of("ACTIVE", "DISABLED");

    private final TenantProvisioningService provisioningService;
    private final TenantRepository tenantRepository;
    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final TenantGoamlConfigRepository configRepository;
    private final TenantGoamlPersonRepository personRepository;
    private final ReportRepository reportRepository;
    private final AttachmentRepository attachmentRepository;
    private final ImportJobRepository importJobRepository;
    private final NotificationRepository notificationRepository;
    private final JurisdictionRegistry jurisdictionRegistry;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final UserStatusCache userStatusCache;

    @Override
    public Tenant provisionTenant(TenantProvisioningRequest request) {
        Tenant tenant = provisioningService.provision(request);
        auditService.record("ADMIN.TENANT_PROVISION", null, null, TenantContext.get(),
                "provisioned tenant " + tenant.getSlug());
        return tenant;
    }

    @Override
    public List<Tenant> listTenants() {
        return tenantRepository.findAll();
    }

    @Override
    public AppUser createUser(UUID tenantId, CreateUserRequest request) {
        String roleName = request.role() == null ? "" : request.role().toUpperCase();
        if (!ASSIGNABLE_ROLES.contains(roleName)) {
            throw new IllegalArgumentException(
                    "role must be one of " + ASSIGNABLE_ROLES + ", was: " + request.role());
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleName));
        if (appUserRepository.existsByEmail(request.email())) {
            throw new AdminExceptions.UserEmailExistsException(
                    "A user already exists with email " + request.email());
        }
        AppUser user = new AppUser(UUID.randomUUID(), tenantId, request.email(),
                passwordEncoder.encode(request.password()), request.firstName(), request.lastName(), "ACTIVE");
        user.addRole(role);
        appUserRepository.save(user);
        auditService.record("ADMIN.USER_CREATE", null, null, TenantContext.get(),
                "created user " + request.email() + " [" + roleName + "]");
        return user;
    }

    @Override
    public List<AppUser> listUsers(UUID tenantId) {
        return appUserRepository.findByTenantId(tenantId);
    }

    @Override
    @Transactional
    public AppUser updateUser(UUID tenantId, UUID userId, UpdateUserRequest request, UUID actingUserId) {
        String roleName = request.role() == null ? "" : request.role().toUpperCase();
        if (!ASSIGNABLE_ROLES.contains(roleName)) {
            throw new IllegalArgumentException(
                    "role must be one of " + ASSIGNABLE_ROLES + ", was: " + request.role());
        }
        String status = request.status() == null ? "" : request.status().toUpperCase();
        if (!USER_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "status must be one of " + USER_STATUSES + ", was: " + request.status());
        }
        AppUser user = appUserRepository.findById(userId)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new AdminExceptions.UserNotFoundException(
                        "No user " + userId + " in this tenant"));
        // Self-lockout guard: an admin cannot disable or demote their own account (that would orphan the tenant).
        if (user.getId().equals(actingUserId) && (!"ACTIVE".equals(status) || !"TENANT_ADMIN".equals(roleName))) {
            throw new IllegalArgumentException(
                    "You cannot disable or change the role of your own admin account");
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleName));
        user.rename(request.firstName(), request.lastName());
        user.setSingleRole(role);
        user.setStatus(status);
        appUserRepository.save(user);
        // Drop the cached ACTIVE verdict so a disable takes effect on the next request, not after the TTL.
        userStatusCache.evict(userId);
        auditService.record("ADMIN.USER_UPDATE", null, null, TenantContext.get(),
                "updated user " + user.getEmail() + " [" + roleName + ", " + status + "]");
        return user;
    }

    @Override
    @Transactional
    public void deleteUser(UUID tenantId, UUID userId, UUID actingUserId) {
        // B6: lock the user row FOR UPDATE up front so the reference-checks → delete sequence is serialized
        // against a concurrent op on the same user (closes the check-then-delete TOCTOU race). No cross-schema
        // FK can backstop this (the tenant tables that reference a user live in tenant_<id>, not public).
        AppUser user = appUserRepository.findByIdForUpdate(userId)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new AdminExceptions.UserNotFoundException(
                        "No user " + userId + " in this tenant"));
        if (user.getId().equals(actingUserId)) {
            throw new IllegalArgumentException("You cannot delete your own account");
        }
        // Block a destructive delete that would orphan a reference anywhere in the tenant. We check every
        // table that soft-references a user id (no FK, because those tables are tenant-schema and the user is
        // in public). Anything referenced → block with 409 and steer the admin to disable instead.
        if (isReferencedAnywhere(userId)) {
            throw new AdminExceptions.UserReferencedException(
                    "This user is referenced by reports, attachments, imports, or notifications and cannot be "
                            + "deleted — disable the user instead");
        }
        appUserRepository.delete(user); // shared FKs (user_role, refresh_token, external_identity) cascade
        // Drop the cached ACTIVE verdict so the deleted user's live tokens are rejected immediately.
        userStatusCache.evict(userId);
        auditService.record("ADMIN.USER_DELETE", null, null, TenantContext.get(),
                "deleted user " + user.getEmail());
    }

    /** True if the user is referenced by any tenant record that would be orphaned by a hard delete (B6). */
    private boolean isReferencedAnywhere(UUID userId) {
        return reportRepository.existsByCreatedBy(userId)
                || reportRepository.existsByReviewedBy(userId)
                || attachmentRepository.existsByUploadedBy(userId)
                || importJobRepository.existsByCreatedBy(userId)
                || notificationRepository.existsByRecipientUserId(userId);
    }

    @Override
    public TenantGoamlConfig getGoamlConfig(UUID tenantId) {
        return configRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new AdminExceptions.GoamlConfigNotFoundException(
                        "No goAML config for this tenant"));
    }

    @Override
    public TenantGoamlConfig upsertGoamlConfig(UUID tenantId, GoamlConfigRequest request) {
        String authMode = validateAuthMode(request.authMode());
        validateJurisdiction(request.jurisdictionCode());

        TenantGoamlConfig config = configRepository.findByTenantId(tenantId)
                .orElseGet(() -> new TenantGoamlConfig(UUID.randomUUID(), tenantId));
        config.setJurisdictionCode(request.jurisdictionCode());
        config.setRentityId(request.rentityId());
        config.setBaseUrl(request.baseUrl());
        config.setSecretsPath(request.secretsPath());
        config.setAuthMode(authMode);
        configRepository.save(config);
        auditService.record("ADMIN.GOAML_CONFIG_SET", null, null, TenantContext.get(),
                "set goAML config (rentity " + request.rentityId() + ", " + request.jurisdictionCode() + ")");
        return config;
    }

    @Override
    public List<TenantGoamlPerson> listGoamlPersons(UUID tenantId) {
        return personRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Override
    @Transactional
    public TenantGoamlPerson createGoamlPerson(UUID tenantId, GoamlPersonRequest request) {
        boolean active = request.active() == null || request.active();
        if (active) {
            deactivateOthers(tenantId, null);
        }
        TenantGoamlPerson person = new TenantGoamlPerson(
                UUID.randomUUID(), tenantId, request.firstName(), request.lastName());
        apply(person, request);
        person.setActive(active);
        personRepository.saveAndFlush(person);
        auditService.record("ADMIN.GOAML_PERSON_CREATE", null, null, TenantContext.get(),
                "added goAML person " + person.getFirstName() + " " + person.getLastName()
                        + (active ? " (active)" : ""));
        return person;
    }

    @Override
    @Transactional
    public TenantGoamlPerson updateGoamlPerson(UUID tenantId, UUID personId, GoamlPersonRequest request) {
        TenantGoamlPerson person = personRepository.findById(personId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new AdminExceptions.GoamlPersonNotFoundException(
                        "No goAML person " + personId + " in this tenant"));
        boolean active = request.active() == null ? person.isActive() : request.active();
        if (active) {
            deactivateOthers(tenantId, personId);
        }
        apply(person, request);
        person.setActive(active);
        personRepository.saveAndFlush(person);
        auditService.record("ADMIN.GOAML_PERSON_UPDATE", null, null, TenantContext.get(),
                "updated goAML person " + personId + (active ? " (active)" : ""));
        return person;
    }

    @Override
    @Transactional
    public void deleteGoamlPerson(UUID tenantId, UUID personId) {
        TenantGoamlPerson person = personRepository.findById(personId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new AdminExceptions.GoamlPersonNotFoundException(
                        "No goAML person " + personId + " in this tenant"));
        personRepository.delete(person);
        auditService.record("ADMIN.GOAML_PERSON_DELETE", null, null, TenantContext.get(),
                "deleted goAML person " + personId);
    }

    /** Copy the editable fields from the request onto the entity (names are already set on create). */
    private static void apply(TenantGoamlPerson person, GoamlPersonRequest request) {
        person.setFirstName(request.firstName());
        person.setLastName(request.lastName());
        person.setGender(request.gender());
        person.setSsn(request.ssn());
        person.setIdNumber(request.idNumber());
        person.setNationality(request.nationality());
        person.setEmail(request.email());
        person.setOccupation(request.occupation());
    }

    /**
     * Clear the active flag on the tenant's other persons before activating one — at most one active per
     * tenant (the partial unique index). Flushed so the UPDATEs land before the activation INSERT/UPDATE.
     */
    private void deactivateOthers(UUID tenantId, UUID keepId) {
        List<TenantGoamlPerson> changed = personRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(TenantGoamlPerson::isActive)
                .filter(p -> keepId == null || !p.getId().equals(keepId))
                .toList();
        if (!changed.isEmpty()) {
            changed.forEach(p -> p.setActive(false));
            personRepository.saveAll(changed);
            personRepository.flush();
        }
    }

    /**
     * Validate the FIU auth mode against {@link B2bAuthMode} at write time (returning the normalized,
     * upper-cased value) so a typo fails here with a clear 400 — not later as a cryptic {@code valueOf}
     * error at submit.
     */
    private static String validateAuthMode(String authMode) {
        String normalized = authMode == null ? "" : authMode.trim().toUpperCase();
        boolean known = Arrays.stream(B2bAuthMode.values()).anyMatch(m -> m.name().equals(normalized));
        if (!known) {
            String allowed = Arrays.stream(B2bAuthMode.values()).map(Enum::name).collect(Collectors.joining(", "));
            throw new IllegalArgumentException("authMode must be one of [" + allowed + "], was: " + authMode);
        }
        return normalized;
    }

    /** Validate the jurisdiction against the configured registry (UAE today) at write time. */
    private void validateJurisdiction(String jurisdictionCode) {
        String code = jurisdictionCode == null ? "" : jurisdictionCode.trim().toLowerCase();
        if (jurisdictionRegistry.find(code).isEmpty()) {
            throw new IllegalArgumentException("Unknown jurisdictionCode: " + jurisdictionCode
                    + " (supported: " + jurisdictionRegistry.codes() + ")");
        }
    }
}
