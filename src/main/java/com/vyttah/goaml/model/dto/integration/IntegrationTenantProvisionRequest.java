package com.vyttah.goaml.model.dto.integration;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload the AML admin service POSTs to goAML when a new client onboards (Phase 1.5 integration).
 * The {@code slug} and {@code externalOrgRef} are derived from the signed assertion's {@code org} claim
 * (the AML companyId) — not the body — following the same B11 pattern as accounting/screening pushes.
 *
 * @param name            company display name (becomes the goAML tenant name)
 * @param email           company/admin email — used as the initial TENANT_ADMIN login; if already taken
 *                        a placeholder is substituted and the response carries a warning
 * @param adminPassword   plaintext initial password (BCrypt-hashed before storage)
 * @param adminFirstName  given name of the initial admin contact
 * @param adminLastName   family name of the initial admin contact
 * @param jurisdictionCode ISO-style jurisdiction (defaults to {@code AE} when blank)
 */
public record IntegrationTenantProvisionRequest(
        @NotBlank String name,
        @NotBlank String email,
        @NotBlank String adminPassword,
        @NotBlank String adminFirstName,
        @NotBlank String adminLastName,
        String jurisdictionCode) {
}
