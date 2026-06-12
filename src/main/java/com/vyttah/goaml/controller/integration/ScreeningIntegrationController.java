package com.vyttah.goaml.controller.integration;

import com.vyttah.goaml.model.dto.integration.ScreeningFilingPayload;
import com.vyttah.goaml.model.dto.integration.ScreeningFilingResponse;
import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload;
import com.vyttah.goaml.model.dto.integration.ScreeningSubjectResponse;
import com.vyttah.goaml.security.IntegrationAuthFilter;
import com.vyttah.goaml.security.ServiceCredentialException;
import com.vyttah.goaml.security.VerifiedServiceAssertion;
import com.vyttah.goaml.service.integration.ScreeningIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 * AML screening → goAML integration push (Phase 1.5c). Server-to-server: authenticated by the screening
 * service's signed assertion (the {@code X-Service-Assertion} header). The assertion is verified by
 * {@link IntegrationAuthFilter} <em>before</em> dispatch (C1) and stashed as a request attribute; the
 * controller reads it (it does not re-verify — the assertion is single-use). On top of authentication, every
 * handler enforces the org-claim cross-check (B11): the tenant a caller may touch is whatever its signed
 * {@code org} claim says, not the raw {@code companyId} request value.
 *
 * <ul>
 *   <li>{@code POST /subjects} — push a screened customer → 202 with the mapped goAML party set</li>
 *   <li>{@code GET  /subjects/{customerUid}?companyId=} — fetch one stored subject</li>
 *   <li>{@code GET  /subjects?companyId=} — all screened subjects from this company</li>
 *   <li>{@code POST /filings} — one-shot file a complete DPMSR (parties + goods + header) → 202 with
 *       the created report id + status (Phase C.4a, the AML "File to goAML")</li>
 *   <li>{@code GET  /filings/{filingRef}?companyId=} — status of a previously-filed report</li>
 *   <li>{@code GET  /filings/{filingRef}/report.xml?companyId=} — the marshalled goAML XML (Phase C.4b,
 *       backs the AML cockpit's "Download report" action)</li>
 * </ul>
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/integration/screening")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ScreeningIntegrationController {

    private final ScreeningIngestionService ingestionService;

    @PostMapping("/subjects")
    public ResponseEntity<ScreeningSubjectResponse> push(
            @RequestAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR) VerifiedServiceAssertion verified,
            @Valid @RequestBody ScreeningPartyPayload payload) {
        requireOrgMatches(verified, payload.companyId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestionService.ingest(payload));
    }

    @GetMapping("/subjects/{customerUid}")
    public ScreeningSubjectResponse get(
            @RequestAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR) VerifiedServiceAssertion verified,
            @RequestParam String companyId,
            @PathVariable String customerUid) {
        requireOrgMatches(verified, companyId);
        return ingestionService.get(companyId, customerUid);
    }

    @GetMapping("/subjects")
    public List<ScreeningSubjectResponse> list(
            @RequestAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR) VerifiedServiceAssertion verified,
            @RequestParam String companyId) {
        requireOrgMatches(verified, companyId);
        return ingestionService.list(companyId);
    }

    @PostMapping("/filings")
    public ResponseEntity<ScreeningFilingResponse> file(
            @RequestAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR) VerifiedServiceAssertion verified,
            @Valid @RequestBody ScreeningFilingPayload payload) {
        requireOrgMatches(verified, payload.companyId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestionService.file(payload));
    }

    @GetMapping("/filings/{filingRef}")
    public ScreeningFilingResponse filingStatus(
            @RequestAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR) VerifiedServiceAssertion verified,
            @RequestParam String companyId,
            @PathVariable String filingRef) {
        requireOrgMatches(verified, companyId);
        return ingestionService.filingStatus(companyId, filingRef);
    }

    @GetMapping(value = "/filings/{filingRef}/report.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> filingXml(
            @RequestAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR) VerifiedServiceAssertion verified,
            @RequestParam String companyId,
            @PathVariable String filingRef) {
        requireOrgMatches(verified, companyId);
        String xml = ingestionService.filingXml(companyId, filingRef);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filingReference(companyId, filingRef) + ".xml\"")
                .body(xml);
    }

    /**
     * B11 — the verified assertion's signed {@code org} claim must be present and equal the requested
     * {@code companyId}. The {@code org} claim is mandatory: an assertion without it is rejected so a legacy
     * signer cannot bypass the tenant guard by relying on the param alone. {@link ServiceCredentialException}
     * maps to {@code 401}.
     */
    private static void requireOrgMatches(VerifiedServiceAssertion verified, String requestedCompanyId) {
        String org = verified == null ? null : verified.externalOrgRef();
        if (org == null || org.isBlank()) {
            throw new ServiceCredentialException(
                    "Service assertion must carry an org claim to access screening data");
        }
        if (!org.equals(requestedCompanyId)) {
            throw new ServiceCredentialException(
                    "Service assertion org does not match the requested company");
        }
    }

    private static String filingReference(String companyId, String filingRef) {
        return "FIL-" + companyId + "-" + filingRef;
    }
}
