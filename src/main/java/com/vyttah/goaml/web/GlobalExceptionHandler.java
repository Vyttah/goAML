package com.vyttah.goaml.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Maps {@link AccessDeniedException} thrown from controller invocations
 * (method security via {@code @PreAuthorize}) to {@code 403 Forbidden}.
 *
 * <p>Why: when {@code @PreAuthorize} denies a method, the resulting exception travels
 * back through {@code DispatcherServlet}. Without this advice, the default behavior
 * inside {@code ExceptionTranslationFilter} can mis-classify the auth state and emit
 * a {@code 401} instead of a {@code 403}.
 *
 * <p>{@code AuthenticationException} is intentionally left to Spring Security's
 * configured {@code AuthenticationEntryPoint} ({@code HttpStatusEntryPoint(401)} via
 * {@code response.sendError}) — handling it here as a JSON body trips clients that
 * retry-on-401 with credentials (e.g. JDK {@code HttpURLConnection}).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "status", HttpStatus.FORBIDDEN.value(),
                "error", "Forbidden",
                "message", ex.getMessage() == null ? "" : ex.getMessage()));
    }
}
