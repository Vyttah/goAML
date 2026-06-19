package com.vyttah.goaml.controller.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.b2b.GoamlB2bClient;
import com.vyttah.goaml.b2b.error.B2bTransportException;
import com.vyttah.goaml.b2b.error.B2bValidationException;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Proves the controller → {@code GlobalExceptionHandler} mapping for FIU submit failures end-to-end. These
 * paths are unit-tested in {@code DefaultSubmissionServiceTest}, but every E2E so far mocked a <em>successful</em>
 * submit, so the HTTP status for a real FIU rejection / transport error was never asserted over the web:
 * a 400 from the FIU → {@code 422} (carrying the FIU error body), an auth/transport failure → {@code 502}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GoamlApplication.class)
@Testcontainers
class SubmissionErrorMappingE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean GoamlB2bClient b2bClient;

    @Autowired TestRestTemplate rest;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;

    private static final String DPMSR_JSON = """
            {
              "entityReference": "%s",
              "submissionDate": "2026-06-02T12:00:00Z",
              "reason": "DPMS threshold met", "action": "Filed",
              "indicators": ["DPMSJ"],
              "reportingPerson": {"firstName": "Sara", "lastName": "Khan"},
              "parties": [{"reason": "Seller", "entity":
                  {"name": "Minimal Trading FZE", "incorporationNumber": "123456", "incorporationCountryCode": "AE"}}],
              "goods": [{"itemType": "GOLD", "estimatedValue": 90000.00, "currencyCode": "AED"}]
            }""";

    private String mlro;

    @BeforeEach
    void setUp() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "suberr-" + UUID.randomUUID().toString().substring(0, 8), "Submit Error FZE", "AE",
                "suberr-admin-" + UUID.randomUUID() + "@suberr.test", "P@ssw0rd!", "S", "E"));
        jdbcTemplate.update("""
                INSERT INTO public.tenant_goaml_config
                  (id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode)
                VALUES (?, ?, 'AE', 3177, 'https://goaml.test/uae', 'goaml/suberr/creds', 'TOKEN')
                """, UUID.randomUUID(), tenant.getId());
        mlro = mlro(tenant.getId());
    }

    @Test
    void fiuRejectionMapsToUnprocessableEntityWithTheFiuErrorBody() {
        when(b2bClient.postReport(any(), any(), any()))
                .thenThrow(new B2bValidationException("FIU rejected", "<error>bad rentity</error>"));
        String reportId = createValidReport("SUBERR-REJECT");

        ResponseEntity<JsonNode> resp = post("/api/v1/reports/" + reportId + "/submit");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().toString()).contains("fiuError");
    }

    @Test
    void transportFailureMapsToBadGateway() {
        when(b2bClient.postReport(any(), any(), any()))
                .thenThrow(new B2bTransportException("connection timed out"));
        String reportId = createValidReport("SUBERR-TIMEOUT");

        assertThat(post("/api/v1/reports/" + reportId + "/submit").getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    // ----- helpers -----

    private String createValidReport(String reference) {
        ResponseEntity<JsonNode> created = post("/api/v1/reports", String.format(DPMSR_JSON, reference));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("status").asText()).isEqualTo("VALID");
        return created.getBody().get("reportId").asText();
    }

    private String mlro(UUID tenantId) {
        String email = "mlro-" + UUID.randomUUID() + "@suberr.test";
        Role role = roleRepository.findByName("MLRO").orElseThrow();
        AppUser u = new AppUser(UUID.randomUUID(), tenantId, email, passwordEncoder.encode("P@ssw0rd!"),
                "Mel", "Roe", "ACTIVE");
        u.addRole(role);
        userRepository.save(u);
        ResponseEntity<JsonNode> resp = post("/api/v1/auth/login",
                String.format("{\"email\":\"%s\",\"password\":\"P@ssw0rd!\"}", email));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> post(String path) {
        return post(path, "");
    }

    private ResponseEntity<JsonNode> post(String path, String body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (mlro != null && !path.contains("/auth/login")) {
            h.setBearerAuth(mlro);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
    }
}
