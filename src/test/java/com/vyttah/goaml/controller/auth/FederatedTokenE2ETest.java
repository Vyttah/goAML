package com.vyttah.goaml.controller.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.appuser.AppUser;
import com.vyttah.goaml.model.entity.federated.ExternalIdentity;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TenantExternalRef;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.model.entity.tenant.Tenant;
import com.vyttah.goaml.model.dto.tenant.TenantProvisioningRequest;
import com.vyttah.goaml.repository.appuser.AppUserRepository;
import com.vyttah.goaml.repository.federated.ExternalIdentityRepository;
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
import java.security.PrivateKey;
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
 * 1.5a.4 end-to-end (MockMvc): federated token-exchange over the full filter chain + Testcontainers Postgres,
 * with {@code goaml.auth.mode=both}. Proves a signed assertion → a usable goAML JWT (existing-mapping + JIT),
 * tenant-scoped, and that a bad assertion → 401. MockMvc (in-process) avoids the embedded-socket 401 flake.
 */
@SpringBootTest(classes = GoamlApplication.class, properties = "goaml.auth.mode=both")
@AutoConfigureMockMvc
@Testcontainers
class FederatedTokenE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired AppUserRepository appUsers;
    @Autowired TrustedServiceRepository trustedServices;
    @Autowired ExternalIdentityRepository externalIdentities;
    @Autowired TenantExternalRefRepository tenantExternalRefs;

    private static KeyPair accountingKeys;
    private Tenant tenant;

    @BeforeEach
    void setUp() throws Exception {
        // The Testcontainers DB is shared across tests in this context (no per-test rollback); reset the
        // federated tables so each test starts clean (trusted_service is UNIQUE per source system).
        externalIdentities.deleteAll();
        tenantExternalRefs.deleteAll();
        trustedServices.deleteAll();

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        accountingKeys = gen.generateKeyPair();

        tenant = provisioningService.provision(new TenantProvisioningRequest(
                "fed-e2e-" + UUID.randomUUID().toString().substring(0, 8), "Fed E2E FZE", "AE",
                "admin-" + UUID.randomUUID() + "@fed.test", "Sup3rS3cret!", "Fed", "Admin"));

        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.ACCOUNTING,
                "accounting", pem(accountingKeys.getPublic()), true, "ACTIVE"));
        tenantExternalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenant.getId(),
                SourceSystem.ACCOUNTING, "ORG-1"));
    }

    @Test
    void existingIdentityIssuesUsableTenantScopedToken() throws Exception {
        AppUser admin = appUsers.findAll().stream()
                .filter(u -> tenant.getId().equals(u.getTenantId())).findFirst().orElseThrow();
        externalIdentities.save(new ExternalIdentity(UUID.randomUUID(), SourceSystem.ACCOUNTING,
                "ext-admin", admin.getEmail(), admin.getId()));

        String token = exchange(SourceSystem.ACCOUNTING,
                assertion(accountingKeys.getPrivate(), "ACCOUNTING", "ext-admin", admin.getEmail(), "ORG-1"));
        assertThat(token).isNotBlank();

        JsonNode me = me(token);
        assertThat(me.get("email").asText()).isEqualTo(admin.getEmail());
        assertThat(me.get("tenantSchema").asText()).isEqualTo(tenant.getSchemaName());
    }

    @Test
    void jitProvisionsNewUserAndIsIdempotent() throws Exception {
        String first = exchange(SourceSystem.ACCOUNTING,
                assertion(accountingKeys.getPrivate(), "ACCOUNTING", "ext-new", "new@fed.test", "ORG-1"));
        JsonNode me = me(first);
        assertThat(me.get("email").asText()).isEqualTo("new@fed.test");
        assertThat(me.get("roles").toString()).contains("ANALYST");
        assertThat(me.get("tenantSchema").asText()).isEqualTo(tenant.getSchemaName());

        // Same external user again → resolves the existing mapping (no duplicate provisioning).
        String second = exchange(SourceSystem.ACCOUNTING,
                assertion(accountingKeys.getPrivate(), "ACCOUNTING", "ext-new", "new@fed.test", "ORG-1"));
        assertThat(second).isNotBlank();
        assertThat(externalIdentities
                .findBySourceSystemAndExternalUserId(SourceSystem.ACCOUNTING, "ext-new")).isPresent();
    }

    @Test
    void jitHonoursTheTrustedServiceDefaultRole() throws Exception {
        // The AML cockpit source declares default_role=MLRO so a JIT-provisioned cockpit user can both
        // create AND approve/submit reports (goAML-as-microservice flow, G1.3).
        KeyPair cockpitKeys = newKeys();
        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.SCREENING,
                "AML cockpit", pem(cockpitKeys.getPublic()), true, "ACTIVE", "MLRO"));
        tenantExternalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenant.getId(),
                SourceSystem.SCREENING, "ORG-SCR"));

        String token = exchange(SourceSystem.SCREENING, assertion(cockpitKeys.getPrivate(),
                "SCREENING", "cockpit-user", "cockpit@aml.test", "ORG-SCR"));
        JsonNode me = me(token);
        assertThat(me.get("roles").toString()).contains("MLRO");
        assertThat(me.get("tenantSchema").asText()).isEqualTo(tenant.getSchemaName());
    }

    @Test
    void badAssertionSignatureReturns401() throws Exception {
        KeyPair wrong = newKeys();
        mvc.perform(post("/api/v1/auth/federated/token")
                        .contentType(APPLICATION_JSON)
                        .content(tokenBody(SourceSystem.ACCOUNTING,
                                assertion(wrong.getPrivate(), "ACCOUNTING", "ext-admin", "x@fed.test", "ORG-1"))))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private String exchange(SourceSystem source, String assertion) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/federated/token")
                        .contentType(APPLICATION_JSON)
                        .content(tokenBody(source, assertion)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private JsonNode me(String token) throws Exception {
        MvcResult res = mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private static String tokenBody(SourceSystem source, String assertion) {
        return "{\"sourceSystem\":\"" + source + "\",\"assertion\":\"" + assertion + "\"}";
    }

    private static KeyPair newKeys() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String assertion(PrivateKey key, String issuer, String sub, String email, String org) {
        return Jwts.builder()
                .issuer(issuer)
                .subject(sub)
                .audience().add("goaml").and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(60, ChronoUnit.SECONDS)))
                .id(java.util.UUID.randomUUID().toString())  // B10 — unique jti (single-use)
                .claim("email", email)
                .claim("org", org)
                .signWith(key, Jwts.SIG.RS256)
                .compact();
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
