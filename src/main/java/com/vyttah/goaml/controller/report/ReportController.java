package com.vyttah.goaml.controller.report;

import com.vyttah.goaml.model.dto.report.DpmsrReportPayload;
import com.vyttah.goaml.model.dto.report.ReportResponses.CreateReportResponse;
import com.vyttah.goaml.model.dto.report.ReportResponses.ReportDetailView;
import com.vyttah.goaml.model.dto.report.ReportResponses.ReportView;
import com.vyttah.goaml.model.dto.report.ReportResponses.ReviewView;
import com.vyttah.goaml.model.dto.report.ReportResponses.StatusView;
import com.vyttah.goaml.model.dto.report.ReportResponses.SubmissionView;
import com.vyttah.goaml.model.dto.report.ReviewDecisionRequest;
import com.vyttah.goaml.model.entity.report.Report;
import com.vyttah.goaml.security.UserPrincipal;
import com.vyttah.goaml.service.report.ReportExceptions;
import com.vyttah.goaml.service.report.ReportResult;
import com.vyttah.goaml.service.report.ReportReviewService;
import com.vyttah.goaml.service.report.ReportService;
import com.vyttah.goaml.service.submission.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API for the DPMSR report lifecycle. Thin — delegates to {@link ReportService} /
 * {@link SubmissionService}; tenant + actor come from the authenticated {@link UserPrincipal}. RBAC:
 * create/read for ANALYST or MLRO; <strong>submit is MLRO-only</strong>. Phase D.2 adds the opt-in review
 * gate — submit-for-review (ANALYST or MLRO) → approve/reject (MLRO) — which, when enabled, the submit call
 * requires to have reached {@code APPROVED}.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportReviewService reviewService;
    private final SubmissionService submissionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ANALYST','MLRO')")
    public ResponseEntity<CreateReportResponse> create(@Valid @RequestBody DpmsrReportPayload request,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        ReportResult result = reportService.create(request, principal.getTenantId(), principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateReportResponse.from(result));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<List<ReportView>> list() {
        return ResponseEntity.ok(reportService.list().stream().map(ReportView::from).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<ReportView> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ReportView.from(reportService.get(id)));
    }

    /**
     * Phase D.3 — the full "Transaction &amp; Report" read view: summary + the stored filing input + the
     * persisted validation messages + the review trail. Lets a goAML login see the whole filing.
     */
    @GetMapping("/{id}/detail")
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<ReportDetailView> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ReportDetailView.from(reportService.detail(id)));
    }

    /**
     * The marshalled goAML XML for a report (built + persisted at create, for VALID and INVALID alike).
     * Served as {@code application/xml} with a download filename so the SPA can both preview and download
     * the exact payload that would be submitted to the FIU.
     */
    @GetMapping(value = "/{id}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<String> xml(@PathVariable UUID id) {
        Report report = reportService.get(id);
        String xml = report.getReportXml();
        if (xml == null || xml.isBlank()) {
            throw new ReportExceptions.ReportNotFoundException("Report has no generated XML: " + id);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + report.getEntityReference() + ".xml\"")
                .body(xml);
    }

    /**
     * Phase D.2 review gate (opt-in per tenant). {@code submit-for-review} moves a VALID report into the
     * queue; {@code approve}/{@code reject} are MLRO-only and decide it. When review is enabled the
     * {@code /submit} call below requires {@code APPROVED}.
     */
    @PostMapping("/{id}/submit-for-review")
    @PreAuthorize("hasAnyRole('ANALYST','MLRO')")
    public ResponseEntity<ReviewView> submitForReview(@PathVariable UUID id,
                                                      @RequestBody(required = false) ReviewDecisionRequest body,
                                                      @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ReviewView.from(reviewService.submitForReview(
                id, principal.getTenantId(), principal.getUserId(), remarkOf(body))));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MLRO')")
    public ResponseEntity<ReviewView> approve(@PathVariable UUID id,
                                              @RequestBody(required = false) ReviewDecisionRequest body,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ReviewView.from(reviewService.approve(
                id, principal.getTenantId(), principal.getUserId(), remarkOf(body))));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MLRO')")
    public ResponseEntity<ReviewView> reject(@PathVariable UUID id,
                                             @RequestBody(required = false) ReviewDecisionRequest body,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ReviewView.from(reviewService.reject(
                id, principal.getTenantId(), principal.getUserId(), remarkOf(body))));
    }

    /** The review queue — reports awaiting MLRO review for this tenant. */
    @GetMapping("/review-queue")
    @PreAuthorize("hasAnyRole('MLRO','TENANT_ADMIN')")
    public ResponseEntity<List<ReportView>> reviewQueue() {
        return ResponseEntity.ok(reviewService.reviewQueue().stream().map(ReportView::from).toList());
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('MLRO')")
    public ResponseEntity<SubmissionView> submit(@PathVariable UUID id,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(SubmissionView.from(
                submissionService.submit(id, principal.getTenantId(), principal.getUserId())));
    }

    private static String remarkOf(ReviewDecisionRequest body) {
        return body == null ? null : body.remark();
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<StatusView> status(@PathVariable UUID id,
                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(StatusView.from(
                submissionService.refreshStatus(id, principal.getTenantId())));
    }
}
