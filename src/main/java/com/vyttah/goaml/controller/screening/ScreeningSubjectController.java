package com.vyttah.goaml.controller.screening;

import com.vyttah.goaml.model.dto.integration.ScreeningSeedRequest;
import com.vyttah.goaml.model.dto.integration.ScreeningSubjectResponse;
import com.vyttah.goaml.model.dto.report.ReportResponses.CreateReportResponse;
import com.vyttah.goaml.security.UserPrincipal;
import com.vyttah.goaml.service.report.ReportResult;
import com.vyttah.goaml.service.screening.ScreeningSubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

/**
 * User-facing API over the screened subjects the AML screening software pushed (Phase 1.5c.3). Unlike the
 * server-to-server {@code /api/v1/integration/screening} push (assertion-authed), this is a normal
 * JWT-authenticated, tenant-scoped API the goAML SPA uses to browse screened customers and seed a DPMSR
 * draft from one. RBAC: read for ANALYST/MLRO/TENANT_ADMIN; seeding (creating a report) for ANALYST/MLRO.
 */
@RestController
@RequestMapping("/api/v1/screening/subjects")
@RequiredArgsConstructor
public class ScreeningSubjectController {

    private final ScreeningSubjectService subjectService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<List<ScreeningSubjectResponse>> list() {
        return ResponseEntity.ok(subjectService.list());
    }

    @GetMapping("/{subjectRef}")
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<ScreeningSubjectResponse> get(@PathVariable String subjectRef) {
        return ResponseEntity.ok(subjectService.get(subjectRef));
    }

    @PostMapping("/{subjectRef}/seed-report")
    @PreAuthorize("hasAnyRole('ANALYST','MLRO')")
    public ResponseEntity<CreateReportResponse> seedReport(
            @PathVariable String subjectRef,
            @Valid @RequestBody ScreeningSeedRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ReportResult result = subjectService.seedReport(
                subjectRef, request, principal.getTenantId(), principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateReportResponse.from(result));
    }
}
