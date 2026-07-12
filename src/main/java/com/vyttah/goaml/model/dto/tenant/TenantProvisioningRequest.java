package com.vyttah.goaml.model.dto.tenant;

/**
 * Input for {@link TenantProvisioningService#provision}.
 *
 * @param slug              the tenant's <strong>company id</strong> — a URL-safe identifier
 *                          ({@code ^[A-Za-z0-9_-]{3,64}$}), normalized to lower-case and unique
 *                          platform-wide (case-insensitive). Used at login to select the tenant and kept
 *                          equal to the tenant's company id in the AML suite so the two platforms map 1:1.
 *                          Also becomes the schema name ({@code tenant_<slug>}). Immutable after creation;
 *                          the reserved value {@code PLATFORM} is rejected.
 * @param name              human-readable tenant name (the client RE's display name)
 * @param jurisdictionCode  ISO-style code that must match a row in {@code public.jurisdiction}
 *                          (only {@code AE} in v1)
 * @param adminEmail        email for the initial TENANT_ADMIN user. <strong>Optional</strong>: when blank, the
 *                          tenant is provisioned with no users (the AML onboarding flow creates goAML users
 *                          explicitly later, only when a goAML role is assigned). When present, the four
 *                          {@code admin*} fields must all be supplied.
 * @param adminPassword     plaintext password (hashed with BCrypt before storage); required iff adminEmail is set
 * @param adminFirstName    given name for the initial admin; required iff adminEmail is set
 * @param adminLastName     family name for the initial admin; required iff adminEmail is set
 */
public record TenantProvisioningRequest(
        String slug,
        String name,
        String jurisdictionCode,
        String adminEmail,
        String adminPassword,
        String adminFirstName,
        String adminLastName) {
}
