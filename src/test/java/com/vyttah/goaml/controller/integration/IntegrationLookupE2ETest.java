package com.vyttah.goaml.controller.integration;

import com.vyttah.goaml.GoamlApplication;
import com.vyttah.goaml.model.entity.federated.SourceSystem;
import com.vyttah.goaml.model.entity.federated.TrustedService;
import com.vyttah.goaml.repository.federated.TrustedServiceRepository;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
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

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase C.3a end-to-end (MockMvc): goAML's service-authed lookup passthrough — a sibling app reads the
 * authoritative {@code code+label} sets server-to-server. Reference data (no tenant); auth is the screening
 * service assertion.
 */
@SpringBootTest(classes = GoamlApplication.class)
@AutoConfigureMockMvc
@Testcontainers
class IntegrationLookupE2ETest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired TrustedServiceRepository trustedServices;

    private static KeyPair keys;

    @BeforeEach
    void setUp() throws Exception {
        trustedServices.deleteAll();
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();
        trustedServices.save(new TrustedService(UUID.randomUUID(), SourceSystem.SCREENING,
                "screening", pem(keys.getPublic()), false, "ACTIVE"));
    }

    @Test
    void servesItemTypesWithCodeAndLabel() throws Exception {
        mvc.perform(get("/api/v1/integration/lookups/ae/item_types")
                        .header("X-Service-Assertion", assertion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='GOLD')].label").value(hasItem("Gold")));
    }

    @Test
    void unknownSetIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/integration/lookups/ae/does-not-exist")
                        .header("X-Service-Assertion", assertion()))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingAssertionIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/integration/lookups/ae/item_types"))
                .andExpect(status().isUnauthorized());
    }

    private static String assertion() {
        return Jwts.builder()
                .issuer("SCREENING")
                .subject("screening-system")
                .audience().add("goaml").and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(60, ChronoUnit.SECONDS)))
                .id(UUID.randomUUID().toString())            // B10 — unique jti (single-use)
                .signWith(keys.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static String pem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}
