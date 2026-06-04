package com.vyttah.goaml.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.repository.audit.AuditLogRepository;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import com.vyttah.goaml.config.tenant.TenantContext;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 sub-step 2.4 — end-to-end auth flow.
 *
 * <p>Provisions a tenant + admin, performs login, and confirms the JWT unlocks the
 * protected {@code /api/v1/me} endpoint while also propagating tenant context.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = GoamlApplication.class)
@Testcontainers
class AuthFlowTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired ObjectMapper objectMapper;
    @Autowired AuditLogRepository auditLogRepository;

    @Test
    void loginIssuesJwtAndProtectedEndpointAcceptsIt() throws Exception {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "auth-tenant", "Auth Test FZE", "AE",
                "mlro@auth.test", "Sup3rS3cret!", "Auth", "Admin"));

        // unauthenticated /me must be 401
        ResponseEntity<String> anon = rest.getForEntity("/api/v1/me", String.class);
        assertThat(anon.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // login
        ResponseEntity<JsonNode> login = postJson("/api/v1/auth/login",
                "{\"email\":\"mlro@auth.test\",\"password\":\"Sup3rS3cret!\"}");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String jwt = login.getBody().get("accessToken").asText();
        assertThat(jwt).isNotBlank();

        // authenticated /me returns identity + tenant context
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        ResponseEntity<JsonNode> me = rest.exchange("/api/v1/me", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email").asText()).isEqualTo("mlro@auth.test");
        assertThat(me.getBody().get("tenantId").asText()).isEqualTo(tenant.getId().toString());
        assertThat(me.getBody().get("tenantSchema").asText()).isEqualTo(tenant.getSchemaName());
        assertThat(me.getBody().get("roles").toString()).contains("TENANT_ADMIN");

        // Audit row recorded in the tenant's own schema.
        TenantContext.set(tenant.getSchemaName());
        try {
            long auditCount = auditLogRepository.count();
            assertThat(auditCount)
                    .as("USER.LOGIN must produce one audit row in the tenant schema")
                    .isEqualTo(1L);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        provisioningService.provision(new TenantProvisioningRequest(
                "auth-bad", "Bad Pass FZE", "AE",
                "bad@auth.test", "Correct!Horse!Battery!Staple!", "Bad", "Pass"));

        ResponseEntity<String> response = postJson("/api/v1/auth/login",
                "{\"email\":\"bad@auth.test\",\"password\":\"wrong\"}", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<JsonNode> postJson(String path, String body) {
        return postJson(path, body, JsonNode.class);
    }

    private <T> ResponseEntity<T> postJson(String path, String body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), type);
    }
}
