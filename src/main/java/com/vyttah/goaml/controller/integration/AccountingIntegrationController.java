package com.vyttah.goaml.controller.integration;

import com.vyttah.goaml.model.dto.integration.AccountingTxnPayload;
import com.vyttah.goaml.model.dto.integration.AccountingTxnResponse;
import com.vyttah.goaml.service.integration.AccountingIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Accounting → goAML integration push (Phase 1.5b, Model 2). Server-to-server: authenticated by the
 * accounting service's signed assertion (the {@code X-Service-Assertion} header). The assertion is verified
 * by {@link com.vyttah.goaml.security.IntegrationAuthFilter} <em>before</em> dispatch (C1), so this path is
 * permitted in the user-JWT chain but never reaches the controller unauthenticated — the controller no longer
 * re-verifies (the assertion is single-use; the filter already consumed it).
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
    public ResponseEntity<AccountingTxnResponse> push(@Valid @RequestBody AccountingTxnPayload payload) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestionService.ingest(payload));
    }

    @GetMapping("/transactions/{documentNumber}")
    public AccountingTxnResponse status(
            @RequestParam int companyId,
            @PathVariable String documentNumber) {
        return ingestionService.status(companyId, documentNumber);
    }

    @GetMapping("/transactions")
    public List<AccountingTxnResponse> list(
            @RequestParam int companyId,
            @RequestParam(required = false) String status) {
        return ingestionService.list(companyId, status);
    }
}
