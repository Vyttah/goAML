package com.vyttah.goaml.config.observability;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 14 observability wiring (full context + Testcontainers): the Prometheus scrape endpoint serves
 * metrics to authenticated callers (in-cluster scrape only — D2 moved it behind auth so it is not public over
 * the single `/` ingress), and every response carries a correlation id (generated or echoed).
 *
 * <p>The Prometheus endpoint is enabled via {@code management.prometheus.metrics.export.enabled=true} in
 * application.yml — the specific key is honored even in tests (Boot otherwise disables metrics export there).
 */
@SpringBootTest(classes = GoamlApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class ObservabilityIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbcTemplate;

    /** A real ACTIVE app_user so the token's sub passes the B16 disabled-user check in JwtAuthFilter. */
    private UUID userId;

    @BeforeEach
    void seedUser() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO public.app_user
                  (id, tenant_id, email, password_hash, first_name, last_name, status, created_at, updated_at)
                VALUES (?, NULL, ?, 'hash', 'Obs', 'User', 'ACTIVE', ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, userId, "obs-" + userId + "@test", OffsetDateTime.now(), OffsetDateTime.now());
    }

    private String token() {
        AppUser user = new AppUser(userId, null, "obs-" + userId + "@test", "hash",
                "Obs", "User", "ACTIVE");
        return jwtService.issueAccessToken(user, "public").token();
    }

    @Test
    void prometheusEndpointIsUnauthorizedWithoutAuth() throws Exception {
        // D2 — the metrics scrape is no longer public; an unauthenticated request must be rejected.
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void prometheusEndpointServesMetricsWhenAuthenticated() throws Exception {
        mvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_")));
    }

    @Test
    void everyResponseGetsACorrelationId() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", notNullValue()));
    }

    @Test
    void inboundCorrelationIdIsEchoed() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Correlation-Id", "trace-xyz"))
                .andExpect(header().string("X-Correlation-Id", "trace-xyz"));
    }
}
