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
 * HTTP-layer coverage for the authoritative reportability check ({@code POST /api/v1/reportability/check}).
 * The verdict logic is unit-tested in {@code ReportabilityDetectorTest}, but the endpoint, its
 * {@code @PreAuthorize}, bean validation, and the AED-only currency guard were never exercised over the web.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GoamlApplication.class)
@Testcontainers
class ReportabilityControllerE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String analystToken;
    private String superAdminToken;

    @BeforeEach
    void setUp() {
        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "rpt-" + UUID.randomUUID().toString().substring(0, 8), "Reportability FZE", "AE",
                "rpt-admin-" + UUID.randomUUID() + "@rpt.test", "P@ssw0rd!", "Rpt", "Adm"));
        analystToken = user(tenant, "ANALYST");
        superAdminToken = user(null, "SUPER_ADMIN");
    }

    @Test
    void cashAboveThresholdInPreciousMetalsIsReportable() {
        JsonNode body = check("{\"amount\":90000,\"currencyCode\":\"AED\","
                + "\"involvesPreciousMetalsOrStones\":true}", analystToken, HttpStatus.OK);
        assertThat(body.get("reportable").asBoolean()).isTrue();
        assertThat(body.get("thresholdAed").asInt()).isEqualTo(55000);
    }

    @Test
    void cashBelowThresholdIsNotReportable() {
        JsonNode body = check("{\"amount\":1000,\"currencyCode\":\"AED\","
                + "\"involvesPreciousMetalsOrStones\":true}", analystToken, HttpStatus.OK);
        assertThat(body.get("reportable").asBoolean()).isFalse();
    }

    @Test
    void nonAedCurrencyIsNotReportableAndExplainsWhy() {
        JsonNode body = check("{\"amount\":90000,\"currencyCode\":\"USD\","
                + "\"involvesPreciousMetalsOrStones\":true}", analystToken, HttpStatus.OK);
        assertThat(body.get("reportable").asBoolean()).isFalse();
        assertThat(body.get("reasons").toString()).contains("AED");
    }

    @Test
    void missingAmountIsBadRequest() {
        ResponseEntity<JsonNode> resp = rest.exchange("/api/v1/reportability/check", HttpMethod.POST,
                new HttpEntity<>("{\"currencyCode\":\"AED\"}", headers(analystToken)), JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void superAdminWithoutATenantRoleIsForbidden() {
        ResponseEntity<JsonNode> resp = rest.exchange("/api/v1/reportability/check", HttpMethod.POST,
                new HttpEntity<>("{\"amount\":90000,\"currencyCode\":\"AED\"}", headers(superAdminToken)),
                JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ----- helpers -----

    private JsonNode check(String json, String jwt, HttpStatus expected) {
        ResponseEntity<JsonNode> resp = rest.exchange("/api/v1/reportability/check", HttpMethod.POST,
                new HttpEntity<>(json, headers(jwt)), JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(expected);
        return resp.getBody();
    }

    private String user(Tenant tenant, String roleName) {
        UUID tenantId = tenant == null ? null : tenant.getId();
        String companyId = tenant == null ? "PLATFORM" : tenant.getSlug();
        String email = roleName.toLowerCase() + "-" + UUID.randomUUID() + "@rpt.test";
        Role role = roleRepository.findByName(roleName).orElseThrow();
        AppUser u = new AppUser(UUID.randomUUID(), tenantId, email, passwordEncoder.encode("P@ssw0rd!"),
                "F", "L", "ACTIVE");
        u.addRole(role);
        userRepository.save(u);
        ResponseEntity<JsonNode> resp = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(String.format("{\"companyId\":\"%s\",\"email\":\"%s\",\"password\":\"P@ssw0rd!\"}",
                        companyId, email), headers(null)), JsonNode.class);
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
