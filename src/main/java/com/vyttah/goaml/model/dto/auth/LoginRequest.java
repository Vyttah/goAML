package com.vyttah.goaml.model.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Native-login credentials.
 *
 * @param companyId the tenant's company id (its {@code slug}); the reserved value {@code PLATFORM}
 *                  logs in a SUPER_ADMIN platform user (no tenant). Email is only unique within a
 *                  tenant, so the company id is required to disambiguate the user.
 * @param email     the user's email
 * @param password  the user's password
 */
public record LoginRequest(
        @NotBlank String companyId,
        @Email @NotBlank String email,
        @NotBlank String password) {
}
