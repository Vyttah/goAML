package com.vyttah.goaml.model.dto.integration;

import jakarta.validation.constraints.Email;

/**
 * Payload the AML admin service PUTs to goAML to create/update the goAML user backing an AML user, when that
 * AML user is granted (or has changed) a goAML role. The goAML user is resolved/keyed by the assertion's
 * {@code org} claim (companyId → tenant) + the path {@code externalUserId} (the AML user id) via
 * {@code external_identity} — never by email, which is what avoids the old JIT email-collision.
 *
 * @param email      the user's email (goAML login identity, unique per tenant); required when creating
 * @param firstName  given name
 * @param lastName   family name
 * @param password   plaintext password to propagate (BCrypt-hashed before storage); when null the existing
 *                   password is left untouched (on create, a random unusable one is set)
 * @param role       goAML role — one of {@code ANALYST|MLRO|TENANT_ADMIN}; null/blank ⇒ no role
 * @param active     {@code true} ⇒ the goAML user is ACTIVE; {@code false} ⇒ DISABLED (role cleared / user
 *                   disabled on the AML side). Defaults to {@code true}.
 */
public record IntegrationUserUpsertRequest(
        @Email String email,
        String firstName,
        String lastName,
        String password,
        String role,
        Boolean active) {
}
