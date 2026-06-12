package com.vyttah.goaml.controller.report;

import com.vyttah.goaml.ingestion.reportability.ReportabilityDetector;
import com.vyttah.goaml.ingestion.reportability.ReportabilityFacts;
import com.vyttah.goaml.ingestion.reportability.ReportabilityVerdict;
import com.vyttah.goaml.model.dto.reportability.ReportabilityCheckRequest;
import com.vyttah.goaml.model.dto.reportability.ReportabilityCheckResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Exposes goAML's authoritative DPMS reportability verdict (Phase 1.5b) so embedded clients (accounting /
 * AML software) can check before building or submitting a report — keeping the rule owned by goAML, not
 * re-implemented per app. The same {@link ReportabilityDetector} backs the accounting raw-invoice push.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/reportability")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ReportabilityController {

    private final ReportabilityDetector detector;

    @PostMapping("/check")
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ReportabilityCheckResponse check(@Valid @RequestBody ReportabilityCheckRequest request) {
        String currency = request.currencyCode() == null || request.currencyCode().isBlank()
                ? "AED" : request.currencyCode().trim().toUpperCase();
        if (!"AED".equals(currency)) {
            // The DPMS threshold is defined in AED; v1 does not convert — the caller supplies AED.
            return new ReportabilityCheckResponse(false,
                    List.of("Supply the amount in AED — the check does not convert from " + currency + " in v1"),
                    ReportabilityDetector.DPMS_CASH_THRESHOLD_AED);
        }
        boolean precious = request.involvesPreciousMetalsOrStones() == null
                || request.involvesPreciousMetalsOrStones();
        ReportabilityVerdict verdict = detector.assess(new ReportabilityFacts(
                request.amount(), precious, request.fundsType(), request.counterpartyType(),
                request.wireScope(), request.viaExchangeHouse()));
        return new ReportabilityCheckResponse(verdict.reportable(), verdict.reasons(),
                ReportabilityDetector.DPMS_CASH_THRESHOLD_AED);
    }
}
