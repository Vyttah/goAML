package com.vyttah.goaml.security;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Method-level RBAC denial matrix at the HTTP boundary — extends {@code RbacTest} (one SUPER_ADMIN endpoint)
 * to the gaps the audit flagged: the admin surface (TENANT_ADMIN vs SUPER_ADMIN routes), the report
 * author/review gates, and that a SUPER_ADMIN (platform operator, no tenant) cannot reach tenant data.
 * An authenticated caller with the wrong role gets 403 (AccessDenied), distinct from the 401 sweep. The
 * {@code @PreAuthorize} check runs before the method body, so non-existent ids in the paths are irrelevant.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GoamlApplication.class)
@Testcontainers
class RoleEnforcementE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

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

    private final Map<String, String> tokens = new HashMap<>();
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        String adminEmail = "tadmin-" + UUID.randomUUID() + "@role.test";
        tenant = provisioningService.provision(new TenantProvisioningRequest(
                "role-" + UUID.randomUUID().toString().substring(0, 8), "Role FZE", "AE",
                adminEmail, "P@ssw0rd!", "Tenant", "Admin"));
        tokens.put("TENANT_ADMIN", login(adminEmail, "P@ssw0rd!"));
        tokens.put("ANALYST", tenantUser("ANALYST"));
        tokens.put("MLRO", tenantUser("MLRO"));
        tokens.put("SUPER_ADMIN", superAdmin());
    }

    record Denied(String role, HttpMethod method, String path) {}

    static Stream<Denied> deniedCombos() {
        String id = UUID.randomUUID().toString();
        return Stream.of(
                // ANALYST is blocked from MLRO-only review actions and the review queue
                new Denied("ANALYST", HttpMethod.GET, "/api/v1/reports/review-queue"),
                // ANALYST/MLRO are blocked from the TENANT_ADMIN admin surface
                new Denied("ANALYST", HttpMethod.GET, "/api/v1/admin/users"),
                new Denied("ANALYST", HttpMethod.GET, "/api/v1/admin/goaml-config"),
                new Denied("ANALYST", HttpMethod.GET, "/api/v1/admin/goaml-persons"),
                new Denied("MLRO", HttpMethod.GET, "/api/v1/admin/users"),
                new Denied("MLRO", HttpMethod.GET, "/api/v1/admin/goaml-persons"),
                // ANALYST/MLRO/TENANT_ADMIN are all blocked from the SUPER_ADMIN platform surface
                new Denied("ANALYST", HttpMethod.GET, "/api/v1/admin/ping"),
                new Denied("MLRO", HttpMethod.GET, "/api/v1/admin/trusted-services"),
                new Denied("TENANT_ADMIN", HttpMethod.GET, "/api/v1/admin/ping"),
                new Denied("TENANT_ADMIN", HttpMethod.GET, "/api/v1/admin/tenants"),
                new Denied("TENANT_ADMIN", HttpMethod.GET, "/api/v1/admin/trusted-services"),
                new Denied("TENANT_ADMIN", HttpMethod.GET, "/api/v1/admin/tenant-external-refs"),
                // a SUPER_ADMIN (platform operator, no tenant) cannot reach tenant data
                new Denied("SUPER_ADMIN", HttpMethod.GET, "/api/v1/reports"),
                new Denied("SUPER_ADMIN", HttpMethod.GET, "/api/v1/notifications"),
                new Denied("SUPER_ADMIN", HttpMethod.GET, "/api/v1/admin/users")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deniedCombos")
    void wrongRoleIsForbidden(Denied denied) {
        ResponseEntity<String> resp = rest.exchange(denied.path(), denied.method(),
                new HttpEntity<>(headers(tokens.get(denied.role()))), String.class);
        assertThat(resp.getStatusCode())
                .as("%s %s as %s must be 403", denied.method(), denied.path(), denied.role())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void tenantAdminCannotAuthorReports() {
        ResponseEntity<String> resp = rest.exchange("/api/v1/reports", HttpMethod.POST,
                new HttpEntity<>(String.format(DPMSR_JSON, "ROLE-TA-1"), headers(tokens.get("TENANT_ADMIN"))),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void tenantAdminCannotUseTheCuratedReportPath() {
        ResponseEntity<String> resp = rest.exchange("/api/v1/reports/dpmsr", HttpMethod.POST,
                new HttpEntity<>(String.format(DPMSR_JSON, "ROLE-TA-2"), headers(tokens.get("TENANT_ADMIN"))),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ----- helpers -----

    private String tenantUser(String roleName) {
        String email = roleName.toLowerCase() + "-" + UUID.randomUUID() + "@role.test";
        Role role = roleRepository.findByName(roleName).orElseThrow();
        AppUser user = new AppUser(UUID.randomUUID(), tenant.getId(), email,
                passwordEncoder.encode("P@ssw0rd!"), roleName, "User", "ACTIVE");
        user.addRole(role);
        userRepository.save(user);
        return login(email, "P@ssw0rd!");
    }

    private String superAdmin() {
        String email = "super-" + UUID.randomUUID() + "@role.test";
        Role role = roleRepository.findByName("SUPER_ADMIN").orElseThrow();
        AppUser user = new AppUser(UUID.randomUUID(), null, email,
                passwordEncoder.encode("P@ssw0rd!"), "Super", "Admin", "ACTIVE");
        user.addRole(role);
        userRepository.save(user);
        return login(email, "P@ssw0rd!");
    }

    private String login(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> resp = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password), headers),
                JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody().get("accessToken").asText();
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
