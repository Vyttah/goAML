package com.vyttah.goaml.controller.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (MockMvc + Testcontainers) for the bulk tenant-status endpoint
 * ({@code POST /api/v1/integration/admin/tenants/status}) that backs the AML admin panel's per-company goAML
 * workspace indicator. Verifies it returns only the provisioned companyIds, matches slugs case-insensitively,
 * and echoes back the caller's original casing.
 */
@SpringBootTest(classes = GoamlApplication.class, properties = "goaml.auth.mode=both")
@AutoConfigureMockMvc
@Testcontainers
class IntegrationTenantStatusE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired TrustedServiceRepository trustedServices;

    private KeyPair keys;

    @BeforeEach
    void setUp() {
        trustedServices.deleteAll();
        keys = newKeys();
        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.SCREENING,
                "AML admin", pem(keys.getPublic()), false, "ACTIVE"));
    }

    @Test
    void returnsOnlyProvisionedCompanyIdsCaseInsensitivelyEchoingOriginalCasing() throws Exception {
        String provisioned = "cs-" + UUID.randomUUID().toString().substring(0, 8);
        provisioningService.provision(new TenantProvisioningRequest(
                provisioned, "Provisioned FZE", "AE", null, null, null, null));
        String missing = "cs-" + UUID.randomUUID().toString().substring(0, 8);

        // Ask with the provisioned id upper-cased to prove case-insensitive matching + original-casing echo.
        String provisionedUpper = provisioned.toUpperCase();
        String body = objectMapper.writeValueAsString(
                Map.of("companyIds", List.of(provisionedUpper, missing)));

        MvcResult res = mvc.perform(post("/api/v1/integration/admin/tenants/status")
                        .header("X-Service-Assertion",
                                assertion(keys.getPrivate(), "SCREENING", "aml-admin", provisioned))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();

        JsonNode array = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("provisionedCompanyIds");
        List<String> result = new ArrayList<>();
        array.forEach(node -> result.add(node.asText()));

        assertThat(result).containsExactly(provisionedUpper);
    }

    @Test
    void rejectsAnUnsignedRequest() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("companyIds", List.of("cs-anything")));
        mvc.perform(post("/api/v1/integration/admin/tenants/status")
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private static KeyPair newKeys() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String assertion(PrivateKey key, String issuer, String sub, String org) {
        return Jwts.builder()
                .issuer(issuer)
                .subject(sub)
                .audience().add("goaml").and()
                .issuedAt(java.util.Date.from(Instant.now()))
                .expiration(java.util.Date.from(Instant.now().plus(60, ChronoUnit.SECONDS)))
                .id(UUID.randomUUID().toString())
                .claim("org", org)
                .signWith(key, Jwts.SIG.RS256).compact();
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
