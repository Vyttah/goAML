package com.vyttah.goaml.controller.report;

import com.vyttah.goaml.model.dto.attachment.AttachmentView;
import com.vyttah.goaml.security.UserPrincipal;
import com.vyttah.goaml.service.attachment.AttachmentExceptions;
import com.vyttah.goaml.service.attachment.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * REST API for report attachments. Thin — delegates to {@link AttachmentService}; tenant + actor come from
 * the authenticated {@link UserPrincipal}. Bytes are proxied through the app (multipart), validated, and
 * stored in S3. RBAC: add/remove for ANALYST or MLRO; list also for TENANT_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/reports/{reportId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<AttachmentView> add(@PathVariable UUID reportId,
                                              @RequestParam("file") MultipartFile file,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AttachmentExceptions.AttachmentRejectedException("Could not read uploaded file");
        }
        var saved = attachmentService.add(reportId, principal.getTenantId(), principal.getUserId(),
                file.getOriginalFilename(), file.getContentType(), bytes);
        return ResponseEntity.status(HttpStatus.CREATED).body(AttachmentView.from(saved));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<List<AttachmentView>> list(@PathVariable UUID reportId) {
        return ResponseEntity.ok(attachmentService.list(reportId).stream().map(AttachmentView::from).toList());
    }

    @GetMapping("/{attachmentId}/content")
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<byte[]> download(@PathVariable UUID reportId, @PathVariable UUID attachmentId) {
        AttachmentService.AttachmentDownload dl = attachmentService.download(reportId, attachmentId);
        return ResponseEntity.ok()
                .contentType(parseContentType(dl.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dl.filename() + "\"")
                .body(dl.bytes());
    }

    /** Best-effort parse of the stored content type; falls back to octet-stream for null/garbage. */
    private static MediaType parseContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (org.springframework.http.InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<Void> remove(@PathVariable UUID reportId,
                                       @PathVariable UUID attachmentId,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        attachmentService.remove(reportId, attachmentId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }
}
