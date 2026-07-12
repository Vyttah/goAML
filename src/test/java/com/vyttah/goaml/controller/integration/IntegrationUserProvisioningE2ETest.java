package com.vyttah.goaml.controller.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.appuser.AppUser;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (MockMvc + Testcontainers) for the decoupled onboarding flow:
 *  - a tenant provisioned with no admin (tenant-only), and
 *  - the {@code PUT /api/v1/integration/admin/users/{externalUserId}} upsert that explicitly creates/updates the
 *    goAML user backing an AML user (keyed by external_identity), which the federated exchange then resolves —
 *    proving the email-collision path is gone. Disabling (role cleared) blocks the exchange.
 */
@SpringBootTest(classes = GoamlApplication.class, properties = "goaml.auth.mode=both")
@AutoConfigureMockMvc
@Testcontainers
class IntegrationUserProvisioningE2ETest {

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

    private KeyPair cockpitKeys;
    private Tenant tenant;
    private String companyId;

    @BeforeEach
    void setUp() throws Exception {
        externalIdentities.deleteAll();
        tenantExternalRefs.deleteAll();
        trustedServices.deleteAll();

        cockpitKeys = newKeys();
        companyId = "iu-e2e-" + UUID.randomUUID().toString().substring(0, 8);

        // Tenant-only: provisioned with NO admin user (new flow).
        tenant = provisioningService.provision(new TenantProvisioningRequest(
                companyId, "IntUser E2E FZE", "AE", null, null, null, null));

        // SCREENING trusted service with JIT OFF (users are provisioned explicitly now).
        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.SCREENING,
                "AML cockpit", pem(cockpitKeys.getPublic()), false, "ACTIVE"));
        tenantExternalRefs.save(new TenantExternalRef(UUID.randomUUID(), tenant.getId(),
                SourceSystem.SCREENING, companyId));
    }

    @Test
    void tenantIsProvisionedWithNoUsers() {
        assertThat(appUsers.findByTenantId(tenant.getId())).isEmpty();
    }

    @Test
    void upsertCreatesUserThatFederatedExchangeResolves() throws Exception {
        String amlUserId = "42";
        String email = "mlro@" + companyId + ".test";

        // create the goAML user for AML user 42 with role MLRO
        JsonNode created = upsert(amlUserId,
                "{\"email\":\"" + email + "\",\"firstName\":\"Aisha\",\"lastName\":\"Khan\","
                        + "\"password\":\"P@ssw0rd!\",\"role\":\"MLRO\",\"active\":true}");
        assertThat(created.get("role").asText()).isEqualTo("MLRO");
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(externalIdentities
                .findBySourceSystemAndExternalUserId(SourceSystem.SCREENING, amlUserId)).isPresent();

        // the federated exchange (same external user id) resolves the mapped user — no JIT, no email collision
        String token = exchange(assertion(cockpitKeys.getPrivate(), "SCREENING", amlUserId, email, companyId));
        JsonNode me = me(token);
        assertThat(me.get("email").asText()).isEqualTo(email);
        assertThat(me.get("roles").toString()).contains("MLRO");
        assertThat(me.get("tenantSchema").asText()).isEqualTo(tenant.getSchemaName());

        // re-upsert (idempotent) with a new role → same user updated to TENANT_ADMIN
        JsonNode updated = upsert(amlUserId,
                "{\"email\":\"" + email + "\",\"firstName\":\"Aisha\",\"lastName\":\"Khan\","
                        + "\"role\":\"TENANT_ADMIN\",\"active\":true}");
        assertThat(updated.get("role").asText()).isEqualTo("TENANT_ADMIN");
        assertThat(updated.get("userId").asText()).isEqualTo(created.get("userId").asText());
    }

    @Test
    void clearingRoleDisablesTheUserAndBlocksExchange() throws Exception {
        String amlUserId = "77";
        String email = "an@" + companyId + ".test";
        upsert(amlUserId, "{\"email\":\"" + email + "\",\"firstName\":\"An\",\"lastName\":\"Alyst\","
                + "\"password\":\"P@ssw0rd!\",\"role\":\"ANALYST\",\"active\":true}");

        // exchange works while active
        assertThat(exchange(assertion(cockpitKeys.getPrivate(), "SCREENING", amlUserId, email, companyId)))
                .isNotBlank();

        // clear the role → disabled
        JsonNode disabled = upsert(amlUserId,
                "{\"email\":\"" + email + "\",\"role\":null,\"active\":false}");
        assertThat(disabled.get("status").asText()).isEqualTo("DISABLED");
        AppUser user = appUsers.findByTenantIdAndEmail(tenant.getId(), email).orElseThrow();
        assertThat(user.getStatus()).isEqualTo("DISABLED");

        // a disabled user must not obtain a token
        mvc.perform(post("/api/v1/auth/federated/token").contentType(APPLICATION_JSON)
                        .content(tokenBody(assertion(cockpitKeys.getPrivate(), "SCREENING", amlUserId, email, companyId))))
                .andExpect(status().is4xxClientError());
    }

    // --- helpers ---

    private JsonNode upsert(String externalUserId, String body) throws Exception {
        MvcResult res = mvc.perform(put("/api/v1/integration/admin/users/" + externalUserId)
                        .header("X-Service-Assertion",
                                assertion(cockpitKeys.getPrivate(), "SCREENING", externalUserId, null, companyId))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private String exchange(String assertion) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/federated/token")
                        .contentType(APPLICATION_JSON).content(tokenBody(assertion)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private JsonNode me(String token) throws Exception {
        MvcResult res = mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private static String tokenBody(String assertion) {
        return "{\"sourceSystem\":\"SCREENING\",\"assertion\":\"" + assertion + "\"}";
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
        var b = Jwts.builder()
                .issuer(issuer)
                .subject(sub)
                .audience().add("goaml").and()
                .issuedAt(java.util.Date.from(Instant.now()))
                .expiration(java.util.Date.from(Instant.now().plus(60, ChronoUnit.SECONDS)))
                .id(UUID.randomUUID().toString())
                .claim("org", org);
        if (email != null) {
            b.claim("email", email);
        }
        return b.signWith(key, Jwts.SIG.RS256).compact();
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
