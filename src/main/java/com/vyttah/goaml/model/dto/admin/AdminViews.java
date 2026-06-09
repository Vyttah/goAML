package com.vyttah.goaml.model.dto.admin;

import com.vyttah.goaml.model.entity.appuser.AppUser;
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
}
