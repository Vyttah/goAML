package com.vyttah.goaml.model.dto.integration;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload the AML admin service POSTs to goAML when a new client onboards (Phase 1.5 integration).
 * The {@code slug} and {@code externalOrgRef} are derived from the signed assertion's {@code org} claim
 * (the AML companyId) — not the body — following the same B11 pattern as accounting/screening pushes.
 *
 * <p>As of the decoupled onboarding flow, the tenant is provisioned with <strong>no users</strong>: the AML
 * admin service creates goAML users explicitly (with an {@code external_identity} mapping) only when a goAML role
 * is assigned to an AML user. The {@code admin*} fields are therefore optional and normally omitted; if all four
 * are supplied an initial TENANT_ADMIN is still created (back-compat).
 *
 * @param name            company display name (becomes the goAML tenant name)
 * @param email           optional initial-admin email; when blank the tenant is created with no users
 * @param adminPassword   optional plaintext initial password (BCrypt-hashed before storage)
 * @param adminFirstName  optional given name of the initial admin contact
 * @param adminLastName   optional family name of the initial admin contact
 * @param jurisdictionCode ISO-style jurisdiction (defaults to {@code AE} when blank)
 */
public record IntegrationTenantProvisionRequest(
        @NotBlank String name,
        String email,
        String adminPassword,
        String adminFirstName,
        String adminLastName,
        String jurisdictionCode) {
}
