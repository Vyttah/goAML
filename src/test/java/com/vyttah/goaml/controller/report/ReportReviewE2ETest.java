package com.vyttah.goaml.controller.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.b2b.GoamlB2bClient;
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
 * Phase D.2 end-to-end: the per-tenant review gate. With {@code tenant_goaml_config.review_required = true},
 * a VALID report must go VALID → PENDING_REVIEW → APPROVED before it can be submitted; submit-before-approve
 * is a conflict, approve/reject are MLRO-only, and a rejection requires a remark. Runs over real HTTP +
 * Testcontainers Postgres with the B2B client mocked.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GoamlApplication.class)
@Testcontainers
class ReportReviewE2ETest {

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

    private Tenant tenant;

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

    @BeforeEach
    void setUp() {
        tenant = provisioningService.provision(new TenantProvisioningRequest(
                "rev-e2e-" + UUID.randomUUID().toString().substring(0, 8), "Review E2E FZE", "AE",
                "admin-" + UUID.randomUUID() + "@e2e.test", "P@ssw0rd!", "Adm", "In"));
        jdbcTemplate.update("""
                INSERT INTO public.tenant_goaml_config
                  (id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode, review_required)
                VALUES (?, ?, 'AE', 3177, 'https://goaml.test/uae', 'goaml/e2e/creds', 'TOKEN', true)
                """, UUID.randomUUID(), tenant.getId());
    }

    @Test
    void reviewGateRunsValidThroughPendingReviewToApprovedThenSubmits() {
        when(b2bClient.postReport(any(), any(), any())).thenReturn("RK-REV");
        String mlro = user("mlro", "MLRO");

        String reportId = createValid("REV-1", mlro);

        // submit directly → blocked, must be APPROVED
        ResponseEntity<JsonNode> early = post("/api/v1/reports/" + reportId + "/submit", "", mlro);
        assertThat(early.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(early.getBody().get("message").asText()).contains("must be APPROVED");

        // submit for review → PENDING_REVIEW, shows in the queue
        ResponseEntity<JsonNode> forReview = post("/api/v1/reports/" + reportId + "/submit-for-review", "", mlro);
        assertThat(forReview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(forReview.getBody().get("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(get("/api/v1/reports/review-queue", mlro).getBody().toString()).contains("REV-1");

        // approve → APPROVED with reviewer recorded
        ResponseEntity<JsonNode> approved = post("/api/v1/reports/" + reportId + "/approve",
                "{\"remark\":\"looks good\"}", mlro);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.getBody().get("reviewedBy").isNull()).isFalse();

        // now submit succeeds
        ResponseEntity<JsonNode> submitted = post("/api/v1/reports/" + reportId + "/submit", "", mlro);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(submitted.getBody().get("status").asText()).isEqualTo("SUBMITTED");
    }

    @Test
    void rejectReturnsReportToValidAndRequiresARemark() {
        String mlro = user("rej", "MLRO");
        String reportId = createValid("REV-REJ", mlro);
        post("/api/v1/reports/" + reportId + "/submit-for-review", "", mlro);

        // reject with no remark → 400
        assertThat(post("/api/v1/reports/" + reportId + "/reject", "", mlro).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // reject with a remark → back to VALID
        ResponseEntity<JsonNode> rejected = post("/api/v1/reports/" + reportId + "/reject",
                "{\"remark\":\"missing party id\"}", mlro);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getBody().get("status").asText()).isEqualTo("VALID");

        // approving a non-PENDING_REVIEW report → 409
        assertThat(post("/api/v1/reports/" + reportId + "/approve", "", mlro).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void analystCanSubmitForReviewButCannotApprove() {
        String analyst = user("analyst", "ANALYST");
        String reportId = createValid("REV-RBAC", analyst);

        assertThat(post("/api/v1/reports/" + reportId + "/submit-for-review", "", analyst).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post("/api/v1/reports/" + reportId + "/approve", "", analyst).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void submitForReviewIsRejectedWhenTheTenantHasReviewDisabled() {
        String mlro = user("off", "MLRO");
        String reportId = createValid("REV-OFF", mlro);

        jdbcTemplate.update("UPDATE public.tenant_goaml_config SET review_required = false WHERE tenant_id = ?",
                tenant.getId());

        ResponseEntity<JsonNode> resp = post("/api/v1/reports/" + reportId + "/submit-for-review", "", mlro);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().get("message").asText()).contains("not enabled");
    }

    // ----- helpers -----

    private String createValid(String ref, String jwt) {
        ResponseEntity<JsonNode> created = post("/api/v1/reports", String.format(DPMSR_JSON, ref), jwt);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("status").asText()).isEqualTo("VALID");
        return created.getBody().get("reportId").asText();
    }

    private String user(String prefix, String roleName) {
        String email = prefix + "-" + UUID.randomUUID() + "@e2e.test";
        String password = "P@ssw0rd!";
        Role role = roleRepository.findByName(roleName).orElseThrow();
        AppUser u = new AppUser(UUID.randomUUID(), tenant.getId(), email,
                passwordEncoder.encode(password), prefix, "User", "ACTIVE");
        u.addRole(role);
        userRepository.save(u);
        return login(email, password);
    }

    private String login(String email, String password) {
        ResponseEntity<JsonNode> resp = post("/api/v1/auth/login",
                String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password), null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> post(String path, String body, String jwt) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(jwt)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String path, String jwt) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(jwt)), JsonNode.class);
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
