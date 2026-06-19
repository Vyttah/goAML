package com.vyttah.goaml.controller.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.role.Role;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.role.RoleRepository;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import org.junit.jupiter.api.BeforeEach;
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
 * 404-on-missing-id coverage for the resource-by-id endpoints whose not-found path wasn't exercised over HTTP:
 * an unknown import job, an unknown screened subject, and submitting a report that doesn't exist (in the
 * caller's tenant). Each is an authenticated, correctly-roled call, so the 404 is the service's not-found
 * mapping — distinct from the 401 (no token) and 403 (wrong role) sweeps.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GoamlApplication.class)
@Testcontainers
class NotFoundPathsE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String mlro;

    @BeforeEach
    void setUp() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "nf-" + UUID.randomUUID().toString().substring(0, 8), "NotFound FZE", "AE",
                "nf-admin-" + UUID.randomUUID() + "@nf.test", "P@ssw0rd!", "N", "F"));
        mlro = user(tenant.getId(), "MLRO");
    }

    @Test
    void unknownImportJobIsNotFound() {
        assertThat(get("/api/v1/imports/" + UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownScreenedSubjectIsNotFound() {
        assertThat(get("/api/v1/screening/subjects/UNKNOWN-" + UUID.randomUUID()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void submittingAMissingReportIsNotFound() {
        assertThat(post("/api/v1/reports/" + UUID.randomUUID() + "/submit").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----- helpers -----

    private String user(UUID tenantId, String roleName) {
        String email = roleName.toLowerCase() + "-" + UUID.randomUUID() + "@nf.test";
        Role role = roleRepository.findByName(roleName).orElseThrow();
        AppUser u = new AppUser(UUID.randomUUID(), tenantId, email, passwordEncoder.encode("P@ssw0rd!"),
                "F", "L", "ACTIVE");
        u.addRole(role);
        userRepository.save(u);
        ResponseEntity<JsonNode> resp = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(String.format("{\"email\":\"%s\",\"password\":\"P@ssw0rd!\"}", email),
                        headers(null)), JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> get(String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(mlro)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String path) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>("", headers(mlro)), JsonNode.class);
    }

    private HttpHeaders headers(String jwt) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (jwt != null) {
            h.setBearerAuth(jwt);
        }
        return h;
    }
}
