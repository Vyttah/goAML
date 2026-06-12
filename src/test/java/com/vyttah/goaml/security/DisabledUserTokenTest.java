package com.vyttah.goaml.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.model.entity.tenant.Tenant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B16 — a JWT that is still in-lifetime must stop working the moment its user is DISABLED. The
 * {@link JwtAuthFilter} consults {@link UserStatusCache}; a non-ACTIVE user is rejected (401) even with a
 * valid signature/expiry. The test evicts the cache to make the disable take effect immediately (in prod a
 * short TTL bounds the window).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = GoamlApplication.class)
@Testcontainers
class DisabledUserTokenTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserStatusCache userStatusCache;

    @Test
    void validTokenForActiveUserWorksThenStopsAfterDisable() {
        String email = "disable-" + UUID.randomUUID() + "@b16.test";
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "b16-" + UUID.randomUUID().toString().substring(0, 8), "B16 FZE", "AE",
                email, "Sup3rS3cret!", "Dis", "Abled"));

        // Login → JWT for an ACTIVE user.
        ResponseEntity<JsonNode> login = postJson("/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"Sup3rS3cret!\"}");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String jwt = login.getBody().get("accessToken").asText();

        // ACTIVE → the token unlocks /me.
        assertThat(me(jwt).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Disable the user out-of-band (admin would do this) and evict the cached verdict.
        int updated = jdbcTemplate.update(
                "UPDATE public.app_user SET status = 'DISABLED' WHERE email = ?", email);
        assertThat(updated).isEqualTo(1);
        UUID userId = jdbcTemplate.queryForObject(
                "SELECT id FROM public.app_user WHERE email = ?", UUID.class, email);
        userStatusCache.evict(userId);

        // Same still-valid token now fails — the disabled user is rejected.
        assertThat(me(jwt).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<JsonNode> me(String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return rest.exchange("/api/v1/me", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> postJson(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
