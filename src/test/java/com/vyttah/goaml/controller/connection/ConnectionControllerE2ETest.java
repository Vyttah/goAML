package com.vyttah.goaml.controller.connection;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP-layer coverage for {@code GET /api/v1/me/connection} (the read-only "my goAML connection" the AML
 * cockpit shows in its settings) — the endpoint had no HTTP test, only the service. Proves it returns the
 * linked tenant + reporting-entity id + fiuConfigured + active reporting person, <strong>never</strong> the
 * secret config (base URL / secrets path), and degrades cleanly for a tenant with no config.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GoamlApplication.class)
@Testcontainers
class ConnectionControllerE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String SECRET_BASE_URL = "https://goaml.test/uae";
    private static final String SECRETS_PATH = "goaml/conn/creds";

    @Autowired TestRestTemplate rest;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;

    private Tenant tenant;
    private String adminToken;
    private String analystToken;

    @BeforeEach
    void setUp() {
        String adminEmail = "conn-admin-" + UUID.randomUUID() + "@conn.test";
        tenant = provisioningService.provision(new TenantProvisioningRequest(
                "conn-" + UUID.randomUUID().toString().substring(0, 8), "Conn FZE", "AE",
                adminEmail, "P@ssw0rd!", "Conn", "Adm"));
        jdbcTemplate.update("""
                INSERT INTO public.tenant_goaml_config
                  (id, tenant_id, jurisdiction_code, rentity_id, base_url, secrets_path, auth_mode)
                VALUES (?, ?, 'AE', 3177, ?, ?, 'TOKEN')
                """, UUID.randomUUID(), tenant.getId(), SECRET_BASE_URL, SECRETS_PATH);
        adminToken = login(tenant.getSlug(), adminEmail, "P@ssw0rd!");
        analystToken = createAnalyst(tenant);
    }

    @Test
    void connectionReturnsTenantAndConfigFlagButNeverSecrets() {
        ResponseEntity<JsonNode> resp = get("/api/v1/me/connection", analystToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = resp.getBody();
        assertThat(body.get("tenant").get("slug").asText()).isEqualTo(tenant.getSlug());
        assertThat(body.get("tenant").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(body.get("reportingEntityId").asInt()).isEqualTo(3177);
        assertThat(body.get("fiuConfigured").asBoolean()).isTrue();
        assertThat(body.get("activeReportingPerson").isNull()).isTrue();
        // the secret config must never be echoed back to a sibling app
        assertThat(body.toString()).doesNotContain(SECRET_BASE_URL).doesNotContain(SECRETS_PATH);
    }

    @Test
    void connectionShowsTheActiveReportingPerson() {
        ResponseEntity<JsonNode> created = rest.exchange("/api/v1/admin/goaml-persons", HttpMethod.POST,
                new HttpEntity<>("{\"firstName\":\"Aisha\",\"lastName\":\"Khan\",\"occupation\":\"MLRO\","
                        + "\"nationality\":\"AE\",\"email\":\"aisha@conn.test\",\"active\":true}",
                        jsonHeaders(adminToken)), JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> resp = get("/api/v1/me/connection", analystToken);
        JsonNode person = resp.getBody().get("activeReportingPerson");
        assertThat(person.isNull()).isFalse();
        assertThat(person.get("firstName").asText()).isEqualTo("Aisha");
        assertThat(person.get("lastName").asText()).isEqualTo("Khan");
        assertThat(person.get("occupation").asText()).isEqualTo("MLRO");
        assertThat(person.get("nationality").asText()).isEqualTo("AE");
    }

    @Test
    void connectionWithoutConfigReportsNotConfigured() {
        String adminEmail = "noconf-admin-" + UUID.randomUUID() + "@conn.test";
        Tenant noconf = provisioningService.provision(new TenantProvisioningRequest(
                "noconf-" + UUID.randomUUID().toString().substring(0, 8), "NoConfig FZE", "AE",
                adminEmail, "P@ssw0rd!", "No", "Conf"));
        String token = login(noconf.getSlug(), adminEmail, "P@ssw0rd!");

        ResponseEntity<JsonNode> resp = get("/api/v1/me/connection", token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("fiuConfigured").asBoolean()).isFalse();
        assertThat(resp.getBody().get("reportingEntityId").isNull()).isTrue();
        assertThat(resp.getBody().get("activeReportingPerson").isNull()).isTrue();
    }

    // ----- helpers -----

    private String createAnalyst(Tenant tenant) {
        String email = "analyst-" + UUID.randomUUID() + "@conn.test";
        Role role = roleRepository.findByName("ANALYST").orElseThrow();
        AppUser user = new AppUser(UUID.randomUUID(), tenant.getId(), email, passwordEncoder.encode("P@ssw0rd!"),
                "An", "Alyst", "ACTIVE");
        user.addRole(role);
        userRepository.save(user);
        return login(tenant.getSlug(), email, "P@ssw0rd!");
    }

    private String login(String companyId, String email, String password) {
        ResponseEntity<JsonNode> resp = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(String.format("{\"companyId\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                        companyId, email, password), jsonHeaders(null)), JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().get("accessToken").asText();
    }

    private ResponseEntity<JsonNode> get(String path, String jwt) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(jsonHeaders(jwt)), JsonNode.class);
    }

    private HttpHeaders jsonHeaders(String jwt) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (jwt != null) {
            h.setBearerAuth(jwt);
        }
        return h;
    }
}
