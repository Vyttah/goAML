package com.vyttah.goaml.model.dto.tenant;

/**
 * Input for {@link TenantProvisioningService#provision}.
 *
 * @param slug              URL-safe tenant identifier; unique platform-wide
 * @param name              human-readable tenant name (the client RE's display name)
 * @param jurisdictionCode  ISO-style code that must match a row in {@code public.jurisdiction}
 *                          (only {@code AE} in v1)
 * @param adminEmail        email for the initial TENANT_ADMIN user (globally unique)
 * @param adminPassword     plaintext password (hashed with BCrypt before storage)
 * @param adminFirstName    given name for the initial admin
 * @param adminLastName     family name for the initial admin
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
