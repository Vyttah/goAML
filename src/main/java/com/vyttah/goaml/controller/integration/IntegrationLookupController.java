package com.vyttah.goaml.controller.integration;

import com.vyttah.goaml.controller.lookup.LookupExceptions.LookupNotFoundException;
import com.vyttah.goaml.engine.jurisdiction.JurisdictionRegistry;
import com.vyttah.goaml.engine.lookup.LookupService;
import com.vyttah.goaml.model.dto.lookup.LookupViews.CodeLabel;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.security.ServiceCredentialValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-authed lookup passthrough (Phase C.3) — lets a sibling app (the AML cockpit) read goAML's
 * authoritative {@code code+label} lookup sets server-to-server, so its own dropdowns (item type / status /
 * report indicators) match goAML's codes exactly with no drift. Reference data, not tenant data: authenticated
 * by the signed service assertion (verified here; the path is permitted in {@code SecurityConfig}), not a user
 * JWT. The user-facing equivalent for goAML's own SPA is {@code /api/v1/lookups/**}.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/integration/lookups")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class IntegrationLookupController {

    private final ServiceCredentialValidator credentialValidator;
    private final JurisdictionRegistry jurisdictionRegistry;
    private final LookupService lookupService;

    @GetMapping("/{jurisdiction}/{set}")
    public List<CodeLabel> lookup(
            @RequestHeader(value = "X-Service-Assertion", required = false) String assertion,
            @PathVariable String jurisdiction,
            @PathVariable String set) {
        credentialValidator.verify(SourceSystem.SCREENING, assertion);
        if (jurisdictionRegistry.find(jurisdiction).isEmpty()) {
            throw new LookupNotFoundException("Unknown jurisdiction: " + jurisdiction);
        }
        if (!lookupService.hasSet(jurisdiction, set)) {
            throw new LookupNotFoundException(
                    "No lookup set '" + set + "' for jurisdiction '" + jurisdiction + "'");
        }
        return lookupService.entries(jurisdiction, set).stream()
                .map(e -> new CodeLabel(e.code(), e.label()))
                .toList();
    }
}
