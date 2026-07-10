package com.vyttah.goaml.controller.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.model.dto.ingestion.ImportJobView;
import com.vyttah.goaml.security.UserPrincipal;
import com.vyttah.goaml.service.ingestion.IngestionExceptions;
import com.vyttah.goaml.service.ingestion.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * REST API for report file imports (Phase 11). Thin — delegates to {@link IngestionService}; tenant + actor
 * come from the authenticated {@link UserPrincipal}. RBAC: ANALYST or MLRO (same as report create). A
 * created job carries per-row results; a whole-file rejection is a 400.
 */
@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class ImportController {

    private final IngestionService ingestionService;
    private final ObjectMapper objectMapper;

    @PostMapping(path = "/xml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<ImportJobView> importXml(@RequestParam("file") MultipartFile file,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        var job = ingestionService.importXml(readBytes(file), file.getOriginalFilename(),
                principal.getTenantId(), principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ImportJobView.from(job, objectMapper));
    }

    @PostMapping(path = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<ImportJobView> importCsv(@RequestParam("file") MultipartFile file,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        var job = ingestionService.importCsv(readBytes(file), file.getOriginalFilename(),
                principal.getTenantId(), principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ImportJobView.from(job, objectMapper));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<List<ImportJobView>> list() {
        return ResponseEntity.ok(ingestionService.list().stream()
                .map(j -> ImportJobView.from(j, objectMapper)).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<ImportJobView> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ImportJobView.from(ingestionService.get(id), objectMapper));
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IngestionExceptions.ImportRejectedException("No file uploaded (empty 'file' part)");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IngestionExceptions.ImportRejectedException("Could not read uploaded file: " + e.getMessage());
        }
    }
}
