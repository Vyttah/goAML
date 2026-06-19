package com.vyttah.goaml.controller.connection;

import com.vyttah.goaml.model.dto.connection.ConnectionViews.ConnectionView;
import com.vyttah.goaml.security.UserPrincipal;
import com.vyttah.goaml.service.connection.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only "my goAML connection" for the authenticated user — the linked goAML tenant + active reporting
 * person + whether FIU config is set. Any tenant role may read it (no {@code @PreAuthorize}); sibling apps
 * (the AML cockpit) call it with their federated JWT to show the connection in their own settings screen.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class ConnectionController {

    private final ConnectionService connectionService;

    @GetMapping("/connection")
    public ResponseEntity<ConnectionView> myConnection(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(connectionService.getConnection(principal.getTenantId()));
    }
}
