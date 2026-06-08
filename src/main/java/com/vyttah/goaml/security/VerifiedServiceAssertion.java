package com.vyttah.goaml.security;

import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TrustedService;

import java.util.List;

/**
 * The validated content of a sibling service's signed assertion (Phase 1.5), produced by
 * {@link ServiceCredentialValidator}. The caller (federated exchange / integration push) trusts these
 * fields because the signature verified against the registered {@link #service} public key.
 *
 * @param service        the registered {@link TrustedService} whose key verified the assertion
 * @param sourceSystem   the asserting source system
 * @param externalUserId the user's id in the source system (assertion {@code sub})
 * @param externalEmail  the user's email in the source system (may be {@code null})
 * @param externalOrgRef the source's org/company reference for tenant resolution (may be {@code null})
 * @param roleHints      advisory role names from the source (goAML remains authoritative for actual roles)
 */
public record VerifiedServiceAssertion(
        TrustedService service,
        SourceSystem sourceSystem,
        String externalUserId,
        String externalEmail,
        String externalOrgRef,
        List<String> roleHints) {
}
