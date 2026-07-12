package com.vyttah.goaml.model.dto.integration;

import java.util.UUID;

/**
 * Result of a goAML federated-user upsert (see {@code IntegrationUserProvisioningService}). Returned to the AML
 * admin service so it can surface goAML's outcome in its own UI alongside the AML result.
 *
 * @param userId  the goAML app_user id (null when no user was created — e.g. no role assigned)
 * @param email   the resolved email
 * @param role    the goAML role now assigned (null when none)
 * @param status  {@code ACTIVE} / {@code DISABLED} / {@code NONE} (no user)
 * @param message human-readable summary for the AML UI
 */
public record IntegrationUserUpsertResponse(
        UUID userId,
        String email,
        String role,
        String status,
        String message) {
}
