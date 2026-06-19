package com.vyttah.goaml.controller.admin;

import com.vyttah.goaml.model.dto.admin.AdminViews.CreateTenantExternalRefRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.CreateTrustedServiceRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.CreateUserRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlConfigView;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlPersonRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.GoamlPersonView;
import com.vyttah.goaml.model.dto.admin.AdminViews.ResetPasswordRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.TenantExternalRefView;
import com.vyttah.goaml.model.dto.admin.AdminViews.TenantView;
import com.vyttah.goaml.model.dto.admin.AdminViews.TrustedServiceView;
import com.vyttah.goaml.model.dto.admin.AdminViews.UpdateTrustedServiceRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.UpdateUserRequest;
import com.vyttah.goaml.model.dto.admin.AdminViews.UserView;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
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
import java.util.Map;
import java.util.UUID;

/**
 * Platform + tenant administration (Phase 13.2). Tenant provisioning/listing is **SUPER_ADMIN**; user
 * management + goAML-config are **TENANT_ADMIN**, scoped to the caller's own tenant (from the JWT principal)
 * — a tenant admin can never touch another tenant's users or config.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/ping")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of("ok", true, "role", "SUPER_ADMIN"));
    }

    // ----- tenants (SUPER_ADMIN) -----

    @PostMapping("/tenants")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TenantView> provisionTenant(@Valid @RequestBody TenantProvisioningRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TenantView.from(adminService.provisionTenant(request)));
    }

    @GetMapping("/tenants")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<TenantView>> listTenants() {
        return ResponseEntity.ok(adminService.listTenants().stream().map(TenantView::from).toList());
    }

    // ----- cross-tenant user management (SUPER_ADMIN) — operate on any tenant by id -----

    @GetMapping("/tenants/{tenantId}/users")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<UserView>> listTenantUsers(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(adminService.listUsers(tenantId).stream().map(UserView::from).toList());
    }

    @PostMapping("/tenants/{tenantId}/users")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserView> createTenantUser(@PathVariable UUID tenantId,
                                                     @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserView.from(adminService.createUser(tenantId, request)));
    }

    @PutMapping("/tenants/{tenantId}/users/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserView> updateTenantUser(@PathVariable UUID tenantId, @PathVariable UUID userId,
                                                     @Valid @RequestBody UpdateUserRequest request,
                                                     @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(UserView.from(
                adminService.updateUser(tenantId, userId, request, principal.getUserId())));
    }

    @DeleteMapping("/tenants/{tenantId}/users/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteTenantUser(@PathVariable UUID tenantId, @PathVariable UUID userId,
                                                 @AuthenticationPrincipal UserPrincipal principal) {
        adminService.deleteUser(tenantId, userId, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/reset-password")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserView> resetTenantUserPassword(@PathVariable UUID tenantId,
                                                            @PathVariable UUID userId,
                                                            @Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(UserView.from(
                adminService.resetUserPassword(tenantId, userId, request.password())));
    }

    // ----- users in the caller's tenant (TENANT_ADMIN) -----

    @PostMapping("/users")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<UserView> createUser(@Valid @RequestBody CreateUserRequest request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserView.from(adminService.createUser(principal.getTenantId(), request)));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<UserView>> listUsers(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(adminService.listUsers(principal.getTenantId())
                .stream().map(UserView::from).toList());
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<UserView> updateUser(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateUserRequest request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(UserView.from(
                adminService.updateUser(principal.getTenantId(), id, request, principal.getUserId())));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        adminService.deleteUser(principal.getTenantId(), id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    // ----- goAML config for the caller's tenant (TENANT_ADMIN) -----

    @GetMapping("/goaml-config")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<GoamlConfigView> getGoamlConfig(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(GoamlConfigView.from(adminService.getGoamlConfig(principal.getTenantId())));
    }

    @PutMapping("/goaml-config")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<GoamlConfigView> upsertGoamlConfig(@Valid @RequestBody GoamlConfigRequest request,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(GoamlConfigView.from(
                adminService.upsertGoamlConfig(principal.getTenantId(), request)));
    }

    // ----- goAML reporting person(s) for the caller's tenant (TENANT_ADMIN) -----
    // The active person is the default goAML auto-injects into every report (so feeds need not send the MLRO).

    @GetMapping("/goaml-persons")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<GoamlPersonView>> listGoamlPersons(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(adminService.listGoamlPersons(principal.getTenantId())
                .stream().map(GoamlPersonView::from).toList());
    }

    @PostMapping("/goaml-persons")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<GoamlPersonView> createGoamlPerson(@Valid @RequestBody GoamlPersonRequest request,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(GoamlPersonView.from(
                adminService.createGoamlPerson(principal.getTenantId(), request)));
    }

    @PutMapping("/goaml-persons/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<GoamlPersonView> updateGoamlPerson(@PathVariable UUID id,
                                                             @Valid @RequestBody GoamlPersonRequest request,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(GoamlPersonView.from(
                adminService.updateGoamlPerson(principal.getTenantId(), id, request)));
    }

    @DeleteMapping("/goaml-persons/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<Void> deleteGoamlPerson(@PathVariable UUID id,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        adminService.deleteGoamlPerson(principal.getTenantId(), id);
        return ResponseEntity.noContent().build();
    }

    // ----- suite connections: trusted services (SUPER_ADMIN) -----
    // Manage federated trust + company→tenant links from the UI instead of hand-run SQL.

    @GetMapping("/trusted-services")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<TrustedServiceView>> listTrustedServices() {
        return ResponseEntity.ok(adminService.listTrustedServices()
                .stream().map(TrustedServiceView::from).toList());
    }

    @PostMapping("/trusted-services")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TrustedServiceView> createTrustedService(
            @Valid @RequestBody CreateTrustedServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TrustedServiceView.from(adminService.createTrustedService(request)));
    }

    @PutMapping("/trusted-services/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TrustedServiceView> updateTrustedService(
            @PathVariable UUID id, @Valid @RequestBody UpdateTrustedServiceRequest request) {
        return ResponseEntity.ok(TrustedServiceView.from(adminService.updateTrustedService(id, request)));
    }

    @DeleteMapping("/trusted-services/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteTrustedService(@PathVariable UUID id) {
        adminService.deleteTrustedService(id);
        return ResponseEntity.noContent().build();
    }

    // ----- suite connections: company → tenant links (SUPER_ADMIN) -----

    @GetMapping("/tenant-external-refs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<TenantExternalRefView>> listTenantExternalRefs() {
        return ResponseEntity.ok(adminService.listTenantExternalRefs()
                .stream().map(TenantExternalRefView::from).toList());
    }

    @PostMapping("/tenant-external-refs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TenantExternalRefView> createTenantExternalRef(
            @Valid @RequestBody CreateTenantExternalRefRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TenantExternalRefView.from(adminService.createTenantExternalRef(request)));
    }

    @DeleteMapping("/tenant-external-refs/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteTenantExternalRef(@PathVariable UUID id) {
        adminService.deleteTenantExternalRef(id);
        return ResponseEntity.noContent().build();
    }
}
