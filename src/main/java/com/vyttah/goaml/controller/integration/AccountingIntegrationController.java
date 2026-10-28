package com.vyttah.goaml.controller.integration;

import com.vyttah.goaml.model.dto.integration.AccountingReportRequest;
import com.vyttah.goaml.model.dto.integration.AccountingReportResponse;
import com.vyttah.goaml.model.dto.integration.AccountingTxnPayload;
import com.vyttah.goaml.model.dto.integration.AccountingTxnResponse;
import com.vyttah.goaml.security.IntegrationAuthFilter;
import com.vyttah.goaml.security.ServiceCredentialException;
import com.vyttah.goaml.security.VerifiedServiceAssertion;
import com.vyttah.goaml.service.integration.AccountingIngestionService;
import com.vyttah.goaml.service.report.pdf.ReportPdfService;
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
 *   <li>{@code POST /reports} — Phase 2 (F0): file a report the ERP's filing wizard already assembled</li>
 *   <li>{@code GET  /reports/{entityReference}?companyId=} — status + validation messages of a filed report</li>
 *   <li>{@code GET  /reports/{entityReference}/xml?companyId=} — X1: the persisted goAML XML, as a download</li>
 *   <li>{@code GET  /reports/{entityReference}/pdf?companyId=} — X1: the report PDF, rendered per request</li>
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
     * Phase 2 (F0) — file a full-fidelity DPMSR report the ERP's goAML filing wizard assembled. 201 on a
     * created report, 200 when the call matched an existing one (idempotent on the payload's
     * {@code entityReference}). Status {@code INVALID} is a successful filing with findings, not an HTTP
     * error — the ERP stores it and shows the messages.
     */
    @PostMapping("/reports")
    public ResponseEntity<AccountingReportResponse> fileReport(
            @RequestAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR) VerifiedServiceAssertion verified,
            @Valid @RequestBody AccountingReportRequest request) {
        requireOrgMatches(verified, request.companyId());
        AccountingReportResponse response = ingestionService.fileReport(
                request.companyId(), request.payload(), verified.externalUserId());
        return ResponseEntity.status(response.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    /** Phase 2 (F0) — status + persisted validation messages of a filed report (used by the refresh job). */
    @GetMapping("/reports/{entityReference}")
    public AccountingReportResponse reportStatus(
            @RequestAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR) VerifiedServiceAssertion verified,
            @RequestParam int companyId,
            @PathVariable String entityReference) {
        requireOrgMatches(verified, companyId);
        return ingestionService.reportStatus(companyId, entityReference);
    }

    /**
     * X1 (D34/D35) — the persisted goAML XML of a filed report for the ERP's "Download XML" row action: raw
     * bytes as {@code application/xml} with the same {@code Content-Disposition} the goAML SPA's
     * {@code GET /api/v1/reports/{id}/xml} sends ({@code <entityReference>.xml}; the ERP rewrites the filename
     * to its document number before the browser sees it). 404 for an unknown reference in the mapped tenant.
     */
    @GetMapping(value = "/reports/{entityReference}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> reportXml(
            @RequestAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR) VerifiedServiceAssertion verified,
            @RequestParam int companyId,
            @PathVariable String entityReference) {
        requireOrgMatches(verified, companyId);
        AccountingIngestionService.ReportArtefact artefact = ingestionService.reportXml(companyId, entityReference);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artefact.filename() + "\"")
                .body(artefact.xml());
    }

    /**
     * X1 (D34–D36) — the print-ready PDF of a filed report, rendered per request from its persisted XML
     * (same renderer as the goAML SPA's {@code GET /api/v1/reports/{id}/report.pdf}). The PDF's
     * "Generated by" line is the assertion's user: its email when the ERP sent one, else its user id.
     */
    @GetMapping(value = "/reports/{entityReference}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> reportPdf(
            @RequestAttribute(IntegrationAuthFilter.VERIFIED_ASSERTION_ATTR) VerifiedServiceAssertion verified,
            @RequestParam int companyId,
            @PathVariable String entityReference) {
        requireOrgMatches(verified, companyId);
        ReportPdfService.PdfDocument doc = ingestionService.reportPdf(companyId, entityReference, userLabel(verified));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.filename() + "\"")
                .body(doc.bytes());
    }

    /** The label the PDF header names as the generator (D36): the assertion's email when present, else its {@code sub}. */
    private static String userLabel(VerifiedServiceAssertion verified) {
        String email = verified.externalEmail();
        return email == null || email.isBlank() ? verified.externalUserId() : email;
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
