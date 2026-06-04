package com.vyttah.goaml.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 sub-step 2.5 — verifies method-level RBAC.
 *
 * <p>{@code /api/v1/admin/ping} requires SUPER_ADMIN. A TENANT_ADMIN gets 403; a
 * SUPER_ADMIN gets 200. The 401 path is covered by {@link AuthFlowTest}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = GoamlApplication.class)
@Testcontainers
class RbacTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void tenantAdminCannotAccessSuperAdminEndpoint() {
        provisioningService.provision(new TenantProvisioningRequest(
                "rbac-tenant", "RBAC Tenant FZE", "AE",
                "tenantadmin@rbac.test", "Sup3rS3cret!", "Tenant", "Admin"));

        String jwt = login("tenantadmin@rbac.test", "Sup3rS3cret!");

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/admin/ping", HttpMethod.GET,
                bearer(jwt), String.class);

        assertThat(response.getStatusCode())
                .as("TENANT_ADMIN must be forbidden from SUPER_ADMIN endpoints")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void superAdminCanAccessSuperAdminEndpoint() {
        createSuperAdmin("super@rbac.test", "Sup3rPa55!");

        String jwt = login("super@rbac.test", "Sup3rPa55!");

        ResponseEntity<JsonNode> response = rest.exchange(
                "/api/v1/admin/ping", HttpMethod.GET,
                bearer(jwt), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("ok").asBoolean()).isTrue();
    }

    // ----- helpers -----

    private void createSuperAdmin(String email, String password) {
        Role role = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
        AppUser user = new AppUser(
                UUID.randomUUID(), null, email, passwordEncoder.encode(password),
                "Super", "Admin", "ACTIVE");
        user.addRole(role);
        userRepository.save(user);
    }

    private String login(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        ResponseEntity<JsonNode> resp = rest.exchange(
                "/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().get("accessToken").asText();
    }

    private HttpEntity<Void> bearer(String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return new HttpEntity<>(headers);
    }
}
