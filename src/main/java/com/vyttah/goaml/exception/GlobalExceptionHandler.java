package com.vyttah.goaml.exception;

import com.vyttah.goaml.service.attachment.AttachmentExceptions;
import com.vyttah.goaml.service.ingestion.IngestionExceptions;
import com.vyttah.goaml.service.notification.NotificationExceptions;
import com.vyttah.goaml.service.report.ReportExceptions;
import com.vyttah.goaml.service.submission.SubmissionExceptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps controller exceptions to HTTP responses with a small JSON body.
 *
 * <p>{@link AccessDeniedException} (method security via {@code @PreAuthorize}) → {@code 403}: without this
 * advice the default behavior inside {@code ExceptionTranslationFilter} can mis-classify the auth state and
 * emit a {@code 401}. {@code AuthenticationException} is intentionally left to Spring Security's
 * {@code AuthenticationEntryPoint} ({@code 401}, no JSON body — a JSON 401 trips retry-on-401 clients).
 *
 * <p>Report/submission/attachment service exceptions map to: not-found → 404, duplicate/conflict/
 * not-submittable/report-not-editable → 409, FIU rejection + packaging-too-large → 422 (rejection also
 * carries the FIU error body), auth/transport → 502, bad input / rejected upload → 400.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler({
            ReportExceptions.ReportNotFoundException.class,
            AttachmentExceptions.AttachmentNotFoundException.class,
            NotificationExceptions.NotificationNotFoundException.class,
            IngestionExceptions.ImportJobNotFoundException.class
    })
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({
            ReportExceptions.DuplicateEntityReferenceException.class,
            SubmissionExceptions.ReportNotSubmittableException.class,
            SubmissionExceptions.TenantConfigMissingException.class,
            AttachmentExceptions.ReportNotEditableException.class
    })
    public ResponseEntity<Map<String, Object>> handleConflict(RuntimeException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SubmissionExceptions.SubmissionRejectedException.class)
    public ResponseEntity<Map<String, Object>> handleRejected(SubmissionExceptions.SubmissionRejectedException ex) {
        Map<String, Object> b = baseBody(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        b.put("fiuError", ex.responseBody());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(b);
    }

    @ExceptionHandler(SubmissionExceptions.SubmissionPackagingException.class)
    public ResponseEntity<Map<String, Object>> handlePackaging(RuntimeException ex) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(SubmissionExceptions.SubmissionTransportException.class)
    public ResponseEntity<Map<String, Object>> handleTransport(RuntimeException ex) {
        return body(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            IllegalArgumentException.class,
            AttachmentExceptions.AttachmentRejectedException.class,
            IngestionExceptions.ImportRejectedException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(baseBody(status, message));
    }

    private static Map<String, Object> baseBody(HttpStatus status, String message) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("status", status.value());
        b.put("error", status.getReasonPhrase());
        b.put("message", message == null ? "" : message);
        return b;
    }
}
