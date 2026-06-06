package com.vyttah.goaml.controller.lookup;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 13.1: the read-only lookup/jurisdiction API + SPA serving + auth boundary. MockMvc over a real
 * context (Testcontainers Postgres needed for startup; the lookup endpoints don't touch the DB). A JWT is
 * minted directly via {@link JwtService} for a synthetic authenticated user (the lookups are
 * {@code isAuthenticated()} reference data — no role/tenant needed).
 */
@SpringBootTest(classes = GoamlApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class LookupApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;

    private String token() {
        AppUser user = new AppUser(UUID.randomUUID(), UUID.randomUUID(), "lookup@test", "hash",
                "Look", "Up", "ACTIVE");
        return jwtService.issueAccessToken(user, "public").token();
    }

    @Test
    void jurisdictionsListsUae() throws Exception {
        mvc.perform(get("/api/v1/lookups/jurisdictions").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='ae')]").exists())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DPMSR")));
    }

    @Test
    void listsSetNamesForJurisdiction() throws Exception {
        mvc.perform(get("/api/v1/lookups/ae").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jurisdiction").value("ae"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("countries")));
    }

    @Test
    void returnsCodesForASet() throws Exception {
        mvc.perform(get("/api/v1/lookups/ae/countries").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.set").value("countries"))
                .andExpect(jsonPath("$.codes").isArray())
                .andExpect(jsonPath("$.codes[0]").exists());
    }

    @Test
    void unknownSetIs404() throws Exception {
        mvc.perform(get("/api/v1/lookups/ae/does-not-exist").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownJurisdictionIs404() throws Exception {
        mvc.perform(get("/api/v1/lookups/zz").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void lookupsRequireAuth() throws Exception {
        mvc.perform(get("/api/v1/lookups/jurisdictions")).andExpect(status().isUnauthorized());
    }

    @Test
    void spaShellIsPublicAndDeepLinksServeIndex() throws Exception {
        // A client-side deep link is served the SPA shell (placeholder index.html) by the fallback
        // resolver — directly streamed, so MockMvc captures the body. No auth needed.
        mvc.perform(get("/dashboard")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("goAML")));
        mvc.perform(get("/reports/123")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("goAML")));
        // "/" is public too (Spring Boot's welcome-page forward to index.html; MockMvc doesn't render
        // forwards, so assert status only here).
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void apiStaysProtectedNotServedAsSpa() throws Exception {
        // an unauthenticated API call must 401 — never fall through to the SPA shell
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }
}
