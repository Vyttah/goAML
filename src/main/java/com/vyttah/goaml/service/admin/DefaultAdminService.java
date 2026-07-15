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
import com.vyttah.goaml.model.dto.admin.AdminViews.CreateTenantExternalRefRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.CreateTrustedServiceRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.UpdateTrustedServiceRequest;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.repository.attachment.AttachmentRepository;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.federated.TrustedServiceRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

    /** Statuses a trusted service may hold (ACTIVE accepts assertions; DISABLED rejects them). */
    private static final Set<String> TRUSTED_SERVICE_STATUSES = Set.of("ACTIVE", "DISABLED");

    private final TenantProvisioningService provisioningService;
    private final TenantRepository tenantRepository;
    private final TrustedServiceRepository trustedServiceRepository;
    private final TenantExternalRefRepository tenantExternalRefRepository;
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
    private final PlatformTransactionManager transactionManager;

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
        // SUPER_ADMIN can target any tenant by id, so the tenant may not exist; a TENANT_ADMIN's own always does.
        if (tenantRepository.findById(tenantId).isEmpty()) {
            throw new AdminExceptions.TenantNotFoundException("No tenant " + tenantId);
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleName));
        // Email is unique per tenant. Check explicitly against the TARGET tenant — a SUPER_ADMIN may be
        // creating into a tenant other than the one bound to their request (so the row filter would not apply).
        if (appUserRepository.existsByTenantIdAndEmail(tenantId, request.email())) {
            throw new AdminExceptions.UserEmailExistsException(
                    "A user already exists with email " + request.email() + " in this tenant");
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
    public void deleteUser(UUID tenantId, UUID userId, UUID actingUserId) {
        // The reference-check below queries tenant-scoped tables (report/attachment/import_job/notification
        // live in tenant_<id>, resolved via the search_path the SchemaMultiTenantConnectionProvider issues when
        // the transaction opens its connection). A TENANT_ADMIN already has their own (matching) schema bound,
        // but a SUPER_ADMIN deleting cross-tenant has no tenant context — so resolve the TARGET tenant's schema
        // and push it onto the thread BEFORE opening the transaction (mirrors DefaultAuditService; a method-level
        // @Transactional would have opened the connection with the wrong schema). An unknown tenant resolves to
        // null and is left to surface as UserNotFound via the tenant-match filter below.
        String targetSchema = tenantRepository.findById(tenantId).map(Tenant::getSchemaName).orElse(null);
        String previous = TenantContext.get();
        if (targetSchema != null) {
            TenantContext.set(targetSchema);
        }
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                // B6: lock the user row FOR UPDATE up front so the reference-checks → delete sequence is
                // serialized against a concurrent op on the same user (closes the check-then-delete TOCTOU
                // race). No cross-schema FK can backstop this (the tenant tables that reference a user live in
                // tenant_<id>, not public).
                AppUser user = appUserRepository.findByIdForUpdate(userId)
                        .filter(u -> tenantId.equals(u.getTenantId()))
                        .orElseThrow(() -> new AdminExceptions.UserNotFoundException(
                                "No user " + userId + " in this tenant"));
                if (user.getId().equals(actingUserId)) {
                    throw new IllegalArgumentException("You cannot delete your own account");
                }
                // Block a destructive delete that would orphan a reference anywhere in the tenant. We check
                // every table that soft-references a user id (no FK, because those tables are tenant-schema and
                // the user is in public). Anything referenced → block with 409 and steer the admin to disable.
                if (isReferencedAnywhere(userId)) {
                    throw new AdminExceptions.UserReferencedException(
                            "This user is referenced by reports, attachments, imports, or notifications and "
                                    + "cannot be deleted — disable the user instead");
                }
                appUserRepository.delete(user); // shared FKs (user_role, refresh_token, external_identity) cascade
                // Drop the cached ACTIVE verdict so the deleted user's live tokens are rejected immediately.
                userStatusCache.evict(userId);
                auditService.record("ADMIN.USER_DELETE", null, null, TenantContext.get(),
                        "deleted user " + user.getEmail());
                return null;
            });
        } finally {
            if (previous != null) {
                TenantContext.set(previous);
            } else {
                TenantContext.clear();
            }
        }
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
        // Normalise to the canonical `jurisdiction` table casing (uppercase, e.g. AE) — the registry validates
        // case-insensitively but the jurisdiction_code FK is case-sensitive, so persist the DB's exact code.
        String jurisdictionCode = validateJurisdiction(request.jurisdictionCode());

        TenantGoamlConfig config = configRepository.findByTenantId(tenantId)
                .orElseGet(() -> new TenantGoamlConfig(UUID.randomUUID(), tenantId));
        config.setJurisdictionCode(jurisdictionCode);
        config.setRentityId(request.rentityId());
        config.setBaseUrl(request.baseUrl());
        config.setSecretsPath(request.secretsPath());
        config.setAuthMode(authMode);
        configRepository.save(config);
        auditService.record("ADMIN.GOAML_CONFIG_SET", null, null, TenantContext.get(),
                "set goAML config (rentity " + request.rentityId() + ", " + jurisdictionCode + ")");
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
    /**
     * Validates the jurisdiction against the registry (case-insensitive) and returns the canonical code in the
     * `jurisdiction` table's casing (uppercase) so the case-sensitive {@code jurisdiction_code} FK is satisfied.
     */
    private String validateJurisdiction(String jurisdictionCode) {
        String code = jurisdictionCode == null ? "" : jurisdictionCode.trim();
        if (jurisdictionRegistry.find(code).isEmpty()) {
            throw new IllegalArgumentException("Unknown jurisdictionCode: " + jurisdictionCode
                    + " (supported: " + jurisdictionRegistry.codes() + ")");
        }
        return code.toUpperCase(Locale.ROOT);
    }

    // ----- Suite Connections (SUPER_ADMIN) -----

    @Override
    public List<TrustedService> listTrustedServices() {
        return trustedServiceRepository.findAll();
    }

    @Override
    @Transactional
    public TrustedService createTrustedService(CreateTrustedServiceRequest request) {
        SourceSystem source = parseSourceSystem(request.sourceSystem());
        if (trustedServiceRepository.findBySourceSystem(source).isPresent()) {
            throw new AdminExceptions.TrustedServiceExistsException(
                    "A trusted service is already registered for " + source);
        }
        boolean jit = request.jitProvisioning() != null && request.jitProvisioning();
        TrustedService ts = new TrustedService(UUID.randomUUID(), source, request.description(),
                request.publicKeyPem(), jit, "ACTIVE", blankToNull(request.defaultRole()));
        trustedServiceRepository.save(ts);
        auditService.record("ADMIN.TRUSTED_SERVICE_CREATE", null, null, TenantContext.get(),
                "registered trusted service " + source);
        return ts;
    }

    @Override
    @Transactional
    public TrustedService updateTrustedService(UUID id, UpdateTrustedServiceRequest request) {
        TrustedService ts = trustedServiceRepository.findById(id)
                .orElseThrow(() -> new AdminExceptions.TrustedServiceNotFoundException(
                        "No trusted service " + id));
        String status = request.status() == null ? "" : request.status().trim().toUpperCase();
        if (!TRUSTED_SERVICE_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "status must be one of " + TRUSTED_SERVICE_STATUSES + ", was: " + request.status());
        }
        ts.setDescription(request.description() == null ? "" : request.description());
        ts.setPublicKeyPem(request.publicKeyPem());
        ts.setJitProvisioning(request.jitProvisioning() != null && request.jitProvisioning());
        ts.setDefaultRole(blankToNull(request.defaultRole()));
        ts.setStatus(status);
        trustedServiceRepository.save(ts);
        auditService.record("ADMIN.TRUSTED_SERVICE_UPDATE", null, null, TenantContext.get(),
                "updated trusted service " + ts.getSourceSystem());
        return ts;
    }

    @Override
    @Transactional
    public void deleteTrustedService(UUID id) {
        TrustedService ts = trustedServiceRepository.findById(id)
                .orElseThrow(() -> new AdminExceptions.TrustedServiceNotFoundException(
                        "No trusted service " + id));
        trustedServiceRepository.delete(ts);
        auditService.record("ADMIN.TRUSTED_SERVICE_DELETE", null, null, TenantContext.get(),
                "revoked trusted service " + ts.getSourceSystem());
    }

    @Override
    public List<TenantExternalRef> listTenantExternalRefs() {
        return tenantExternalRefRepository.findAll();
    }

    @Override
    @Transactional
    public TenantExternalRef createTenantExternalRef(CreateTenantExternalRefRequest request) {
        SourceSystem source = parseSourceSystem(request.sourceSystem());
        String orgRef = request.externalOrgRef() == null ? "" : request.externalOrgRef().trim();
        if (orgRef.isBlank()) {
            throw new IllegalArgumentException("externalOrgRef must not be blank");
        }
        if (tenantRepository.findById(request.tenantId()).isEmpty()) {
            throw new AdminExceptions.TenantNotFoundException("No tenant " + request.tenantId());
        }
        if (tenantExternalRefRepository.findBySourceSystemAndExternalOrgRef(source, orgRef).isPresent()) {
            throw new AdminExceptions.TenantExternalRefExistsException(
                    source + " org '" + orgRef + "' is already mapped to a tenant");
        }
        TenantExternalRef ref = new TenantExternalRef(UUID.randomUUID(), request.tenantId(), source, orgRef);
        tenantExternalRefRepository.save(ref);
        auditService.record("ADMIN.TENANT_EXTERNAL_REF_CREATE", null, null, TenantContext.get(),
                "mapped " + source + " org '" + orgRef + "' → tenant " + request.tenantId());
        return ref;
    }

    @Override
    @Transactional
    public void deleteTenantExternalRef(UUID id) {
        TenantExternalRef ref = tenantExternalRefRepository.findById(id)
                .orElseThrow(() -> new AdminExceptions.TenantExternalRefNotFoundException(
                        "No company link " + id));
        tenantExternalRefRepository.delete(ref);
        auditService.record("ADMIN.TENANT_EXTERNAL_REF_DELETE", null, null, TenantContext.get(),
                "removed link " + ref.getSourceSystem() + " org '" + ref.getExternalOrgRef() + "'");
    }

    @Override
    @Transactional
    public AppUser resetUserPassword(UUID tenantId, UUID userId, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        AppUser user = appUserRepository.findById(userId)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new AdminExceptions.UserNotFoundException(
                        "No user " + userId + " in this tenant"));
        user.changePassword(passwordEncoder.encode(newPassword));
        appUserRepository.save(user);
        auditService.record("ADMIN.USER_PASSWORD_RESET", null, null, TenantContext.get(),
                "reset password for user " + user.getEmail());
        return user;
    }

    private static SourceSystem parseSourceSystem(String value) {
        try {
            return SourceSystem.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("sourceSystem must be one of "
                    + Arrays.toString(SourceSystem.values()) + ", was: " + value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
