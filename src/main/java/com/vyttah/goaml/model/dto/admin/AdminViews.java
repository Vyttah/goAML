package com.vyttah.goaml.model.dto.admin;

import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlConfig;
import com.vyttah.goaml.model.entity.goamlconfig.TenantGoamlPerson;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request/response DTOs for the admin API (Phase 13.2). Tenant management is SUPER_ADMIN; user management +
 * goAML-config are TENANT_ADMIN, scoped to the caller's own tenant. {@code secretsPath}/{@code baseUrl} are
 * references (not credentials — those stay in Secrets Manager), so they're safe to echo.
 */
public final class AdminViews {

    private AdminViews() {}

    public record TenantView(UUID id, String slug, String name, String jurisdictionCode,
                             String schemaName, String status, OffsetDateTime createdAt) {
        public static TenantView from(Tenant t) {
            return new TenantView(t.getId(), t.getSlug(), t.getName(), t.getJurisdictionCode(),
                    t.getSchemaName(), t.getStatus(), t.getCreatedAt());
        }
    }

    /** Create a user in the caller's tenant. {@code role} ∈ ANALYST | MLRO | TENANT_ADMIN. */
    public record CreateUserRequest(@Email @NotBlank String email, @NotBlank String password,
                                    @NotBlank String firstName, @NotBlank String lastName,
                                    @NotBlank String role) {}

    /** Update a user in the caller's tenant: profile, single role, and status (ACTIVE | DISABLED). Email is
     *  immutable (the login identity). A DISABLED user cannot log in but is retained for audit. */
    public record UpdateUserRequest(@NotBlank String firstName, @NotBlank String lastName,
                                    @NotBlank String role, @NotBlank String status) {}

    /** Reset a user's password (SUPER_ADMIN cross-tenant). */
    public record ResetPasswordRequest(@NotBlank String password) {}

    public record UserView(UUID id, String email, String firstName, String lastName, String status,
                           List<String> roles, OffsetDateTime createdAt) {
        public static UserView from(AppUser u) {
            return new UserView(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getStatus(),
                    u.getRoles().stream().map(Role::getName).sorted().toList(), u.getCreatedAt());
        }
    }

    /** Upsert the caller tenant's goAML B2B config (the single per-tenant row). */
    public record GoamlConfigRequest(@NotBlank String jurisdictionCode, @NotNull Integer rentityId,
                                     @NotBlank String baseUrl, @NotBlank String secretsPath,
                                     @NotBlank String authMode) {}

    public record GoamlConfigView(UUID tenantId, String jurisdictionCode, Integer rentityId,
                                  String baseUrl, String secretsPath, String authMode,
                                  OffsetDateTime updatedAt) {
        public static GoamlConfigView from(TenantGoamlConfig c) {
            return new GoamlConfigView(c.getTenantId(), c.getJurisdictionCode(), c.getRentityId(),
                    c.getBaseUrl(), c.getSecretsPath(), c.getAuthMode(), c.getUpdatedAt());
        }
    }

    /**
     * Create/update a tenant goAML reporting person (the filing MLRO; mirrors LexAML's "GoAML Person"). Only
     * {@code firstName}/{@code lastName} are required (the goAML reporting_person mandates only those); the
     * rest are optional. {@code active} (default true on create) makes this the tenant's default — at most one
     * is active, so setting it active deactivates the others.
     */
    public record GoamlPersonRequest(@NotBlank String firstName, @NotBlank String lastName,
                                     String gender, String ssn, String idNumber, String nationality,
                                     @Email String email, String occupation, Boolean active) {}

    public record GoamlPersonView(UUID id, String firstName, String lastName, String gender, String ssn,
                                  String idNumber, String nationality, String email, String occupation,
                                  boolean active, OffsetDateTime updatedAt) {
        public static GoamlPersonView from(TenantGoamlPerson p) {
            return new GoamlPersonView(p.getId(), p.getFirstName(), p.getLastName(), p.getGender(), p.getSsn(),
                    p.getIdNumber(), p.getNationality(), p.getEmail(), p.getOccupation(), p.isActive(),
                    p.getUpdatedAt());
        }
    }

    // ----- Suite Connections (SUPER_ADMIN): trusted services + company→tenant links -----

    /**
     * Register a sibling Vyttah service (ACCOUNTING | SCREENING) allowed to drive the federated token-exchange
     * and integration push. {@code publicKeyPem} is the RS256 public key goAML verifies assertions against
     * (the private key never leaves the sibling). {@code jitProvisioning} auto-creates unknown users on first
     * exchange with {@code defaultRole} (blank → least-privilege ANALYST).
     */
    public record CreateTrustedServiceRequest(@NotBlank String sourceSystem, String description,
                                              @NotBlank String publicKeyPem, Boolean jitProvisioning,
                                              String defaultRole) {}

    /** Update a trusted service's key/policy/status. {@code status} ∈ ACTIVE | DISABLED. */
    public record UpdateTrustedServiceRequest(String description, @NotBlank String publicKeyPem,
                                              Boolean jitProvisioning, String defaultRole,
                                              @NotBlank String status) {}

    public record TrustedServiceView(UUID id, String sourceSystem, String description, String publicKeyPem,
                                     boolean jitProvisioning, String defaultRole, String status,
                                     OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        public static TrustedServiceView from(TrustedService t) {
            return new TrustedServiceView(t.getId(), t.getSourceSystem().name(), t.getDescription(),
                    t.getPublicKeyPem(), t.isJitProvisioning(), t.getDefaultRole(), t.getStatus(),
                    t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    /** Map a sibling system's org id ({@code externalOrgRef}) to a goAML {@code tenantId}. */
    public record CreateTenantExternalRefRequest(@NotNull UUID tenantId, @NotBlank String sourceSystem,
                                                 @NotBlank String externalOrgRef) {}

    public record TenantExternalRefView(UUID id, UUID tenantId, String sourceSystem, String externalOrgRef,
                                        OffsetDateTime createdAt) {
        public static TenantExternalRefView from(TenantExternalRef t) {
            return new TenantExternalRefView(t.getId(), t.getTenantId(), t.getSourceSystem().name(),
                    t.getExternalOrgRef(), t.getCreatedAt());
        }
    }
}
