package com.vyttah.goaml.controller.me;

import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigView;
import com.vyttah.goaml.security.UserPrincipal;
import com.vyttah.goaml.service.admin.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service management of the caller's own tenant goAML B2B config — the "my connection" companion to
 * {@link com.vyttah.goaml.controller.connection.ConnectionController} and
 * {@link MeGoamlPersonsController}. Sibling apps (the AML cockpit) drive this with their federated JWT so a
 * tenant admin can set its FIU submission config without opening goAML's admin SPA.
 *
 * <p>Restricted to <strong>TENANT_ADMIN</strong> (mirroring the TENANT_ADMIN-only
 * {@code /api/v1/admin/goaml-config} endpoints). Everything is scoped to {@code principal.getTenantId()} and
 * delegates to the same {@link AdminService} — no parallel logic. GET returns 404 when no config is set yet
 * ({@code GoamlConfigNotFoundException}); the UI treats that as an empty form.
 */
@RestController
@RequestMapping("/api/v1/me/goaml-config")
@RequiredArgsConstructor
public class MeGoamlConfigController {

    private final AdminService adminService;

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<GoamlConfigView> getGoamlConfig(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(GoamlConfigView.from(adminService.getGoamlConfig(principal.getTenantId())));
    }

    @PutMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<GoamlConfigView> upsertGoamlConfig(@Valid @RequestBody GoamlConfigRequest request,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(GoamlConfigView.from(
                adminService.upsertGoamlConfig(principal.getTenantId(), request)));
    }
}
