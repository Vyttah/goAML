package com.vyttah.goaml.security;

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

/**
 * Schema-per-tenant isolation at the <strong>HTTP boundary</strong> — the gap {@code TenantIsolationTest}
 * (JPA layer) and the admin service tests don't cover. Two tenants, two MLROs, a report in each; a user of
 * tenant B must never read, submit, or attach to tenant A's report through the REST API (and vice-versa).
 * Because both callers are MLRO, any denial here is <em>tenant isolation</em>, not the role gate. The goAML
 * B2B client is mocked (the create/read paths don't call it; this just keeps the context off the network).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GoamlApplication.class)
@Testcontainers
class CrossTenantIsolationE2ETest {

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

    private String mlroA;
    private String mlroB;
    private String reportIdA;

    @BeforeEach
    void setUp() {
        Tenant tenantA = provision("iso-a");
        Tenant tenantB = provision("iso-b");
        mlroA = mlro(tenantA, "mlro-a");
        mlroB = mlro(tenantB, "mlro-b");
        reportIdA = createReport(mlroA, "ISO-A-REF");
        createReport(mlroB, "ISO-B-REF");
    }

    @Test
    void reportListIsScopedToTheCallersTenant() {
        assertThat(get("/api/v1/reports", mlroA).getBody().toString())
                .contains("ISO-A-REF").doesNotContain("ISO-B-REF");
        assertThat(get("/api/v1/reports", mlroB).getBody().toString())
                .contains("ISO-B-REF").doesNotContain("ISO-A-REF");
    }

    @Test
    void cannotReadAnotherTenantsReportThroughAnyReadEndpoint() {
        assertThat(get("/api/v1/reports/" + reportIdA, mlroB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/reports/" + reportIdA + "/detail", mlroB).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/api/v1/reports/" + reportIdA + "/status", mlroB).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<String> xml = rest.exchange("/api/v1/reports/" + reportIdA + "/xml",
                HttpMethod.GET, new HttpEntity<>(headers(mlroB)), String.class);
        assertThat(xml.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // sanity: the owner CAN read it — proves the 404s above are isolation, not a broken report
        assertThat(get("/api/v1/reports/" + reportIdA, mlroA).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void cannotSubmitAnotherTenantsReport() {
        // both callers are MLRO, so this denial is tenant isolation, not the submit role gate
        assertThat(post("/api/v1/reports/" + reportIdA + "/submit", "", mlroB).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cannotListAttachmentsOnAnotherTenantsReport() {
        assertThat(get("/api/v1/reports/" + reportIdA + "/attachments", mlroB).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        // the owner sees a (legitimately empty) attachment list
        ResponseEntity<JsonNode> own = get("/api/v1/reports/" + reportIdA + "/attachments", mlroA);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(own.getBody().isArray()).isTrue();
    }

    // ----- helpers -----

    private Tenant provision(String slug) {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                slug + "-" + UUID.randomUUID().toString().substring(0, 8), "Iso " + slug + " FZE", "AE",
                "admin-" + UUID.randomUUID() + "@iso.test", "P@ssw0rd!", "Iso", "Adm"));
        jdbcTemplate.update("""
                INSERT INTO public.tenant_goaml_config
                  (id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode)
                VALUES (?, ?, 'AE', 3177, 'https://goaml.test/uae', 'goaml/iso/creds', 'TOKEN')
                """, UUID.randomUUID(), tenant.getId());
        return tenant;
    }

    private String mlro(Tenant tenant, String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@iso.test";
        Role role = roleRepository.findByName("MLRO").orElseThrow();
        AppUser user = new AppUser(UUID.randomUUID(), tenant.getId(), email,
                passwordEncoder.encode("P@ssw0rd!"), prefix, "User", "ACTIVE");
        user.addRole(role);
        userRepository.save(user);
        return login(tenant.getSlug(), email, "P@ssw0rd!");
    }

    private String createReport(String jwt, String reference) {
        ResponseEntity<JsonNode> created = post("/api/v1/reports", String.format(DPMSR_JSON, reference), jwt);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody().get("reportId").asText();
    }

    private String login(String companyId, String email, String password) {
        ResponseEntity<JsonNode> resp = post("/api/v1/auth/login",
                String.format("{\"companyId\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                        companyId, email, password), null);
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
