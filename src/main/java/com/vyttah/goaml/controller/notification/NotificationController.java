package com.vyttah.goaml.controller.notification;

import com.vyttah.goaml.model.dto.notification.NotificationView;
import com.vyttah.goaml.security.UserPrincipal;
import com.vyttah.goaml.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST API for a user's own in-app notifications (Phase 10). Thin — delegates to
 * {@link NotificationService}; the recipient is the authenticated {@link UserPrincipal}, and the tenant
 * schema is bound by the JWT filter, so a user only ever sees/marks their own rows. Restricted to tenant
 * roles: notifications live in tenant schemas, so a tenantless platform SUPER_ADMIN has none — gating here
 * returns a clean 403 instead of a 500 from querying a table absent in the {@code public} schema.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<List<NotificationView>> list(
            @RequestParam(name = "unread", defaultValue = "false") boolean unread,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(notificationService.list(principal.getUserId(), unread)
                .stream().map(NotificationView::from).toList());
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('ANALYST','MLRO','TENANT_ADMIN')")
    public ResponseEntity<NotificationView> markRead(@PathVariable UUID id,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(NotificationView.from(
                notificationService.markRead(principal.getUserId(), id)));
    }
}
