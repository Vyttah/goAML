package com.vyttah.goaml.controller.me;

import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlPersonRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlPersonView;
import com.vyttah.goaml.security.UserPrincipal;
import com.vyttah.goaml.service.admin.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Self-service management of the caller's own tenant goAML reporting person(s) — the "my connection"
 * companion to {@link com.vyttah.goaml.controller.connection.ConnectionController}. Sibling apps (the AML
 * cockpit) drive this with their federated JWT so a tenant can manage its filing MLRO without opening
 * goAML's admin SPA.
 *
 * <p>Split-permission by design: <strong>MLRO</strong> (the cockpit's default federated role) may only
 * <em>read</em> the persons; <strong>TENANT_ADMIN</strong> may add / update / activate / delete. Everything
 * is scoped to {@code principal.getTenantId()} and delegates to the same {@link AdminService} the
 * TENANT_ADMIN-only {@code /api/v1/admin/goaml-persons} endpoints use — no parallel logic.
 */
@RestController
@RequestMapping("/api/v1/me/goaml-persons")
@RequiredArgsConstructor
public class MeGoamlPersonsController {

    private final AdminService adminService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MLRO')")
    public ResponseEntity<List<GoamlPersonView>> listGoamlPersons(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(adminService.listGoamlPersons(principal.getTenantId())
                .stream().map(GoamlPersonView::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<GoamlPersonView> createGoamlPerson(@Valid @RequestBody GoamlPersonRequest request,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(GoamlPersonView.from(
                adminService.createGoamlPerson(principal.getTenantId(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<GoamlPersonView> updateGoamlPerson(@PathVariable UUID id,
                                                             @Valid @RequestBody GoamlPersonRequest request,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(GoamlPersonView.from(
                adminService.updateGoamlPerson(principal.getTenantId(), id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<Void> deleteGoamlPerson(@PathVariable UUID id,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        adminService.deleteGoamlPerson(principal.getTenantId(), id);
        return ResponseEntity.noContent().build();
    }
}
