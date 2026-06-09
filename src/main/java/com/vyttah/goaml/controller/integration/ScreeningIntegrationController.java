package com.vyttah.goaml.controller.integration;

import com.vyttah.goaml.model.dto.integration.ScreeningFilingPayload;
import com.vyttah.goaml.model.dto.integration.ScreeningFilingResponse;
import com.vyttah.goaml.model.dto.integration.ScreeningPartyPayload;
import com.vyttah.goaml.model.dto.integration.ScreeningSubjectResponse;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.security.ServiceCredentialValidator;
import com.vyttah.goaml.service.integration.ScreeningIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AML screening → goAML integration push (Phase 1.5c). Server-to-server: authenticated by the screening
 * service's signed assertion (the {@code X-Service-Assertion} header, verified by
 * {@link ServiceCredentialValidator} for {@link SourceSystem#SCREENING}), not a user JWT — so this path is
 * permitted in {@code SecurityConfig} and gated here instead.
 *
 * <ul>
 *   <li>{@code POST /subjects} — push a screened customer → 202 with the mapped goAML party set</li>
 *   <li>{@code GET  /subjects/{customerUid}?companyId=} — fetch one stored subject</li>
 *   <li>{@code GET  /subjects?companyId=} — all screened subjects from this company</li>
 *   <li>{@code POST /filings} — one-shot file a complete DPMSR (parties + goods + header) → 202 with
 *       the created report id + status (Phase C.4a, the AML "File to goAML")</li>
 *   <li>{@code GET  /filings/{filingRef}?companyId=} — status of a previously-filed report</li>
 * </ul>
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/integration/screening")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ScreeningIntegrationController {

    private final ServiceCredentialValidator credentialValidator;
    private final ScreeningIngestionService ingestionService;

    @PostMapping("/subjects")
    public ResponseEntity<ScreeningSubjectResponse> push(
            @RequestHeader(value = "X-Service-Assertion", required = false) String assertion,
            @Valid @RequestBody ScreeningPartyPayload payload) {
        credentialValidator.verify(SourceSystem.SCREENING, assertion);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestionService.ingest(payload));
    }

    @GetMapping("/subjects/{customerUid}")
    public ScreeningSubjectResponse get(
            @RequestHeader(value = "X-Service-Assertion", required = false) String assertion,
            @RequestParam String companyId,
            @PathVariable String customerUid) {
        credentialValidator.verify(SourceSystem.SCREENING, assertion);
        return ingestionService.get(companyId, customerUid);
    }

    @GetMapping("/subjects")
    public List<ScreeningSubjectResponse> list(
            @RequestHeader(value = "X-Service-Assertion", required = false) String assertion,
            @RequestParam String companyId) {
        credentialValidator.verify(SourceSystem.SCREENING, assertion);
        return ingestionService.list(companyId);
    }

    @PostMapping("/filings")
    public ResponseEntity<ScreeningFilingResponse> file(
            @RequestHeader(value = "X-Service-Assertion", required = false) String assertion,
            @Valid @RequestBody ScreeningFilingPayload payload) {
        credentialValidator.verify(SourceSystem.SCREENING, assertion);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestionService.file(payload));
    }

    @GetMapping("/filings/{filingRef}")
    public ScreeningFilingResponse filingStatus(
            @RequestHeader(value = "X-Service-Assertion", required = false) String assertion,
            @RequestParam String companyId,
            @PathVariable String filingRef) {
        credentialValidator.verify(SourceSystem.SCREENING, assertion);
        return ingestionService.filingStatus(companyId, filingRef);
    }
}
