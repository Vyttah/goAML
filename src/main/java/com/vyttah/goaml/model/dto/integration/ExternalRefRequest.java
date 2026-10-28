package com.vyttah.goaml.model.dto.integration;

import jakarta.validation.constraints.NotBlank;

/**
 * Add a sibling system's org reference to the <em>caller's</em> tenant (goAML ↔ ERP wiring, Phase 1 / W1).
 * The tenant is never named in the body: it is the one the assertion's {@code org} claim is mapped to under
 * the caller's source system, so a caller can only extend its own tenant.
 *
 * @param sourceSystem   the system the reference belongs to ({@code ACCOUNTING} for the ERP company id)
 * @param externalOrgRef that system's org id (e.g. the ERP {@code company_id} as a string)
 */
public record ExternalRefRequest(@NotBlank String sourceSystem, @NotBlank String externalOrgRef) {
}
