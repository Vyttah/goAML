package com.vyttah.goaml.security;

import com.vyttah.goaml.GoamlApplication;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Systemic 401 guard: every authenticated REST endpoint must reject a request with no bearer token
 * ({@link HttpStatus#UNAUTHORIZED} via the {@code HttpStatusEntryPoint}). Before this sweep, missing-token 401
 * was asserted in only one file ({@code LookupApiTest}) — leaving the report, attachment, admin, import,
 * notification, reportability, screening, connection controllers with no anonymous-access guard at the HTTP
 * layer. {@code /api/v1/auth/**} and {@code /api/v1/integration/**} are deliberately public and excluded.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GoamlApplication.class)
@Testcontainers
class UnauthenticatedAccessE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;

    static Stream<Arguments> protectedEndpoints() {
        String id = UUID.randomUUID().toString();
        return Stream.of(
                arguments(HttpMethod.GET, "/api/v1/me"),
                arguments(HttpMethod.GET, "/api/v1/me/connection"),
                arguments(HttpMethod.GET, "/api/v1/reports"),
                arguments(HttpMethod.POST, "/api/v1/reports"),
                arguments(HttpMethod.POST, "/api/v1/reports/dpmsr"),
                arguments(HttpMethod.GET, "/api/v1/reports/" + id),
                arguments(HttpMethod.GET, "/api/v1/reports/" + id + "/detail"),
                arguments(HttpMethod.GET, "/api/v1/reports/" + id + "/xml"),
                arguments(HttpMethod.GET, "/api/v1/reports/" + id + "/status"),
                arguments(HttpMethod.POST, "/api/v1/reports/" + id + "/submit"),
                arguments(HttpMethod.POST, "/api/v1/reports/" + id + "/submit-for-review"),
                arguments(HttpMethod.POST, "/api/v1/reports/" + id + "/approve"),
                arguments(HttpMethod.POST, "/api/v1/reports/" + id + "/reject"),
                arguments(HttpMethod.GET, "/api/v1/reports/review-queue"),
                arguments(HttpMethod.POST, "/api/v1/reportability/check"),
                arguments(HttpMethod.GET, "/api/v1/reports/" + id + "/attachments"),
                arguments(HttpMethod.GET, "/api/v1/notifications"),
                arguments(HttpMethod.POST, "/api/v1/notifications/" + id + "/read"),
                arguments(HttpMethod.GET, "/api/v1/imports"),
                arguments(HttpMethod.GET, "/api/v1/imports/" + id),
                arguments(HttpMethod.GET, "/api/v1/lookups/jurisdictions"),
                arguments(HttpMethod.GET, "/api/v1/lookups/ae"),
                arguments(HttpMethod.GET, "/api/v1/lookups/ae/countries"),
                arguments(HttpMethod.GET, "/api/v1/screening/subjects"),
                arguments(HttpMethod.GET, "/api/v1/screening/subjects/" + id),
                arguments(HttpMethod.GET, "/api/v1/admin/ping"),
                arguments(HttpMethod.GET, "/api/v1/admin/tenants"),
                arguments(HttpMethod.GET, "/api/v1/admin/users"),
                arguments(HttpMethod.GET, "/api/v1/admin/goaml-config"),
                arguments(HttpMethod.GET, "/api/v1/admin/goaml-persons"),
                arguments(HttpMethod.GET, "/api/v1/admin/trusted-services"),
                arguments(HttpMethod.GET, "/api/v1/admin/tenant-external-refs"),
                arguments(HttpMethod.GET, "/actuator/prometheus")
        );
    }

    @ParameterizedTest(name = "{0} {1} → 401 without a token")
    @MethodSource("protectedEndpoints")
    void protectedEndpointsRejectAnonymousAccess(HttpMethod method, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.exchange(path, method, new HttpEntity<>(headers), String.class);
        assertThat(resp.getStatusCode())
                .as("%s %s must be 401 without a token", method, path)
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
