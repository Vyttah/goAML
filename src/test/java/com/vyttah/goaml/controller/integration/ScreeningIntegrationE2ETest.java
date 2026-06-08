package com.vyttah.goaml.controller.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.repository.federated.TenantExternalRefRepository;
import com.vyttah.goaml.repository.federated.TrustedServiceRepository;
import com.vyttah.goaml.service.tenant.TenantProvisioningService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 1.5c.2 end-to-end (MockMvc): screening party push → reusable screened_subject (mapped goAML parties),
 * idempotency, fetch, and service-assertion auth — over the full filter chain + Testcontainers Postgres.
 */
@SpringBootTest(classes = GoamlApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class ScreeningIntegrationE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TrustedServiceRepository trustedServices;
    @Autowired TenantExternalRefRepository tenantExternalRefs;

    private static final int COMPANY_ID = 501;
    private static KeyPair keys;

    @BeforeEach
    void setUp() throws Exception {
        tenantExternalRefs.deleteAll();
        trustedServices.deleteAll();

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();

        Tenant tenant = provisioningService.provision(new TenantProvisioningRequest(
                "scr-e2e-" + UUID.randomUUID().toString().substring(0, 8), "Screening E2E FZE", "AE",
                "admin-" + UUID.randomUUID() + "@scr.test", "Sup3rS3cret!", "Scr", "Admin"));

        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.SCREENING,
                "screening", pem(keys.getPublic()), false, "ACTIVE"));
        tenantExternalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenant.getId(),
                SourceSystem.SCREENING, String.valueOf(COMPANY_ID)));
    }

    @Test
    void legalSubjectIsStoredWithMappedPartiesAndIsIdempotent() throws Exception {
        JsonNode res = push(legalPayload(COMPANY_ID, "LEG-1"));
        assertThat(res.get("subjectRef").asText()).isEqualTo("SCR-501-LEG-1");
        assertThat(res.get("subjectType").asText()).isEqualTo("LEGAL");
        assertThat(res.get("displayName").asText()).isEqualTo("Risky Trading FZE");
        assertThat(res.get("riskFlag").asBoolean()).isTrue();
        // customer + 1 shareholder
        assertThat(res.get("parties")).hasSize(2);
        assertThat(res.get("parties").get(0).get("entity").get("name").asText()).isEqualTo("Risky Trading FZE");
        assertThat(res.get("parties").get(0).get("comments").asText()).contains("OFAC");
        assertThat(res.get("sanctionsContext").asText()).contains("hit(s)");

        // fetch
        JsonNode got = getJson("/api/v1/integration/screening/subjects/LEG-1");
        assertThat(got.get("subjectRef").asText()).isEqualTo("SCR-501-LEG-1");
        assertThat(got.get("parties")).hasSize(2);

        // idempotent re-push (no duplicate, same ref) — and the list has exactly one
        push(legalPayload(COMPANY_ID, "LEG-1"));
        MvcResult listRes = mvc.perform(get("/api/v1/integration/screening/subjects")
                        .header("X-Service-Assertion", assertion())
                        .param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(listRes.getResponse().getContentAsString())).hasSize(1);
    }

    @Test
    void naturalSubjectMapsToPerson() throws Exception {
        JsonNode res = push("""
                {"companyId": %d, "customerUid": "NAT-1", "subjectType": "NATURAL",
                 "natural": {"firstName":"Jane","lastName":"Doe","nationality":"AE","emiratesId":"784-1","pep":true}}
                """.formatted(COMPANY_ID));
        assertThat(res.get("subjectType").asText()).isEqualTo("NATURAL");
        assertThat(res.get("displayName").asText()).isEqualTo("Jane Doe");
        assertThat(res.get("parties").get(0).get("person").get("firstName").asText()).isEqualTo("Jane");
        assertThat(res.get("parties").get(0).get("comments").asText()).contains("PEP");
    }

    @Test
    void missingAssertionIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/integration/screening/subjects")
                        .contentType(APPLICATION_JSON).content(legalPayload(COMPANY_ID, "LEG-2")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unmappedCompanyIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/integration/screening/subjects")
                        .header("X-Service-Assertion", assertion())
                        .contentType(APPLICATION_JSON).content(legalPayload(999, "LEG-3")))
                .andExpect(status().isNotFound());
    }

    @Test
    void fetchingUnknownSubjectIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/integration/screening/subjects/NOPE")
                        .header("X-Service-Assertion", assertion())
                        .param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private JsonNode push(String body) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/integration/screening/subjects")
                        .header("X-Service-Assertion", assertion())
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode getJson(String path) throws Exception {
        MvcResult r = mvc.perform(get(path)
                        .header("X-Service-Assertion", assertion())
                        .param("companyId", String.valueOf(COMPANY_ID)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private static String legalPayload(int companyId, String uid) {
        return """
                {
                  "companyId": %d, "customerUid": "%s", "subjectType": "LEGAL",
                  "legal": {"legalName":"Risky Trading FZE","incorporationNumber":"INC-1","incorporationCountry":"AE"},
                  "shareholders": [
                    {"partyType":"NATURAL","fullName":"Mona Ali","nationality":"AE","pep":true,"shareholdingPercent":55}
                  ],
                  "sanctions": {"riskFlag": true, "hits": [
                    {"name":"Risky Trading FZE","score":95,"sourceList":"OFAC","category":"SANCTIONS"}
                  ]}
                }""".formatted(companyId, uid);
    }

    private static String assertion() {
        return Jwts.builder()
                .issuer("SCREENING")
                .subject("screening-system")
                .audience().add("goaml").and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(60, ChronoUnit.SECONDS)))
                .signWith(keys.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
