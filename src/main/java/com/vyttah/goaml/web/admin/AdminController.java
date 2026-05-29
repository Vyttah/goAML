package com.vyttah.goaml.web.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Platform-admin endpoints. SUPER_ADMIN only.
 *
 * <p>Phase 2 only ships a minimal ping for RBAC verification; later phases add tenant
 * management (create/list/suspend) and goAML credential configuration.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @GetMapping("/ping")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of("ok", true, "role", "SUPER_ADMIN"));
    }
}
