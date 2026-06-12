package com.vyttah.goaml.controller.integration;

import com.vyttah.goaml.model.dto.integration.AccountingTxnPayload;
import com.vyttah.goaml.model.dto.integration.AccountingTxnResponse;
import com.vyttah.goaml.security.IntegrationAuthFilter;
import com.vyttah.goaml.security.ServiceCredentialException;
import com.vyttah.goaml.security.VerifiedServiceAssertion;
import com.vyttah.goaml.service.integration.AccountingIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Accounting → goAML integration push (Phase 1.5b, Model 2). Server-to-server: authenticated by the
 * accounting service's signed assertion (the {@code X-Service-Assertion} header). The assertion is verified
 * by {@link IntegrationAuthFilter} <em>before</em> dispatch (C1) and stashed as a request attribute; the
 * controller reads it (it does not re-verify — the assertion is single-use). On top of authentication, every
 * handler enforces the org-claim cross-check (B11, mirroring {@link ScreeningIntegrationController}): the
 * tenant a caller may touch is whatever its signed {@code org} claim says, not the raw {@code companyId}
 * request value.
 *
 * <ul>
 *   <li>{@code POST /transactions} — push an invoice → 202 with the reportability verdict (+ draft if reportable)</li>
 *   <li>{@code GET  /transactions/{documentNumber}?companyId=} — status of one document</li>
 *   <li>{@code GET  /transactions?companyId=&status=} — all goAML reports from this company</li>
 * </ul>
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/integration/accounting")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AccountingIntegrationController {

    private final AccountingIngestionService ingestionService;

    @PostMapping("/transactions")
    public ResponseEntity<AccountingTxnResponse> push(
            @RequestAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR) VerifiedServiceAssertion verified,
            @Valid @RequestBody AccountingTxnPayload payload) {
        requireOrgMatches(verified, payload.companyId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestionService.ingest(payload));
    }

    @GetMapping("/transactions/{documentNumber}")
    public AccountingTxnResponse status(
            @RequestAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR) VerifiedServiceAssertion verified,
            @RequestParam int companyId,
            @PathVariable String documentNumber) {
        requireOrgMatches(verified, companyId);
        return ingestionService.status(companyId, documentNumber);
    }

    @GetMapping("/transactions")
    public List<AccountingTxnResponse> list(
            @RequestAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR) VerifiedServiceAssertion verified,
            @RequestParam int companyId,
            @RequestParam(required = false) String status) {
        requireOrgMatches(verified, companyId);
        return ingestionService.list(companyId, status);
    }

    /**
     * B11 — the verified assertion's signed {@code org} claim must be present and equal the requested
     * {@code companyId}. The {@code org} claim is mandatory: an assertion without it is rejected so a legacy
     * signer cannot bypass the tenant guard by relying on the payload alone. {@link ServiceCredentialException}
     * maps to {@code 401} — same semantics as the screening controller.
     */
    private static void requireOrgMatches(VerifiedServiceAssertion verified, int requestedCompanyId) {
        String org = verified == null ? null : verified.externalOrgRef();
        if (org == null || org.isBlank()) {
            throw new ServiceCredentialException(
                    "Service assertion must carry an org claim to access accounting data");
        }
        if (!org.equals(String.valueOf(requestedCompanyId))) {
            throw new ServiceCredentialException(
                    "Service assertion org does not match the requested company");
        }
    }
}
