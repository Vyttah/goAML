package com.vyttah.goaml.model.dto.auth;

import com.vyttah.goaml.model.entity.federated.SourceSystem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Federated token-exchange request (Phase 1.5). A sibling service calls this server-to-server after
 * authenticating its own user.
 *
 * @param sourceSystem the calling source ({@code ACCOUNTING} | {@code SCREENING}) — selects the verification key
 * @param assertion    the signed service assertion (short-lived RS256 JWT) asserting the end-user identity
 */
public record FederatedTokenRequest(
        @NotNull SourceSystem sourceSystem,
        @NotBlank String assertion) {
}
