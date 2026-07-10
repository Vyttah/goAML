package com.vyttah.goaml.controller.integration;

import com.vyttah.goaml.model.dto.admin.AdminViews.CreateTenantExternalRefRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.TenantView;
import com.vyttah.goaml.model.dto.integration.IntegrationTenantProvisionRequest;
import com.vyttah.goaml.model.dto.integration.IntegrationTenantProvisionResponse;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.security.IntegrationAuthFilter;
import com.vyttah.goaml.security.VerifiedServiceAssertion;
import com.vyttah.goaml.service.admin.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * AML → goAML platform integration: tenant provisioning (Phase 1.5). Called server-to-server by the AML
 * admin service when a new client onboards. Authenticated by the SCREENING service assertion
 * ({@code X-Service-Assertion}), verified by {@link IntegrationAuthFilter} before dispatch (C1).
 *
 * <p>The assertion's {@code org} claim (AML companyId) is authoritative for both the tenant {@code slug}
 * and the {@code tenant_external_ref} mapping — following the same B11 pattern as accounting/screening pushes.
 * After this call, all subsequent pushes with {@code companyId=<org>} automatically resolve the right tenant.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/integration/admin")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PlatformIntegrationController {

    private final AdminService adminService;

    @PostMapping("/tenants")
    public ResponseEntity<IntegrationTenantProvisionResponse> provision(
            @RequestAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR) VerifiedServiceAssertion verified,
            @Valid @RequestBody IntegrationTenantProvisionRequest request) {

        String companyId = verified.externalOrgRef();
        if (companyId == null || companyId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Service assertion must carry an org claim for tenant provisioning");
        }

        String jurisdiction = (request.jurisdictionCode() == null || request.jurisdictionCode().isBlank())
                ? "AE" : request.jurisdictionCode().trim().toUpperCase();

        // Try provisioning with the provided company email.
        // If that email is already taken (a prior partial onboarding or a shared address),
        // fall back to a deterministic placeholder and surface a warning so the operator
        // can correct it via the admin panel before the client logs in.
        String warning = null;
        String adminEmail = request.email();
        Tenant tenant;

        try {
            tenant = adminService.provisionTenant(buildRequest(
                    companyId, request, adminEmail, jurisdiction));
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Email already in use")) {
                adminEmail = "admin-" + companyId + "@noreply.goaml";
                warning = "The provided email '" + request.email() + "' is already associated with another "
                        + "account. A placeholder admin email '" + adminEmail + "' has been set instead. "
                        + "Update it via the admin panel before the client attempts to log in.";
                tenant = adminService.provisionTenant(buildRequest(
                        companyId, request, adminEmail, jurisdiction));
            } else if (ex.getMessage() != null && ex.getMessage().startsWith("Tenant slug already in use")) {
                // companyId was already onboarded — treat as a conflict, not a server error.
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A tenant with companyId '" + companyId + "' is already provisioned");
            } else {
                throw ex;
            }
        }

        // Wire companyId → tenantId so future accounting/screening pushes resolve this tenant.
        adminService.createTenantExternalRef(new CreateTenantExternalRefRequest(
                tenant.getId(), SourceSystem.SCREENING.name(), companyId));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new IntegrationTenantProvisionResponse(TenantView.from(tenant), warning));
    }

    private static TenantProvisioningRequest buildRequest(String companyId,
                                                          IntegrationTenantProvisionRequest req,
                                                          String adminEmail,
                                                          String jurisdiction) {
        return new TenantProvisioningRequest(
                companyId,
                req.name(),
                jurisdiction,
                adminEmail,
                req.adminPassword(),
                req.adminFirstName(),
                req.adminLastName());
    }
}
